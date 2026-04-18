package cu.lenier.artpay

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Maneja el flujo completo de pago y verificación mediante código QR e intenciones
 * con la Billetera Art-Pay.
 *
 * ```kotlin
 * private val artPayManager = ArtPayManager(this)
 *
 * // Para abrir el dialog de pago:
 * artPayManager.handlePayment(
 *     rootView = binding.root,
 *     tierName = "Pro",          // nombre exacto del producto en el backend (productToken)
 *     displayName = "Pro",       // para mensajes de UI
 *     onSuccess = { result -> }
 * )
 * ```
 */
class ArtPayManager(private val activity: AppCompatActivity) {

    private var pendingTierName: String = ""
    private var pendingDisplayName: String = ""
    private var pendingRootView: View? = null
    private var pendingOnSuccess: ((ArtPayVerificationResult) -> Unit)? = null
    private var pendingOnError: ((String) -> Unit)? = null
    private var loadingDialog: AlertDialog? = null
    private var intentDialog: AlertDialog? = null
    private var pollingJob: Job? = null

    /**
     * Compatibilidad hacia atrás (antes se registraba un launcher de archivos).
     * Ya no hace nada, sólo mantiene la firma de la API.
     */
    fun register() {
        // No-op
    }

    /**
     * Abre un QRCode para que la Billetera pague.
     */
    fun handlePayment(
        rootView: View,
        tierName: String,
        displayName: String = tierName,
        onSuccess: (ArtPayVerificationResult) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        pendingRootView = rootView
        pendingTierName = tierName
        pendingDisplayName = displayName
        pendingOnSuccess = onSuccess
        pendingOnError = onError

        showLoading("Generando Intención de Pago...")
        
        activity.lifecycleScope.launch {
            val result = ArtPayService.createIntent(pendingTierName)
            hideLoading()
            
            if (result.success && result.intentId != null) {
                showQrDialog(result.intentId)
            } else {
                val msg = result.errorMessage ?: "Error al crear la intención de pago"
                snack(msg, true)
                pendingOnError?.invoke(msg)
            }
        }
    }

    private fun showQrDialog(intentId: String) {
        val deepLinkUrl = "artpay://intent?id=$intentId"

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER
        }

        val title = TextView(activity).apply {
            text = "Pagar con Art-Pay"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val qrImageView = ImageView(activity).apply {
            setPadding(0, 32, 0, 32)
            setImageBitmap(generateQr(deepLinkUrl))
        }

        val desc = TextView(activity).apply {
            text = "Escanea este código con la app Billetera instalada en otro móvil, o toca el botón para abrir la Billetera localmente."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val btnOpen = Button(activity).apply {
            text = "ABRIR ART-PAY"
            setBackgroundColor(Color.parseColor("#1E88E5"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))
                try {
                    activity.startActivity(i)
                } catch (e: Exception) {
                    snack("No se encontró la app Billetera instalada")
                }
            }
        }

        val btnCancel = Button(activity).apply {
            text = "CANCELAR"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.GRAY)
            setOnClickListener {
                closeDialogs()
                pendingOnError?.invoke("Pago cancelado")
            }
        }

        layout.addView(title)
        layout.addView(qrImageView)
        layout.addView(desc)
        layout.addView(btnOpen)
        layout.addView(btnCancel)

        intentDialog = AlertDialog.Builder(activity)
            .setView(layout)
            .setCancelable(false)
            .create()
        
        intentDialog?.show()

        startPolling(intentId)
    }

    private fun startPolling(intentId: String) {
        pollingJob?.cancel()
        pollingJob = activity.lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                val status = ArtPayService.checkIntentStatus(intentId)
                if (status.success) {
                    if (status.status == "PAID") {
                        closeDialogs()
                        handlePaid(status)
                        break
                    } else if (status.status == "CANCELLED") {
                        closeDialogs()
                        snack("Intención de pago expirada o cancelada", true)
                        pendingOnError?.invoke("Pago cancelado o expirado")
                        break
                    }
                }
            }
        }
    }

    private fun handlePaid(status: ArtPayService.IntentResult) {
        snack("¡Licencia exitosa! ${status.productName ?: pendingDisplayName}")
        pendingOnSuccess?.invoke(
            ArtPayVerificationResult(
                success = true,
                productToken = status.productName, // compatibility match
                productName = status.productName,
                packageName = status.packageName,
                accessExpiresAt = status.accessExpiresAt
            )
        )
    }

    private fun closeDialogs() {
        intentDialog?.dismiss()
        intentDialog = null
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun showLoading(msg: String) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
            gravity = Gravity.CENTER
        }
        layout.addView(ProgressBar(activity).apply { isIndeterminate = true })
        layout.addView(TextView(activity).apply {
            text = msg
            textSize = 16f
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER
        })
        loadingDialog = AlertDialog.Builder(activity)
            .setView(layout).setCancelable(false).create()
        loadingDialog?.show()
    }

    private fun hideLoading() { loadingDialog?.dismiss(); loadingDialog = null }

    private fun generateQr(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 600, 600)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun snack(msg: String, isError: Boolean = false) {
        val root = pendingRootView ?: activity.window?.decorView?.rootView ?: return
        val s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
        if (isError) s.setBackgroundTint(Color.RED)
        s.show()
    }
}
