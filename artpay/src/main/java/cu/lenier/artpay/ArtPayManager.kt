package cu.lenier.artpay

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Maneja el flujo completo de selección y verificación de licencia .lic.
 *
 * IMPORTANTE — register() DEBE llamarse en onCreate() de la Activity:
 *
 * ```kotlin
 * private val artPayManager = ArtPayManager(this)
 *
 * override fun onCreate(...) {
 *     artPayManager.register()
 * }
 *
 * // Para abrir el selector:
 * artPayManager.handlePayment(
 *     rootView = binding.root,
 *     tierName = "Pro",          // nombre exacto del producto en el backend
 *     displayName = "Pro",       // para mensajes de UI
 *     onSuccess = { result -> }
 * )
 * ```
 */
class ArtPayManager(private val activity: AppCompatActivity) {

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var pendingTierName: String = ""
    private var pendingDisplayName: String = ""
    private var pendingRootView: View? = null
    private var pendingOnSuccess: ((ArtPayVerificationResult) -> Unit)? = null
    private var pendingOnError: ((String) -> Unit)? = null
    private var loadingDialog: AlertDialog? = null

    /**
     * Registra el selector de archivos.
     * ⚠️ DEBE llamarse en onCreate() antes de que empiece la Activity.
     */
    fun register() {
        filePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            val name = getFileName(uri)
            if (!name.lowercase().endsWith(".lic")) {
                snack("Por favor, selecciona un archivo .lic", isError = true)
                return@registerForActivityResult
            }
            processLicenseFile(uri)
        }
    }

    /**
     * Abre el selector de archivos y ejecuta el flujo completo de verificación.
     *
     * @param rootView    Vista raíz para mostrar Snackbars
     * @param tierName    Nombre del tier enviado al backend (ej: "Pro", "enterprise")
     * @param displayName Nombre para mostrar en mensajes de UI (ej: "Pro", "Empresarial")
     * @param onSuccess   Callback cuando la licencia es válida
     * @param onError     Callback cuando falla (opcional)
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

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Seleccionar licencia .lic"))
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun processLicenseFile(uri: Uri) {
        val root = pendingRootView ?: return

        activity.lifecycleScope.launch {
            val tempFile = copyUriToTemp(uri) ?: run {
                snack("No se pudo leer el archivo", isError = true)
                return@launch
            }
            showLoading()
            val result = ArtPayService.verifyLicense(tempFile, pendingTierName, activity.packageName)
            tempFile.delete()
            hideLoading()
            handleResult(result, root)
        }
    }

    private fun handleResult(result: ArtPayVerificationResult, root: View) {
        if (!result.success) {
            val msg = result.errorMessage ?: "Error desconocido"
            Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
            pendingOnError?.invoke(msg)
            return
        }

        // Validar packageName — se lee automáticamente del package de la app
        val pkg = result.packageName
        val myPackage = activity.packageName
        if (!pkg.isNullOrBlank() && pkg != myPackage) {
            val msg = "Esta licencia pertenece a otra aplicación"
            Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
            pendingOnError?.invoke(msg)
            return
        }

        // Validar que el producto del backend coincida con el tier solicitado
        val productName = result.productName?.lowercase() ?: ""
        val tierLower = pendingTierName.lowercase()
        val matches = productName.contains(tierLower) ||
            (tierLower == "enterprise" && productName.contains("empresarial"))

        if (!matches) {
            val msg = "Compraste \"${result.productName}\" pero intentaste activar \"$pendingDisplayName\". Selecciona la opción correcta."
            Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
            pendingOnError?.invoke(msg)
            return
        }

        // ✅ Éxito
        pendingOnSuccess?.invoke(result)
        Snackbar.make(root, "¡Licencia activada! ${result.productName ?: pendingDisplayName}", Snackbar.LENGTH_LONG).show()
    }

    private fun showLoading() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
            gravity = Gravity.CENTER
        }
        layout.addView(ProgressBar(activity).apply { isIndeterminate = true })
        layout.addView(TextView(activity).apply {
            text = "Verificando licencia..."
            textSize = 16f
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER
        })
        loadingDialog = AlertDialog.Builder(activity)
            .setView(layout).setCancelable(false).create()
        loadingDialog?.show()
    }

    private fun hideLoading() { loadingDialog?.dismiss(); loadingDialog = null }

    private fun copyUriToTemp(uri: Uri): File? = try {
        val tmpFile = File(activity.cacheDir, "lic_${System.currentTimeMillis()}.lic")
        activity.contentResolver.openInputStream(uri)?.use { inp ->
            FileOutputStream(tmpFile).use { out -> inp.copyTo(out) }
        }
        tmpFile
    } catch (_: Exception) { null }

    private fun getFileName(uri: Uri): String {
        var name = ""
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun snack(msg: String, isError: Boolean = false) {
        val root = pendingRootView ?: activity.window?.decorView?.rootView ?: return
        val s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
        if (isError) s.setBackgroundTint(activity.getColor(android.R.color.holo_red_dark))
        s.show()
    }
}
