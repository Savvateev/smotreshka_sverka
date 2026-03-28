import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TokenData(val token: String)
data class TokenResponse(val success: Boolean, val code: Int, val data: TokenData?, val message: String)

fun hmacSha512(salt: String, secretKey: String): String {
    val algorithm = "HmacSHA512"
    val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(keySpec)
    val hashBytes = mac.doFinal(salt.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

fun getToken(uid: String, sign: String, salt: String): String? {
    val url = URL("https://api.bsvyazi.ru/api/v1/billing/users/token")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("User-Agent", "MikbillApiAgent/1.0")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    val requestBody = """{"uid":"$uid","sign":"$sign","salt":"$salt"}"""

    connection.outputStream.use { os ->
        OutputStreamWriter(os, Charsets.UTF_8).use { it.write(requestBody) }
    }

    if (connection.responseCode == 200) {
        connection.inputStream.bufferedReader().use { reader ->
            val responseText = reader.readText()
            val mapper = jacksonObjectMapper()
            val response: TokenResponse = mapper.readValue(responseText)
            if (response.success && response.data != null) {
                return response.data.token
            }
        }
    } else {
        System.err.println("Request failed with HTTP code: ${connection.responseCode}")
        connection.errorStream?.bufferedReader()?.use { System.err.println(it.readText()) }
    }
    return null
}