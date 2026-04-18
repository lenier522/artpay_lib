package cu.lenier.artpay

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Servicio que se comunica con el backend Art-Pay para verificar licencias .lic.
 *
 * Configura antes de usar:
 *   ArtPayService.baseUrl = "https://artpay.neti.cu"
 */
object ArtPayService {

    /** URL base del servidor Art-Pay */
    var baseUrl: String = "https://artpay.neti.cu"

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 1: Verificación pública por producto ID (productToken)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verifica un archivo .lic contra el backend usando el ID del producto.
     *
     * @param licenseFile       Archivo .lic seleccionado por el usuario.
     * @param expectedProductToken  UUID del producto en el panel Art-Pay
     *                              (el que corresponde al botón de activación).
     * @param packageName       Package name de la app (opcional, seguridad extra).
     */
    /**
     * Obtiene el token JWT del usuario conectado en la aplicación Billetera Art-Pay.
     */
    suspend fun getBilleteraToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://cu.lenier.billetera_artpay.provider/license")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var token: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                token = cursor.getString(0)
                cursor.close()
            }
            if (token.isNullOrBlank()) null else token
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifica la licencia del usuario usando el Token JWT alojado de forma segura en la
     * Billetera Art-Pay. (Recomendado para apps de terceros).
     *
     * @param context               Contexto de Android.
     * @param licenseFile           Archivo .lic a verificar.
     * @param expectedProductToken  UUID del producto en el panel Art-Pay.
     * @param packageName           El package name de tu aplicación cliente.
     */
    suspend fun verifyLicenseWithBilletera(
        context: Context,
        licenseFile: File,
        expectedProductToken: String = "",
        packageName: String = ""
    ): ArtPayVerificationResult = withContext(Dispatchers.IO) {
        val token = getBilleteraToken(context)
        if (token == null) {
            return@withContext ArtPayVerificationResult(
                success = false,
                errorMessage = "Licencia no válida o Billetera no autenticada. Asegúrate de iniciar sesión en Billetera Art-Pay."
            )
        }
        
        // Delegamos a la validacion autenticada segura en el backend
        return@withContext verifyLicenseAuthenticated(
            licenseFile = licenseFile,
            jwtToken = token,
            expectedProductToken = expectedProductToken,
            packageName = packageName
        )
    }

    /** [DEPRECADO] Usa verifyLicenseLocal en su lugar */
    suspend fun verifyLicense(
        licenseFile: File,
        expectedProductToken: String,
        packageName: String = ""
    ): ArtPayVerificationResult = withContext(Dispatchers.IO) {
        try {
            val fileContent = licenseFile.readText(Charsets.UTF_8)
            val body = buildJsonBody(
                encryptedLicense = fileContent.trim(),
                expectedPackageName = packageName,
                expectedProductToken = expectedProductToken
            )
            post(
                endpoint = "/api/purchases/verify-license",
                body = body,
                bearerToken = null
            )
        } catch (e: SocketException) {
            ArtPayVerificationResult(
                success = false,
                errorMessage = "Error de conexión con Art-Pay. Verifica tu internet o la URL del servidor."
            )
        } catch (e: Exception) {
            ArtPayVerificationResult(success = false, errorMessage = "Error inesperado: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 2: Verificación autenticada con JWT (Billetera Art-Pay)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verifica un archivo .lic usando la cuenta autenticada del usuario (JWT).
     * No require número de teléfono — valida que el usuario logueado sea el dueño.
     *
     * Para obtener el JWT llama primero a [login].
     *
     * @param licenseFile           Archivo .lic a verificar.
     * @param jwtToken              Token JWT obtenido al iniciar sesión en Art-Pay.
     * @param expectedProductToken  UUID del producto (opcional).
     * @param packageName           Package name de la app (opcional).
     */
    suspend fun verifyLicenseAuthenticated(
        licenseFile: File,
        jwtToken: String,
        expectedProductToken: String = "",
        packageName: String = ""
    ): ArtPayVerificationResult = withContext(Dispatchers.IO) {
        try {
            val fileContent = licenseFile.readText(Charsets.UTF_8)
            val body = buildJsonBody(
                encryptedLicense = fileContent.trim(),
                expectedPackageName = packageName,
                expectedProductToken = expectedProductToken
            )
            post(
                endpoint = "/api/purchases/verify-license-authenticated",
                body = body,
                bearerToken = jwtToken
            )
        } catch (e: SocketException) {
            ArtPayVerificationResult(
                success = false,
                errorMessage = "Error de conexión con Art-Pay."
            )
        } catch (e: Exception) {
            ArtPayVerificationResult(success = false, errorMessage = "Error inesperado: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 3: Login (para obtener JWT)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Inicia sesión en Art-Pay y devuelve el JWT token.
     *
     * @return [ArtPayLoginResult] con el token JWT o el error.
     */
    suspend fun login(email: String, password: String): ArtPayLoginResult =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"email": "${escapeJson(email)}", "password": "${escapeJson(password)}"}"""
                val url = URL("$baseUrl/api/auth/login")
                val connection = openSecureConnection(url).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val responseBody = if (code in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }
                connection.disconnect()

                if (code in 200..299) {
                    val json = JSONObject(responseBody)
                    val data = json.optJSONObject("data")
                    val token = data?.optString("token") ?: ""
                    val email2 = data?.optString("email") ?: email
                    val name = data?.optString("name") ?: ""
                    ArtPayLoginResult(success = true, token = token, email = email2, name = name)
                } else {
                    val msg = try {
                        JSONObject(responseBody).optString("message").ifBlank { "Credenciales incorrectas" }
                    } catch (_: Exception) { "Error de autenticación (Código: $code)" }
                    ArtPayLoginResult(success = false, errorMessage = msg)
                }
            } catch (e: Exception) {
                ArtPayLoginResult(success = false, errorMessage = "Error de conexión: ${e.message}")
            }
        }

    // ─────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────

    private fun buildJsonBody(
        encryptedLicense: String,
        expectedPackageName: String,
        expectedProductToken: String
    ): String {
        return JSONObject().apply {
            put("encryptedLicense", encryptedLicense)
            put("expectedPackageName", expectedPackageName)
            put("expectedProductToken", expectedProductToken)
            put("devicePhoneNumber", "")
        }.toString()
    }

    private suspend fun post(
        endpoint: String,
        body: String,
        bearerToken: String?
    ): ArtPayVerificationResult = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl$endpoint")
        val connection = openSecureConnection(url).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText() ?: ""
        }
        connection.disconnect()

        if (statusCode in 200..299) parseSuccessResponse(responseBody)
        else parseErrorResponse(responseBody, statusCode)
    }

    /**
     * En un entorno con certificado autofirmado (desarrollo/neti.cu),
     * se puede sobreescribir la fábrica SSL. En producción con CA válida no es necesario.
     */
    private fun openSecureConnection(url: URL): HttpURLConnection {
        return url.openConnection() as HttpURLConnection
    }

    private fun parseSuccessResponse(body: String): ArtPayVerificationResult {
        return try {
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            val expiresAt = data?.optString("accessExpiresAt")?.let { parseDate(it) }
            ArtPayVerificationResult(
                success = true,
                productToken = data?.optString("productToken"),
                productName = data?.optString("productName"),
                packageName = data?.optString("packageName"),
                accessExpiresAt = expiresAt
            )
        } catch (e: Exception) {
            ArtPayVerificationResult(success = true)
        }
    }

    private fun parseErrorResponse(body: String, code: Int): ArtPayVerificationResult {
        val msg = try {
            JSONObject(body).optString("message").ifBlank { null }
                ?: "Error al verificar licencia (Código: $code)"
        } catch (_: Exception) {
            "Error al verificar licencia (Código: $code)"
        }
        return ArtPayVerificationResult(success = false, errorMessage = msg)
    }

    private fun parseDate(str: String): java.util.Date? {
        if (str.isBlank()) return null
        listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd").forEach { fmt ->
            try {
                return SimpleDateFormat(fmt, Locale.US).also {
                    it.timeZone = TimeZone.getTimeZone("UTC")
                }.parse(str)
            } catch (_: Exception) {}
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 4: Intent - Pago por QR / Deep Link
    // ─────────────────────────────────────────────────────────────────

    data class IntentResult(
        val success: Boolean,
        val intentId: String? = null,
        val status: String? = null,
        val licenseData: String? = null,
        val productName: String? = null,
        val packageName: String? = null,
        val accessExpiresAt: java.util.Date? = null,
        val errorMessage: String? = null
    )

    suspend fun createIntent(productToken: String): IntentResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/intents?productToken=$productToken")
            val connection = openSecureConnection(url).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Length", "0")
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
            }
            connection.outputStream.use { it.write(ByteArray(0)) }

            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }
            connection.disconnect()

            if (statusCode in 200..299) {
                val json = JSONObject(responseBody)
                val data = json.optJSONObject("data")
                IntentResult(
                    success = true,
                    intentId = data?.optString("id"),
                    status = data?.optString("status")
                )
            } else {
                val msg = try { JSONObject(responseBody).optString("message").ifBlank { null } } catch (_: Exception) { null }
                IntentResult(success = false, errorMessage = msg ?: "Error al crear pago ($statusCode)")
            }
        } catch (e: Exception) {
            IntentResult(success = false, errorMessage = "Error: ${e.message}")
        }
    }

    suspend fun checkIntentStatus(intentId: String): IntentResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/intents/$intentId/status")
            val connection = openSecureConnection(url).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }
            connection.disconnect()

            if (statusCode in 200..299) {
                val json = JSONObject(responseBody)
                val data = json.optJSONObject("data")
                val expiresAt = data?.optString("accessExpiresAt")?.let { parseDate(it) }
                IntentResult(
                    success = true,
                    intentId = data?.optString("id"),
                    status = data?.optString("status"),
                    licenseData = data?.optString("licenseData"),
                    productName = data?.optString("productName"),
                    packageName = data?.optString("packageName"),
                    accessExpiresAt = expiresAt
                )
            } else {
                val msg = try { JSONObject(responseBody).optString("message").ifBlank { null } } catch (_: Exception) { null }
                IntentResult(success = false, errorMessage = msg ?: "Error check ($statusCode)")
            }
        } catch (e: Exception) {
            IntentResult(success = false, errorMessage = "Error: ${e.message}")
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
