package cu.lenier.artpay

/**
 * Resultado de un login en Art-Pay.
 */
data class ArtPayLoginResult(
    val success: Boolean,
    val token: String? = null,
    val email: String? = null,
    val name: String? = null,
    val errorMessage: String? = null
)
