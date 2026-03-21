package cu.lenier.cashrapido

/**
 * Tipos de licencia de ESTA aplicación.
 * Define aquí todos los planes que ofrezcas — la librería artpay solo necesita
 * el tierName (nombre exacto del producto en el backend) y el displayName (para la UI).
 */
enum class ArtPayLicenseType(
    val displayName: String,
    val tierName: String
) {
    BASIC(displayName = "Básico",      tierName = "Basic"),
    PRO(displayName = "Pro",           tierName = "Pro"),
    ENTERPRISE(displayName = "Empresarial", tierName = "enterprise");
}
