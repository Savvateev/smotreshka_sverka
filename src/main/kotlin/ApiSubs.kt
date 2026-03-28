
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject

fun getSubscriptions(authToken: String): List<Pair<String, Double>> {
    val url = URL("https://api.bsvyazi.ru/api/v1/cabinet/user")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "MikbillApiAgent/1.0")
    connection.setRequestProperty("Authorization", authToken)

    val responseCode = connection.responseCode
    if (responseCode != 200) {
        throw RuntimeException("Failed : HTTP error code : $responseCode")
    }

    val reader = BufferedReader(InputStreamReader(connection.inputStream))
    val response = reader.use { it.readText() }

    val jsonObject = JSONObject(response)
    val dataObject = jsonObject.getJSONObject("data")
    val feeObject : JSONObject = dataObject.getJSONObject("fee")
    val subsObject : JSONObject = feeObject.getJSONObject("subscriptions")
   // val detailedObject : JSONObject = subsObject.getJSONObject("detailed")
    val detailedArray = subsObject.getJSONArray("detailed")

    val result = mutableListOf<Pair<String, Double>>()
    for (i in 0 until detailedArray.length()) {
        val item = detailedArray.getJSONObject(i)
        val name = item.getString("name")
        val price = item.getDouble("price")
        result.add(name to price)
    }
    return result
}