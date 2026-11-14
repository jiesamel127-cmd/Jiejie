package com.example.data.importer

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

data class XlsxSheetData(
  val sheetName: String,
  val rows: List<List<String>>,
)

/**
 * Lightweight, memory-safe multi-sheet Excel (.xlsx) parser using standard ZIP + SAXParser.
 * Skips embedded images/media (xl/media/) so large Google Sheets exports parse cleanly on mobile.
 */
object XlsxWorkbookParser {

  private fun shouldReadZipEntry(name: String): Boolean {
    val clean = name.trimStart('/')
    return clean == "xl/sharedStrings.xml" ||
      clean == "xl/workbook.xml" ||
      clean == "xl/_rels/workbook.xml.rels" ||
      (clean.startsWith("xl/worksheets/") && clean.endsWith(".xml", ignoreCase = true))
  }

  fun parse(inputStream: InputStream): List<XlsxSheetData> {
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(inputStream).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        val cleanName = entry.name.trimStart('/')
        if (!entry.isDirectory && shouldReadZipEntry(cleanName)) {
          entries[cleanName] = zis.readBytes()
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    if (entries.isEmpty()) {
      throw IllegalArgumentException("File Excel (.xlsx) kosong, bukan format .xlsx, atau tidak memiliki sheet yang bisa dibaca.")
    }

    val sharedStrings = entries["xl/sharedStrings.xml"]
      ?.let { parseSharedStrings(it) }
      ?: emptyList()

    val relsMap = entries["xl/_rels/workbook.xml.rels"]
      ?.let { parseWorkbookRels(it) }
      ?: emptyMap()

    val orderedSheets = entries["xl/workbook.xml"]
      ?.let { parseWorkbookSheets(it, relsMap) }
      ?.filter { (_, path) -> entries.containsKey(path) }
      ?: emptyList()

    val sheetsToRead = if (orderedSheets.isNotEmpty()) {
      orderedSheets
    } else {
      entries.keys
        .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
        .sorted()
        .mapIndexed { idx, path -> "Sheet ${idx + 1}" to path }
    }

    return sheetsToRead.mapNotNull { (sheetName, path) ->
      val xmlBytes = entries[path] ?: return@mapNotNull null
      val rows = parseWorksheet(xmlBytes, sharedStrings)
      if (rows.isEmpty()) null else XlsxSheetData(sheetName = sheetName, rows = rows)
    }
  }

  private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
    val result = mutableListOf<String>()
    parseWithSax(xmlBytes, object : DefaultHandler() {
      var inSi = false
      var inT = false
      val sb = StringBuilder()

      override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (localTagName(localName, qName)) {
          "si" -> {
            inSi = true
            sb.clear()
          }
          "t" -> if (inSi) inT = true
        }
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inSi && inT) {
          sb.append(ch, start, length)
        }
      }

      override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localTagName(localName, qName)) {
          "t" -> inT = false
          "si" -> {
            inSi = false
            result.add(sb.toString())
          }
        }
      }
    })
    return result
  }

  private fun parseWorkbookRels(xmlBytes: ByteArray): Map<String, String> {
    val map = linkedMapOf<String, String>()
    parseWithSax(xmlBytes, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (localTagName(localName, qName) == "Relationship" && attributes != null) {
          val id = attributes.getValue("Id").orEmpty()
          val rawTarget = attributes.getValue("Target").orEmpty().trimStart('/').removePrefix("../")
          if (id.isNotBlank() && rawTarget.isNotBlank()) {
            val normalized = if (rawTarget.startsWith("xl/")) rawTarget else "xl/$rawTarget"
            map[id] = normalized
          }
        }
      }
    })
    return map
  }

  private fun parseWorkbookSheets(
    xmlBytes: ByteArray,
    relsMap: Map<String, String>,
  ): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    parseWithSax(xmlBytes, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (localTagName(localName, qName) == "sheet" && attributes != null) {
          val name = attributes.getValue("name").orEmpty().ifBlank { "Sheet ${list.size + 1}" }
          val rId = findAttrByLocalName(attributes, "id").orEmpty()
          val target = relsMap[rId] ?: "xl/worksheets/sheet${list.size + 1}.xml"
          list.add(name to target)
        }
      }
    })
    return list
  }

  private fun parseWorksheet(
    xmlBytes: ByteArray,
    sharedStrings: List<String>,
  ): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    parseWithSax(xmlBytes, object : DefaultHandler() {
      var currentRowMap = linkedMapOf<Int, String>()
      var maxColInRow = -1
      var inRow = false
      var inCell = false
      var currentColIndex = 0
      var currentCellType = ""
      var inV = false
      var inInlineStr = false
      var inInlineT = false
      val textBuf = StringBuilder()

      override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (localTagName(localName, qName)) {
          "row" -> {
            inRow = true
            currentRowMap = linkedMapOf()
            maxColInRow = -1
          }
          "c" -> if (inRow) {
            inCell = true
            val ref = attributes?.getValue("r").orEmpty()
            currentColIndex = if (ref.isNotBlank()) columnIndexFromRef(ref) else (maxColInRow + 1)
            currentCellType = attributes?.getValue("t").orEmpty()
            textBuf.clear()
          }
          "v" -> if (inCell) {
            inV = true
            textBuf.clear()
          }
          "is" -> if (inCell) {
            inInlineStr = true
            textBuf.clear()
          }
          "t" -> if (inCell && inInlineStr) {
            inInlineT = true
          }
        }
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inV || (inInlineStr && inInlineT)) {
          textBuf.append(ch, start, length)
        }
      }

      override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localTagName(localName, qName)) {
          "v" -> inV = false
          "t" -> inInlineT = false
          "is" -> inInlineStr = false
          "c" -> if (inCell) {
            val rawVal = textBuf.toString()
            val resolved = when (currentCellType) {
              "s" -> {
                val idx = rawVal.trim().toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
              }
              "b" -> if (rawVal.trim() == "1") "TRUE" else "FALSE"
              "inlineStr", "str" -> rawVal
              else -> normalizeNumericString(rawVal)
            }
            if (resolved.isNotEmpty()) {
              currentRowMap[currentColIndex] = resolved
              if (currentColIndex > maxColInRow) maxColInRow = currentColIndex
            }
            inCell = false
          }
          "row" -> if (inRow) {
            if (maxColInRow >= 0) {
              val rowList = MutableList(maxColInRow + 1) { col -> currentRowMap[col].orEmpty() }
              rows.add(rowList)
            }
            inRow = false
          }
        }
      }
    })
    return rows
  }

  private fun parseWithSax(xmlBytes: ByteArray, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance().apply {
      isNamespaceAware = false
    }
    val saxParser = factory.newSAXParser()
    saxParser.parse(ByteArrayInputStream(xmlBytes), handler)
  }

  internal fun normalizeNumericString(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    // Convert Excel scientific notation integers (e.g. phone numbers like 8.7784571986E10)
    if (trimmed.contains('e', ignoreCase = true)) {
      val converted = runCatching {
        BigDecimal(trimmed).stripTrailingZeros().toPlainString()
      }.getOrNull()
      if (converted != null && !converted.contains('.')) {
        return converted
      }
    }
    // Strip trailing ".0" / ".00" from integer numeric cells stored as floats in Excel
    val dotZeroMatch = Regex("^(-?[0-9]+)\\.0+$").matchEntire(trimmed)
    if (dotZeroMatch != null) {
      return dotZeroMatch.groupValues[1]
    }
    return trimmed
  }

  private fun columnIndexFromRef(cellRef: String): Int {
    var col = 0
    for (ch in cellRef) {
      if (ch in 'A'..'Z') {
        col = col * 26 + (ch - 'A' + 1)
      } else if (ch in 'a'..'z') {
        col = col * 26 + (ch - 'a' + 1)
      } else {
        break
      }
    }
    return (col - 1).coerceAtLeast(0)
  }

  private fun findAttrByLocalName(attributes: Attributes, attrLocalName: String): String? {
    for (i in 0 until attributes.length) {
      val name = attributes.getQName(i).ifBlank { attributes.getLocalName(i) }
      if (localName(name) == attrLocalName) {
        return attributes.getValue(i)
      }
    }
    return null
  }

  private fun localTagName(localName: String?, qName: String?): String {
    val raw = qName?.takeIf { it.isNotBlank() } ?: localName.orEmpty()
    return localName(raw)
  }

  private fun localName(tag: String?): String {
    if (tag == null) return ""
    val colon = tag.indexOf(':')
    return if (colon >= 0) tag.substring(colon + 1) else tag
  }
}
