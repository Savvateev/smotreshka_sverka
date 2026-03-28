import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File

data class SubInfo(val uid: String, val login: String, val subscribe: String, val price: Int)
data class UidLogin(val uid: String, val login: String)

var count: Int = 0

fun readSubscriptions(filePath: String): Map<String, SubInfo> {
    val file = File(filePath)
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheetAt(0)

    val headerRowIndex = 7
    val headerRow = sheet.getRow(headerRowIndex) ?: throw IllegalArgumentException("Header row not found")

    var loginCol = -1
    var subscriptionCol = -1
    var priceCol = -1
    var count  = 0

    for (cellIndex in 0 until headerRow.lastCellNum) {
        val cell = headerRow.getCell(cellIndex)
        if (cell != null) {
            val cellValue = cell.stringCellValue.trim()
            if (cellValue == "Логин") loginCol = cellIndex
            if (cellValue == "Название подписки") subscriptionCol = cellIndex
            if (cellValue == "Стоимость подписки") priceCol = cellIndex
        }
    }

    if (loginCol == -1 || subscriptionCol == -1 || priceCol == -1) {
        workbook.close()
        throw IllegalArgumentException("Required columns not found")
    }

    val subscriptions = mutableMapOf<String, SubInfo>()

    for (rowIndex in headerRowIndex + 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowIndex) ?: continue
        val loginCell = row.getCell(loginCol)
        val subscriptionCell = row.getCell(subscriptionCol)
        val priceCell = row.getCell(priceCol)
        if (loginCell != null && subscriptionCell != null && priceCell != null) {
            val fullLogin = loginCell.stringCellValue.trim()
            val login = fullLogin.substringBefore("@")
            val subscription = subscriptionCell.stringCellValue.trim()
            val price = when {
                priceCell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC -> priceCell.numericCellValue.toInt()
                priceCell.cellType == org.apache.poi.ss.usermodel.CellType.STRING -> priceCell.stringCellValue.trim()
                    .toIntOrNull() ?: 0

                else -> 0
            }
            if (login.isNotEmpty()) {
                subscriptions[login] = SubInfo(uid = "", login = login, subscribe = subscription, price = price)
                count += 1
            }
        }
    }
    workbook.close()
    println(count)
    return subscriptions
}

fun readUidLoginPairs(filePath: String): Map<String, UidLogin> {
    val file = File(filePath)
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheetAt(0)
    val map = mutableMapOf<String, UidLogin>()
    for (rowIndex in 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowIndex) ?: continue
        val uidCell = row.getCell(0)
        val loginCell = row.getCell(1)
        if (uidCell != null && loginCell != null) {
            val uid = uidCell.toString().trim()
            val login = loginCell.toString().trim()
            if (uid.isNotEmpty() && login.isNotEmpty()) {
                map[login] = UidLogin(uid, login)
            }
        }
    }
    workbook.close()
    return map
}

fun createSubInfoList(uidLoginMap: Map<String, UidLogin>, subscriptions: Map<String, SubInfo>): List<SubInfo> {
    val result = mutableListOf<SubInfo>()
    for ((login, subInfo) in subscriptions) {
        val uidLogin = uidLoginMap[login]
        if (uidLogin != null) {
            if (uidLogin.uid.isEmpty()) {
                println("Warning: Login '$login' has empty UID in UidLogin")
            }
            result.add(subInfo.copy(uid = uidLogin.uid))
        } else {
            println("No such login in UidLogin: $login")
            count++
        }
    }
    return result
}


fun main() {
    val uidLoginPath = "/Users/pavelsavvateev/Desktop/Большие связи/Смотрешка сверка/abon.xls"
    val smtrPath = "/Users/pavelsavvateev/Desktop/ИП Тимонин А.В. Отчет Январь 2026.xlsx"
    val secretKey = "Forex2012"
    val salt = "12345"
    var good = 0
    var bad = 0
    var noBilling = 0
    var noTariff = 0

    val subscriptions = readSubscriptions(smtrPath)
    val uidLoginMap = readUidLoginPairs(uidLoginPath)

    val subInfoList = createSubInfoList(uidLoginMap, subscriptions)

    subInfoList.forEach {
        val token = getToken(it.uid, hmacSha512(salt, secretKey), salt)
        if (token != null) {
            val subs = it.subscribe
            //print("SMTR :" + it.login + " " + subs + " ")
            val list = getSubscriptions(token)
            if (list.isEmpty()) {
                noBilling += 1
                println()
                println("нет данных в биллинге о подписке, по данным смотрешки : " + subs + " " + it.login)
            } else {
                val billingData = getServiceValue(list.first().first)
                if (billingData == null) println(list.first().first)
                //println(list)
                if (billingData != subs) {
                    if (billingData != "не учитываемый тариф") {
                        bad += 1
                        println()
                        println("Биллинг : " + billingData + " Смотрешка : " + subs + " " + it.login)
                    }
                    else {
                        noTariff += 1
                    }
                } else {
                    good += 1
                    //print("+")
                }
            }
        } else println("Failed to get token")
    }
    println("Нет в биллинге " + noBilling)
    println("не соответствие тарифов " + bad)
    println("не учитываемые тарифы " + noTariff)
    println("соответствие " + good)
}