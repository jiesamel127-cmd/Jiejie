package com.example.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RekapanCsvParserTest {

  @Test
  fun parseBundledRekapanJie_extractsAll13BatchesAndUnpaidRows() {
    val file = listOf(
      File("app/src/main/res/raw/rekapan_jie.csv"),
      File("src/main/res/raw/rekapan_jie.csv"),
    ).first { it.exists() }

    val result = RekapanCsvParser.parse(file.readText())
    assertEquals(13, result.totalBatches)
    assertEquals(160, result.totalItems)
    assertTrue(result.totalRemaining > 0L)
    assertEquals(4, result.unpaidItemsCount)

    // Verify Batch 1 grouping (nana4sho has 3 rows -> 1 member with 3 items)
    val batch1 = result.batches.first()
    assertEquals("BATCH-1", batch1.batchCode)
    val nana = batch1.members.first { it.memberName == "nana4sho" }
    assertEquals(3, nana.items.size)
    assertEquals(395_000L, nana.totalCost)
    assertEquals(0L, nana.remainingBalance)

    // Verify Batch 13 unpaid buyer (elan has 2 unpaid rows totaling Rp310,000)
    val batch13 = result.batches.last()
    assertEquals("BATCH-13", batch13.batchCode)
    val elan = batch13.members.first { it.memberName == "elan" }
    assertEquals(2, elan.items.size)
    assertEquals(310_000L, elan.totalCost)
    assertEquals(0L, elan.paidAmount)
    assertEquals(310_000L, elan.remainingBalance)
  }

  @Test
  fun parseMultiSheetXlsxWorkbook_extractsWarLelangAndJajananLainSheets() {
    val xlsxBytes = buildSampleMultiSheetXlsx()
    val sheets = XlsxWorkbookParser.parse(ByteArrayInputStream(xlsxBytes))
    assertEquals(3, sheets.size)
    assertEquals(listOf("War", "Lelang", "Jajanan Korea"), sheets.map { it.sheetName })

    val result = RekapanCsvParser.parseSheets(
      sheets.map { RekapanSheetInput(sheetName = it.sheetName, rows = it.rows) }
    )

    assertEquals(3, result.sheetNames.size)
    assertEquals(3, result.totalBatches)
    assertEquals(4, result.totalItems)
    assertTrue(result.batches.any { it.sheetName == "Lelang" && it.totalValue == 125_000L })
    assertTrue(result.batches.any { it.sheetName == "Jajanan Korea" && it.totalValue == 150_000L })
  }

  @Test
  fun parseBundledFullJiegoWorkbook_extractsAllSheetsAndRealisticTotals() {
    val file = listOf(
      File("app/src/main/res/raw/rekapan_jie_full.xlsx"),
      File("src/main/res/raw/rekapan_jie_full.xlsx"),
    ).first { it.exists() }

    val sheets = file.inputStream().use { XlsxWorkbookParser.parse(it) }
    val result = RekapanCsvParser.parseSheets(
      sheets.map { RekapanSheetInput(sheetName = it.sheetName, rows = it.rows) }
    )

    assertTrue("Expected at least 5 non-empty non-novel sheets, got ${result.sheetNames}", result.sheetNames.size >= 5)
    assertTrue(result.sheetNames.any { it.contains("LELANGWAR", ignoreCase = true) })
    assertTrue(result.sheetNames.any { it.contains("LNGSHOT", ignoreCase = true) })
    assertTrue(result.sheetNames.any { it.contains("KPOP", ignoreCase = true) })
    assertTrue(result.sheetNames.any { it.contains("TABUNGAN", ignoreCase = true) })
    assertTrue(result.sheetNames.any { it.contains("BARCHIN", ignoreCase = true) })
    assertTrue(result.sheetNames.none { it.contains("NOVEL", ignoreCase = true) })
    assertTrue(result.batches.none { it.sheetName.contains("NOVEL", ignoreCase = true) })

    // Ensure no item price slipped through as 480 billion or unscaled Rp 20
    val allItems = result.batches.flatMap { it.members }.flatMap { it.items }
    assertTrue(allItems.isNotEmpty())
    assertTrue(allItems.all { it.unitPrice == 0L || it.unitPrice >= 1_000L })
    assertTrue(allItems.all { it.unitPrice < 50_000_000L })
    assertTrue(result.totalRemaining > 0L)
  }

  @Test
  fun parsePriceAndGoogleSheetsUrl_handlesDpAndDriveUrl() {
    assertEquals(480_000L, RekapanCsvParser.parsePriceIdr("480,000 dp 210,000"))
    assertEquals(210_000L, RekapanCsvParser.extractInlineDp("480,000 dp 210,000"))
    assertEquals(90_000L, RekapanCsvParser.parseInstallmentIdr("(2) 90,000"))
    assertEquals("87784571986", XlsxWorkbookParser.normalizeNumericString("8.7784571986E10"))
    assertEquals(
      "https://docs.google.com/spreadsheets/d/1diP_VBtV6A7LJ_-_8czHX4ajKvamHOsKivykvcC4UF0/export?format=xlsx",
      GoogleSheetsFetcher.buildXlsxExportUrl(GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL)
    )
  }

  private fun buildSampleMultiSheetXlsx(): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      fun put(path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }

      put(
        "xl/workbook.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="War" sheetId="1" r:id="rId1"/>
            <sheet name="Lelang" sheetId="2" r:id="rId2"/>
            <sheet name="Jajanan Korea" sheetId="3" r:id="rId3"/>
          </sheets>
        </workbook>
        """.trimIndent()
      )

      put(
        "xl/_rels/workbook.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
        </Relationships>
        """.trimIndent()
      )

      val strings = listOf(
        "NO", "FOTO PC", "NAMA", "NOMOR HANDPHONE", "HARGA", "STATUS",
        "ael", "08119003825", "done payment",
        "ITEM", "PEMBELI", "NO HP", "TOTAL", "PEMBAYARAN",
        "PC Mingyu", "sinta", "081234567890", "belum lunas",
        "JAJANAN", "QTY", "HARGA", "NAMA PEMBELI", "WHATSAPP",
        "Samyang Buldak", "nadia", "08987654321", "lunas",
        "Tteokbokki Cup", "rara", "085612341234", "belum"
      )
      val siXml = strings.joinToString("") { "<si><t>$it</t></si>" }
      put(
        "xl/sharedStrings.xml",
        """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$siXml</sst>"""
      )

      // Sheet 1: War
      put(
        "xl/worksheets/sheet1.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c>
            </row>
            <row r="2">
              <c r="A2"><v>1</v></c><c r="C2" t="s"><v>6</v></c><c r="D2" t="s"><v>7</v></c><c r="E2"><v>152000.0</v></c><c r="F2" t="s"><v>8</v></c>
            </row>
          </sheetData>
        </worksheet>
        """.trimIndent()
      )

      // Sheet 2: Lelang
      put(
        "xl/worksheets/sheet2.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>9</v></c><c r="B1" t="s"><v>10</v></c><c r="C1" t="s"><v>11</v></c><c r="D1" t="s"><v>12</v></c><c r="E1" t="s"><v>13</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>14</v></c><c r="B2" t="s"><v>15</v></c><c r="C2" t="s"><v>16</v></c><c r="D2"><v>125000</v></c><c r="E2" t="s"><v>17</v></c>
            </row>
          </sheetData>
        </worksheet>
        """.trimIndent()
      )

      // Sheet 3: Jajanan Korea (with QTY column and no NO column)
      put(
        "xl/worksheets/sheet3.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>18</v></c><c r="B1" t="s"><v>19</v></c><c r="C1" t="s"><v>20</v></c><c r="D1" t="s"><v>21</v></c><c r="E1" t="s"><v>22</v></c><c r="F1" t="s"><v>5</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>23</v></c><c r="B2"><v>2</v></c><c r="C2"><v>50000</v></c><c r="D2" t="s"><v>24</v></c><c r="E2" t="s"><v>25</v></c><c r="F2" t="s"><v>26</v></c>
            </row>
            <row r="3">
              <c r="A3" t="s"><v>27</v></c><c r="B3"><v>1</v></c><c r="C3"><v>50000</v></c><c r="D3" t="s"><v>28</v></c><c r="E3" t="s"><v>29</v></c><c r="F3" t="s"><v>30</v></c>
            </row>
          </sheetData>
        </worksheet>
        """.trimIndent()
      )
    }
    return baos.toByteArray()
  }
}
