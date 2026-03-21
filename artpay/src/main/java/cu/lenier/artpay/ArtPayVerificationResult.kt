package cu.lenier.artpay

import java.util.Date

/**
 * Resultado de verificar una licencia .lic con el backend Art-Pay.
 */
data class ArtPayVerificationResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val productToken: String? = null,
    val productName: String? = null,
    val packageName: String? = null,
    val accessExpiresAt: Date? = null
)
