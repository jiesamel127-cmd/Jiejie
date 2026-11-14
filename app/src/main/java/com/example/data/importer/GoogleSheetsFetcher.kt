package com.example.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

object GoogleSheetsFetcher {

  const val DEFAULT_JIEGO_SHEET_URL =
    "https://docs.google.com/spreadsheets/d/1diP_VBtV6A7LJ_-_8czHX4ajKvamHOsKivykvcC4UF0/edit?usp=drivesdk"

  private val SHEET_ID_REGEX = Regex("/spreadsheets/d/([a-zA-Z0-9_-]+)")

  fun extractSpreadsheetId(rawUrlOrId: String): String? {
    val trimmed = rawUrlOrId.trim()
    if (trimmed.isEmpty()) return null
    val match = SHEET_ID_REGEX.find(trimmed)
    if (match != null) return match.groupValues[1]
    if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{20,}$"))) {
      return trimmed
    }
    return null
  }

  fun buildXlsxExportUrl(rawUrlOrId: String): String {
    val id = extractSpreadsheetId(rawUrlOrId)
      ?: throw IllegalArgumentException("Link Google Sheets tidak valid. Pastikan formatnya mengandung /spreadsheets/d/...")
    return "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
  }

  suspend fun fetchAndParse(
    rawUrlOrId: String,
    defaultArtistGroup: String = "K-Pop Photocard",
  ): ParsedRekapanResult = withContext(Dispatchers.IO) {
    val exportUrl = buildXlsxExportUrl(rawUrlOrId)
    val workbookSheets = openStreamWithRedirects(exportUrl) { bis ->
      bis.mark(8)
      val header = ByteArray(4)
      val readCount = bis.read(header)
      bis.reset()

      val isZipXlsx = readCount == 4 &&
        header[0] == 0x50.toByte() &&
        header[1] == 0x4B.toByte() &&
        header[2] == 0x03.toByte() &&
        header[3] == 0x04.toByte()

      if (!isZipXlsx) {
        throw IllegalStateException(
          "Spreadsheet masih terkunci (privat) atau tidak bisa diunduh. Pastikan akses Bagikan di Google Sheets diset ke 'Siapa saja yang memiliki link -> Pelihat', atau gunakan tombol Muat Rekapan JIEGO Bawaan."
        )
      }
      XlsxWorkbookParser.parse(bis)
    }

    RekapanCsvParser.parseSheets(
      sheets = workbookSheets.map { RekapanSheetInput(sheetName = it.sheetName, rows = it.rows) },
      defaultArtistGroup = defaultArtistGroup,
    )
  }

  private inline fun <T> openStreamWithRedirects(
    initialUrl: String,
    block: (BufferedInputStream) -> T,
  ): T {
    var currentUrl = initialUrl
    var hops = 0
    while (hops < 6) {
      val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        useCaches = false
        connectTimeout = 20_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "JIEGO-Android/1.0")
      }
      val code = conn.responseCode
      if (code in 300..399) {
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        if (location.isNullOrBlank()) {
          throw IllegalStateException("Redirect dari Google Sheets tidak lengkap.")
        }
        currentUrl = URL(URL(currentUrl), location).toString()
        hops++
        continue
      }
      try {
        if (code !in 200..299) {
          throw IllegalStateException("Gagal mengunduh Google Sheets (HTTP $code). Pastikan akses link publik.")
        }
        return BufferedInputStream(conn.inputStream, 32 * 1024).use(block)
      } finally {
        conn.disconnect()
      }
    }
    throw IllegalStateException("Terlalu banyak pengalihan saat mengunduh Google Sheets.")
  }
}
