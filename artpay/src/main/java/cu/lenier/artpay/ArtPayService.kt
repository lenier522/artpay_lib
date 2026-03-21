package cu.lenier.artpay

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
 *   ArtPayService.baseUrl = "http://TU_IP:8080"
 */
object ArtPayService {

    /** URL base del servidor Art-Pay */
    var baseUrl: String = "http://192.168.1.102:8080"

    /**
     * Verifica una licencia .lic contra el backend.
     * Corre en Dispatchers.IO — úsalo desde una coroutine.
     */
    suspend fun verifyLicense(
        licenseFile: File,
        targetLicenseName: String,
        packageName: String = ""
    ): ArtPayVerificationResult = withContext(Dispatchers.IO) {
        try {
            val fileContent = licenseFile.readText(Charsets.UTF_8)
            val safeContent = fileContent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")

            val url = URL("$baseUrl/api/purchases/verify-license")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val body = """{"encryptedLicense": "$safeContent", "expectedPackageName": "$packageName", "expectedLicenseTier": "$targetLicenseName"}"""
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

        } catch (e: SocketException) {
            ArtPayVerificationResult(
                success = false,
                errorMessage = "Error de conexión con Art-Pay. Verifica tu internet o la URL del servidor."
            )
        } catch (e: Exception) {
            ArtPayVerificationResult(success = false, errorMessage = "Error inesperado: ${e.message}")
        }
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
}
