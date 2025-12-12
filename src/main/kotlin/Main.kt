import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UserSubscription(val login: String, val subscription: String)

fun readCredentials(filePath: String): Map<String, String> {
    val file = File(filePath)
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheetAt(1) // Sheet 2
    val creds = mutableMapOf<String, String>()
    for (row in sheet) {
        val loginCell = row.getCell(0)
        val passwordCell = row.getCell(1)
        if (loginCell != null && passwordCell != null) {
            val login = loginCell.stringCellValue.trim()
            val password = passwordCell.stringCellValue.trim()
            if (login.isNotEmpty() && password.isNotEmpty()) {
                creds[login] = password
            }
        }
    }
    workbook.close()
    return creds
}

fun readSubscriptions(filePath: String): List<UserSubscription> {
    val file = File(filePath)
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheet("Sheet1") ?: throw IllegalArgumentException("Sheet1 not found")

    // Find header row at row 8 (index 7)
    val headerRow = sheet.getRow(7) ?: throw IllegalArgumentException("Header row (8) not found")

    var loginCol = -1
    var subscriptionCol = -1

    for (cellIndex in 0 until headerRow.lastCellNum) {
        val cell = headerRow.getCell(cellIndex)
        if (cell != null) {
            val cellValue = cell.stringCellValue.trim()
            if (cellValue == "Логин") loginCol = cellIndex
            if (cellValue == "Название подписки") subscriptionCol = cellIndex
        }
    }
    if (loginCol == -1 || subscriptionCol == -1) {
        workbook.close()
        throw IllegalArgumentException("Required columns not found")
    }

    val subs = mutableListOf<UserSubscription>()
    val lastRowNum = sheet.lastRowNum
    for (rowIndex in 8..lastRowNum) {
        val row = sheet.getRow(rowIndex) ?: continue
        val loginCell = row.getCell(loginCol)
        val subCell = row.getCell(subscriptionCol)
        if (loginCell != null && subCell != null) {
            val fullLogin = loginCell.stringCellValue.trim()
            val subscription = subCell.stringCellValue.trim()
            if (fullLogin.isNotEmpty()) {
                val login = fullLogin.substringBefore("@")
                subs.add(UserSubscription(login, subscription))
            }
        }
    }
    workbook.close()
    return subs
}

fun getApiKey(login: String, password: String): String? {
    val url = URL("https://api.example.com/getApiKey") // replace with actual API URL
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("User-Agent", "MikbillApiAgent/1.0")
    connection.doOutput = true

    // example request body with login and password in JSON, adjust if needed
    val requestBody = """{"login":"$login","password":"$password"}"""
    connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

    if (connection.responseCode == 200) {
        connection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            // Extract API key from response, implement actual parsing here
            // For example, assume response is plain API key string:
            return response.trim()
        }
    }
    return null
}

fun getSubscriptionByApiKey(apiKey: String): String {
    val url = URL("https://api.example.com/getSubscription") // replace with actual API URL
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "MikbillApiAgent/1.0")
    connection.setRequestProperty("Authorization", "Bearer $apiKey")

    if (connection.responseCode == 200) {
        connection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            // Parse subscription from response, implement actual parsing
            return response.trim()
        }
    }
    return ""
}

fun main() {
    val credentialsFile = "credentials.xlsx"
    val subscriptionsFile = "subscriptions.xlsx"

    val credentials = readCredentials(credentialsFile)
    val userSubs = readSubscriptions(subscriptionsFile)

    for (userSub in userSubs) {
        val password = credentials[userSub.login]
        if (password == null) {
            println("No password found for login: ${userSub.login}")
            continue
        }
        val apiKey = getApiKey(userSub.login, password)
        if (apiKey == null) {
            println("Failed to get API key for login: ${userSub.login}")
            continue
        }
        val actualSubscription = getSubscriptionByApiKey(apiKey)
        if (actualSubscription != userSub.subscription) {
            println("Subscription mismatch for login: ${userSub.login}. File: ${userSub.subscription}, API: $actualSubscription")
        }
    }
}