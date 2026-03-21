package cu.lenier.cashrapido

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cu.lenier.artpay.ArtPayManager
import cu.lenier.artpay.ArtPayVerificationResult
import cu.lenier.cashrapido.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val artPayManager = ArtPayManager(this)
    private var activeLicense: ArtPayVerificationResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ⚠️ OBLIGATORIO: registrar antes de que empiece la Activity
        artPayManager.register()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupButtons()
    }

    private fun setupButtons() {
        // La APP define sus propios tipos de licencia en ArtPayLicenseType.kt
        // La librería solo necesita tierName y displayName como Strings

        binding.btnActivateBasic.setOnClickListener {
            artPayManager.handlePayment(
                rootView = binding.main,
                tierName = ArtPayLicenseType.BASIC.tierName,
                displayName = ArtPayLicenseType.BASIC.displayName,
                onSuccess = { result -> onLicenseActivated(result) },
                onError = { error -> onLicenseError(error) }
            )
        }

        binding.btnActivatePro.setOnClickListener {
            artPayManager.handlePayment(
                rootView = binding.main,
                tierName = ArtPayLicenseType.PRO.tierName,
                displayName = ArtPayLicenseType.PRO.displayName,
                onSuccess = { result -> onLicenseActivated(result) },
                onError = { error -> onLicenseError(error) }
            )
        }

        binding.btnActivateEnterprise.setOnClickListener {
            artPayManager.handlePayment(
                rootView = binding.main,
                tierName = ArtPayLicenseType.ENTERPRISE.tierName,
                displayName = ArtPayLicenseType.ENTERPRISE.displayName,
                onSuccess = { result -> onLicenseActivated(result) },
                onError = { error -> onLicenseError(error) }
            )
        }
    }

    private fun onLicenseActivated(result: ArtPayVerificationResult) {
        activeLicense = result
        updateLicenseStatusUI(result)
        // TODO: Guardar en SharedPreferences o sistema de estado
    }

    private fun onLicenseError(error: String) {
        // ArtPayManager ya muestra el Snackbar. Añade lógica extra aquí si necesitas.
    }

    private fun updateLicenseStatusUI(result: ArtPayVerificationResult) {
        binding.tvLicenseStatus.text = "✅ ${result.productName ?: "Licencia activa"}"
        val expiry = result.accessExpiresAt
        if (expiry != null) {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvLicenseExpiry.text = "Válida hasta: ${fmt.format(expiry)}"
        } else {
            binding.tvLicenseExpiry.text = "Sin fecha de expiración"
        }
    }
}