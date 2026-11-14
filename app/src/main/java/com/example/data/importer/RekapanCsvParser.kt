package com.example.data.importer

import com.example.data.model.GoStatus
import com.example.data.model.MemberOrderStatus
import kotlin.math.roundToLong

data class RekapanSheetInput(
  val sheetName: String,
  val rows: List<List<String>>,
)

data class ParsedRekapanItem(
  val itemName: String,
  val variant: String,
  val quantity: Long,
  val unitPrice: Long,
  val isPaid: Boolean,
  val checkoutDone: Boolean,
  val isShipped: Boolean,
  val isReceived: Boolean,
  val paidAmountOverride: Long? = null,
) {
  val totalCost: Long get() = quantity * unitPrice
  val effectivePaidAmount: Long
    get() = (paidAmountOverride ?: if (isPaid) totalCost else 0L).coerceIn(0L, totalCost)
  val isFullyPaid: Boolean
    get() = isPaid || (totalCost > 0L && effectivePaidAmount >= totalCost)
}

data class ParsedRekapanMember(
  val memberName: String,
  val phoneNumber: String,
  val items: List<ParsedRekapanItem>,
) {
  val totalCost: Long get() = items.sumOf { it.totalCost }
  val paidAmount: Long get() = items.sumOf { it.effectivePaidAmount }.coerceAtMost(totalCost)
  val remainingBalance: Long get() = (totalCost - paidAmount).coerceAtLeast(0L)

  val derivedStatus: MemberOrderStatus
    get() = when {
      items.isNotEmpty() && items.all { it.isReceived } -> MemberOrderStatus.COMPLETED
      items.any { it.isShipped } -> MemberOrderStatus.SHIPPED
      items.any { it.checkoutDone } -> MemberOrderStatus.ORDERED
      remainingBalance == 0L && totalCost > 0L -> MemberOrderStatus.CONFIRMED
      else -> MemberOrderStatus.WAITING_PAYMENT
    }
}

data class ParsedRekapanBatch(
  val batchTitle: String,
  val batchCode: String,
  val artistGroup: String,
  val members: List<ParsedRekapanMember>,
  val sheetName: String = "",
) {
  val totalItemsCount: Int get() = members.sumOf { it.items.size }
  val totalValue: Long get() = members.sumOf { it.totalCost }
  val totalPaid: Long get() = members.sumOf { it.paidAmount }
  val unpaidItemsCount: Int get() = members.sumOf { m -> m.items.count { !it.isFullyPaid } }

  val derivedGoStatus: GoStatus
    get() = when {
      members.any { it.remainingBalance > 0L } -> GoStatus.WAITING_PAYMENT
      members.isNotEmpty() && members.all { it.derivedStatus == MemberOrderStatus.COMPLETED } -> GoStatus.SHIPPED
      members.any { it.derivedStatus == MemberOrderStatus.SHIPPED } -> GoStatus.SHIPPED
      members.any { it.derivedStatus == MemberOrderStatus.ORDERED } -> GoStatus.ORDERED
      else -> GoStatus.CONFIRMED
    }
}

data class ParsedRekapanResult(
  val batches: List<ParsedRekapanBatch>,
  val sheetNames: List<String> = emptyList(),
) {
  val totalBatches: Int get() = batches.size
  val totalMembers: Int get() = batches.sumOf { it.members.size }
  val totalItems: Int get() = batches.sumOf { it.totalItemsCount }
  val totalValue: Long get() = batches.sumOf { it.totalValue }
  val totalPaid: Long get() = batches.sumOf { it.totalPaid }
  val totalRemaining: Long get() = (totalValue - totalPaid).coerceAtLeast(0L)
  val unpaidItemsCount: Int get() = batches.sumOf { it.unpaidItemsCount }
}

object RekapanCsvParser {

  fun parse(csvContent: String, defaultArtistGroup: String = "K-Pop Photocard"): ParsedRekapanResult {
    val rows = parseCsvRecords(csvContent)
    return parseSheetRows(
      rows = rows,
      sheetName = "",
      defaultArtistGroup = defaultArtistGroup,
    )
  }

  fun parseCsvFiles(
    files: List<Pair<String, String>>,
    defaultArtistGroup: String = "K-Pop Photocard",
  ): ParsedRekapanResult {
    val sheetInputs = files.map { (fileName, csvContent) ->
      val cleanName = fileName
        .substringBeforeLast('.')
        .trim()
      RekapanSheetInput(
        sheetName = cleanName,
        rows = parseCsvRecords(csvContent),
      )
    }
    return parseSheets(sheetInputs, defaultArtistGroup = defaultArtistGroup)
  }

  fun isExcludedSheet(sheetName: String): Boolean {
    return sheetName.trim().contains("NOVEL", ignoreCase = true)
  }

  fun parseSheets(
    sheets: List<RekapanSheetInput>,
    defaultArtistGroup: String = "K-Pop Photocard",
  ): ParsedRekapanResult {
    val allBatches = mutableListOf<ParsedRekapanBatch>()
    val usedSheets = mutableListOf<String>()

    sheets.forEach { sheet ->
      if (isExcludedSheet(sheet.sheetName)) return@forEach
      val parsed = parseSheetRows(
        rows = sheet.rows,
        sheetName = sheet.sheetName,
        defaultArtistGroup = defaultArtistGroup,
      )
      if (parsed.batches.isNotEmpty()) {
        allBatches.addAll(parsed.batches)
        if (sheet.sheetName.isNotBlank()) {
          usedSheets.add(sheet.sheetName.trim())
        }
      }
    }

    return ParsedRekapanResult(
      batches = allBatches,
      sheetNames = usedSheets.distinct(),
    )
  }

  fun parseSheetRows(
    rows: List<List<String>>,
    sheetName: String = "",
    defaultArtistGroup: String = "K-Pop Photocard",
  ): ParsedRekapanResult {
    val cleanSheetName = sheetName.trim()
    if (isExcludedSheet(cleanSheetName)) {
      return ParsedRekapanResult(batches = emptyList(), sheetNames = emptyList())
    }
    val batches = mutableListOf<ParsedRekapanBatch>()

    var currentBatchTitle: String? = null
    var hasSeenHeader = false
    var colNo = 0
    var colItem = 1
    var colVariant = -1
    var colNama = 2
    var colPhone = 3
    var colQty = -1
    var colHarga = 4
    var colTotalHarga = -1
    var colDp = -1
    var colTabungan = -1
    var colPayment = 5
    var colCheckout = 6
    var colShipped = 7
    var colReceived = 8

    data class RawRow(
      val no: String,
      val itemDesc: String,
      val variantDesc: String,
      val name: String,
      val phone: String,
      val qty: Long,
      val unitPrice: Long,
      val isPaid: Boolean,
      var paidAmountOverride: Long?,
      val checkoutDone: Boolean,
      val isShipped: Boolean,
      val isReceived: Boolean,
    )

    val currentRows = mutableListOf<RawRow>()
    var lastBuyerName = ""
    var lastBuyerPhone = ""

    fun flushBatch() {
      if (currentRows.isEmpty()) return

      // Normalize thousands shorthand (e.g. "20" = Rp20.000, "125" = Rp125.000),
      // even when a batch mixes shorthand rows with full-Rupiah rows (e.g. 25 alongside 342000).
      val scaledRows = currentRows.map { r ->
        val scaledPrice = if (r.unitPrice in 1L..999L) r.unitPrice * 1_000L else r.unitPrice
        val scaledPaid = r.paidAmountOverride?.let { p ->
          if (p in 1L..999L) p * 1_000L else p
        }
        r.copy(unitPrice = scaledPrice, paidAmountOverride = scaledPaid)
      }

      val rawTitle = currentBatchTitle
        ?: cleanSheetName.takeIf { it.isNotBlank() }
        ?: "REKAPAN BATCH ${batches.size + 1}"

      val title = if (
        cleanSheetName.isNotBlank() &&
        !rawTitle.contains(cleanSheetName, ignoreCase = true)
      ) {
        "$cleanSheetName • $rawTitle"
      } else {
        rawTitle
      }

      val batchNumMatch = Regex("BATCH\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(title)
      val batchCode = if (batchNumMatch != null) {
        val prefix = cleanSheetName
          .uppercase()
          .replace(Regex("[^A-Z0-9]+"), "")
          .take(10)
        if (prefix.isNotBlank() && !title.startsWith("PO WAR BATCH", ignoreCase = true)) {
          "$prefix-B${batchNumMatch.groupValues[1]}"
        } else {
          "BATCH-${batchNumMatch.groupValues[1]}"
        }
      } else if (cleanSheetName.isNotBlank()) {
        val cleanBase = cleanSheetName.uppercase().replace(Regex("[^A-Z0-9]+"), "-").trim('-').take(18)
        if (batches.isEmpty()) cleanBase.ifBlank { "SHEET-1" } else "$cleanBase-${batches.size + 1}"
      } else {
        "BATCH-${batches.size + 1}"
      }

      val grouped = linkedMapOf<String, MutableList<RawRow>>()
      for (r in scaledRows) {
        val cleanName = r.name.trim()
        val normPhone = r.phone.filter { it.isDigit() }
        val hasMeaningfulName = cleanName.any { it.isLetterOrDigit() } && cleanName !in setOf(".", "~", "-")
        val key = if (hasMeaningfulName) {
          "name:${cleanName.lowercase()}"
        } else if (normPhone.isNotEmpty()) {
          "phone:$normPhone"
        } else {
          "row:${r.no}_${grouped.size}"
        }
        grouped.getOrPut(key) { mutableListOf() }.add(r)
      }

      val members = grouped.values.map { buyerRows ->
        val first = buyerRows.first()
        val displayName = first.name.trim().ifBlank { "Pembeli #${first.no}" }.take(80)
        val displayPhone = buyerRows.map { it.phone.trim() }.firstOrNull { it.isNotBlank() }?.take(60) ?: ""
        val items = buyerRows.map { row ->
          val itemLabel = when {
            row.itemDesc.isNotBlank() -> "${row.itemDesc} (#${row.no})"
            row.variantDesc.isNotBlank() -> "${row.variantDesc} (#${row.no})"
            cleanSheetName.isNotBlank() -> "$cleanSheetName $batchCode (#${row.no})"
            else -> "Photocard $batchCode (#${row.no})"
          }
          val variantText = listOfNotNull(
            batchCode.takeIf { it.isNotBlank() },
            row.variantDesc.replace('\n', ',').trim().takeIf { it.isNotBlank() },
          ).joinToString(" • ")

          ParsedRekapanItem(
            itemName = itemLabel.take(120),
            variant = variantText.take(80),
            quantity = row.qty.coerceAtLeast(1L),
            unitPrice = row.unitPrice,
            isPaid = row.isPaid,
            checkoutDone = row.checkoutDone,
            isShipped = row.isShipped,
            isReceived = row.isReceived,
            paidAmountOverride = row.paidAmountOverride,
          )
        }
        ParsedRekapanMember(
          memberName = displayName,
          phoneNumber = displayPhone,
          items = items,
        )
      }

      batches.add(
        ParsedRekapanBatch(
          batchTitle = title.take(120),
          batchCode = batchCode.take(40),
          artistGroup = cleanSheetName.ifBlank { defaultArtistGroup }.take(60),
          members = members,
          sheetName = cleanSheetName,
        )
      )
      currentRows.clear()
      lastBuyerName = ""
      lastBuyerPhone = ""
    }

    for (row in rows) {
      val trimmedCells = row.map { it.trim() }
      val nonEmpty = trimmedCells.filter { it.isNotEmpty() }
      if (nonEmpty.isEmpty()) continue

      val firstCell = trimmedCells.firstOrNull().orEmpty()
      val nonEmptyText = nonEmpty.firstOrNull().orEmpty()
      val upperRow = trimmedCells.joinToString(" ").uppercase()

      // Skip legend/note lines like "done payment = sudah bayar"
      if (nonEmpty.size <= 3 && upperRow.contains(" = ")) continue

      // 1. Detect section title header row (e.g. "DAFTAR LIST WAR BATCH 1", "TABUNGAN ... BATCH 1")
      val isHeaderKeywordsRow = isColumnHeaderRow(trimmedCells)
      if (!isHeaderKeywordsRow && isSectionTitleRow(firstCell, nonEmpty, upperRow)) {
        flushBatch()
        currentBatchTitle = normalizeBatchTitle(nonEmptyText)
        hasSeenHeader = false
        continue
      }

      // 2. Detect column header row across diverse sheet layouts
      if (isHeaderKeywordsRow) {
        colNo = findColumnIndex(trimmedCells) { h ->
          h == "NO" || h == "NO." || h == "#" || h == "NOMOR"
        } ?: -1

        // Prefer descriptive item names over "FOTO BARANG" (which is often an embedded image column)
        colItem = findColumnIndex(trimmedCells) { h ->
          h.contains("NAMA BARANG") || h.contains("NAMA NOVEL") || h.contains("JAJAN") ||
            h.contains("NAMA ITEM") || h == "ITEM" || h.contains("PRODUK") || h.contains("MERCH") ||
            h.contains("PESANAN") || h.contains("ALBUM")
        } ?: findColumnIndex(trimmedCells) { h ->
          h.contains("FOTO") || h.contains("BARANG") || h.contains("ORDER") ||
            h.contains("DESKRIPSI") || h.contains("KETERANGAN") || h == "PC"
        } ?: -1

        colVariant = findColumnIndex(trimmedCells) { h ->
          h == "JENIS" || h.contains("LIST PRIO") || h.contains("FREE GANCI") ||
            h == "MARKET" || h.contains("VARIAN") || h.contains("VERSI")
        } ?: -1

        colNama = findColumnIndex(trimmedCells) { h ->
          h == "NAMA" || h == "NAMA PEMBELI" || h == "NAMA BUYER" || h == "USN" ||
            (!h.contains("BARANG") && !h.contains("NOVEL") && !h.contains("ITEM") && !h.contains("PRODUK") && !h.contains("GROUP") &&
              (h.contains("NAMA") || h.contains("PEMBELI") || h.contains("BUYER") || h.contains("USERNAME") || h.contains("UNAME") || h.contains("CUST") || h == "CUS"))
        } ?: -1

        colPhone = findColumnIndex(trimmedCells) { h ->
          h.contains("HANDPHONE") || h.contains("TELEPON") || h.contains("HP") ||
            h.contains("WHATSAPP") || h == "WA" || h.contains("NO WA") || h.contains("NO. WA") ||
            h.contains("NOMOR WA") || h.contains("KONTAK") || h.contains("TELP") || h.contains("PHONE")
        } ?: -1

        colTotalHarga = findColumnIndex(trimmedCells) { h ->
          h.contains("TOTAL HARGA") || h.contains("TOTAL PAYMENT") || h.contains("TOTAL TAGIHAN") ||
            h.contains("SUBTOTAL")
        } ?: -1

        val explicitHargaCol = findColumnIndex(trimmedCells) { h ->
          h == "HARGA" || h.contains("HARGA SATUAN") || h == "PRICE" || h.contains("NOMINAL")
        }
        colHarga = explicitHargaCol
          ?: if (colTotalHarga >= 0) colTotalHarga else findColumnIndex(trimmedCells) { h -> h == "TOTAL" } ?: -1

        colQty = findColumnIndex(trimmedCells) { h ->
          h == "QTY" || h.contains("JUMLAH") || h == "JML" || h == "PCS" ||
            h.contains("TOTAL BARANG") || h.contains("TOTAL NOVEL")
        } ?: if (explicitHargaCol != null) {
          findColumnIndex(trimmedCells) { h -> h == "TOTAL" }
            ?.takeIf { it != colHarga && it != colTotalHarga }
            ?: -1
        } else {
          -1
        }

        colDp = findColumnIndex(trimmedCells) { h ->
          h == "DP" || h.contains("UANG MUKA")
        } ?: -1

        colTabungan = findColumnIndex(trimmedCells) { h ->
          h.contains("TABUNGAN") || h.contains("CICILAN") || h.contains("TERBAYAR") || h.contains("SUDAH BAYAR")
        } ?: -1

        colPayment = findColumnIndex(trimmedCells) { h ->
          h == "STATUS" || h == "PAYMENT" || h.contains("PEMBAYARAN") ||
            h.contains("KET BAYAR") || h == "LUNAS" || h.contains("STATUS BAYAR")
        } ?: -1

        colCheckout = findColumnIndex(trimmedCells) { h ->
          h == "CHECKOUT" || h == "CO" || h.contains("CHECK OUT")
        } ?: if (colPayment >= 0) colPayment + 1 else -1

        colShipped = findColumnIndex(trimmedCells) { h ->
          h.contains("DIKIRIM") || h.contains("SHIPPED") || h == "KIRIM" || h == "RESI"
        } ?: -1

        colReceived = findColumnIndex(trimmedCells) { h ->
          h.contains("DITERIMA") || h.contains("RECEIVED") || h == "SELESAI"
        } ?: -1

        hasSeenHeader = true
        continue
      }

      // 3. Skip subheader / TOTAL summary rows
      val joinedUpper = nonEmpty.joinToString(" ").uppercase()
      if (isSummaryOrSubheaderRow(firstCell, trimmedCells, joinedUpper)) {
        continue
      }

      // 4. Data row extraction
      val noVal = if (colNo >= 0) trimmedCells.getOrNull(colNo).orEmpty() else firstCell
      val noInt = noVal.toIntOrNull()
      val rawName = if (colNama >= 0) trimmedCells.getOrNull(colNama).orEmpty() else ""
      val rawPhone = normalizePhone(if (colPhone >= 0) trimmedCells.getOrNull(colPhone).orEmpty() else "")
      val rawItem = if (colItem >= 0) trimmedCells.getOrNull(colItem).orEmpty() else ""
      val rawVariant = if (colVariant >= 0) trimmedCells.getOrNull(colVariant).orEmpty() else ""
      val rawQty = if (colQty >= 0) trimmedCells.getOrNull(colQty).orEmpty() else ""
      val rawHargaCell = if (colHarga >= 0) trimmedCells.getOrNull(colHarga).orEmpty() else ""
      val rawTotalCell = if (colTotalHarga >= 0) trimmedCells.getOrNull(colTotalHarga).orEmpty() else ""
      val rawDpCell = if (colDp >= 0) trimmedCells.getOrNull(colDp).orEmpty() else ""
      val rawTabunganCell = if (colTabungan >= 0) trimmedCells.getOrNull(colTabungan).orEmpty() else ""
      val rawPayment = if (colPayment >= 0) trimmedCells.getOrNull(colPayment).orEmpty().lowercase() else ""
      val rawCheckout = if (colCheckout >= 0) trimmedCells.getOrNull(colCheckout).orEmpty() else ""
      val rawShipped = if (colShipped >= 0) trimmedCells.getOrNull(colShipped).orEmpty() else ""
      val rawReceived = if (colReceived >= 0) trimmedCells.getOrNull(colReceived).orEmpty() else ""

      val parsedHarga = parsePriceIdr(rawHargaCell)
      val parsedTotal = parsePriceIdr(rawTotalCell)
      val inlineDp = extractInlineDp(rawHargaCell)
      val dpAmount = parsePriceIdr(rawDpCell).let { if (it > 0L) it else inlineDp }
      val tabunganAmount = parseInstallmentIdr(rawTabunganCell)

      val parsedQty = rawQty.replace(Regex("[^0-9]"), "").toLongOrNull()?.coerceIn(1L, 999L) ?: 1L

      // Handle installment/sub-item continuation rows (e.g. TABUNGAN SWISH rows (2), (3) or WAR NOVEL extra books)
      if (noInt == null && rawName.isBlank() && lastBuyerName.isNotBlank()) {
        if (tabunganAmount > 0L && rawItem.isBlank() && parsedHarga == 0L && parsedTotal == 0L) {
          val lastRow = currentRows.lastOrNull()
          if (lastRow != null) {
            val existingPaid = lastRow.paidAmountOverride ?: 0L
            lastRow.paidAmountOverride = existingPaid + tabunganAmount
          }
          continue
        }
      }

      // Determine effective qty and unitPrice without double-multiplying subtotals
      var qty = parsedQty
      var unitPrice = when {
        parsedHarga > 0L && parsedTotal > 0L -> {
          if (qty > 1L && parsedTotal == parsedHarga * qty) {
            parsedHarga
          } else if (qty > 1L && parsedTotal > parsedHarga) {
            // Multi-row buyer summary (e.g. WAR NOVEL "Total novel=3, harga=90, Total Payment=125")
            qty = 1L
            parsedHarga
          } else {
            qty = 1L
            parsedTotal
          }
        }
        parsedHarga > 0L -> {
          // If Harga cell holds combined total (e.g. TABUNGAN SWISH "480,000 dp 210,000" for qty=3), split evenly
          if (qty > 1L && colTotalHarga < 0 && (rawHargaCell.contains("dp", ignoreCase = true) || (parsedHarga % qty == 0L && parsedHarga / qty >= 50_000L))) {
            parsedHarga / qty
          } else {
            parsedHarga
          }
        }
        parsedTotal > 0L -> {
          qty = 1L
          parsedTotal
        }
        else -> 0L
      }

      val acceptRow = when {
        noInt != null -> rawName.isNotBlank() || rawPhone.isNotBlank() || rawItem.isNotBlank() || unitPrice > 0L
        hasSeenHeader && (rawName.isNotBlank() || (lastBuyerName.isNotBlank() && rawItem.isNotBlank() && unitPrice > 0L)) -> true
        else -> false
      }
      if (!acceptRow) continue

      val resolvedName = rawName.ifBlank {
        if (noInt == null && lastBuyerName.isNotBlank()) lastBuyerName else ""
      }
      val resolvedPhone = rawPhone.ifBlank {
        if (noInt == null && lastBuyerPhone.isNotBlank()) lastBuyerPhone else ""
      }

      if (resolvedName.isNotBlank()) {
        lastBuyerName = resolvedName
        if (resolvedPhone.isNotBlank()) lastBuyerPhone = resolvedPhone
      }

      val isPaid = isPaymentFullyPaid(rawPayment)
      val partialPaid = when {
        isPaid -> null // full cost
        tabunganAmount > 0L -> tabunganAmount
        dpAmount > 0L -> dpAmount
        else -> null
      }

      val rowNumber = noInt?.toString() ?: (currentRows.size + 1).toString()

      currentRows.add(
        RawRow(
          no = rowNumber,
          itemDesc = rawItem,
          variantDesc = rawVariant,
          name = resolvedName,
          phone = resolvedPhone,
          qty = qty,
          unitPrice = unitPrice,
          isPaid = isPaid,
          paidAmountOverride = partialPaid,
          checkoutDone = isTruthfulFlag(rawCheckout),
          isShipped = isTruthfulFlag(rawShipped),
          isReceived = isTruthfulFlag(rawReceived),
        )
      )
    }

    flushBatch()
    return ParsedRekapanResult(
      batches = batches,
      sheetNames = if (batches.isNotEmpty() && cleanSheetName.isNotBlank()) listOf(cleanSheetName) else emptyList(),
    )
  }

  private fun isTruthfulFlag(raw: String): Boolean {
    val clean = raw.trim().lowercase()
    return clean == "true" || clean == "ya" || clean == "yes" ||
      clean == "done" || clean == "1" || clean.contains("done checkout")
  }

  private fun isPaymentFullyPaid(rawPayment: String): Boolean {
    if (rawPayment.isBlank()) return false
    if (
      rawPayment.contains("not") ||
      rawPayment.contains("belum") ||
      rawPayment.contains("unpaid") ||
      rawPayment.contains("hutang") ||
      rawPayment.contains("utang") ||
      rawPayment.contains("menabung") ||
      rawPayment.contains("cicil") ||
      rawPayment.contains("dp") ||
      rawPayment == "false"
    ) {
      return false
    }
    return rawPayment.contains("done") ||
      rawPayment.contains("lunas") ||
      rawPayment.contains("pelunasan") ||
      rawPayment.contains("paid") ||
      rawPayment.contains("fullpay") ||
      rawPayment.contains("full pay") ||
      rawPayment.contains("full checkout") ||
      rawPayment.contains("sudah") ||
      rawPayment == "ok" ||
      rawPayment == "ya" ||
      rawPayment == "yes" ||
      rawPayment == "true"
  }

  private fun normalizePhone(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isEmpty()) return trimmed
    // Excel strips leading zero when stored as numeric float (e.g. 087784571986 -> 87784571986)
    if (!trimmed.startsWith("0") && !trimmed.startsWith("62") && !trimmed.startsWith("+") &&
      digits.startsWith("8") && digits.length in 9..13
    ) {
      return "0$digits"
    }
    return trimmed
  }

  private fun isColumnHeaderRow(cells: List<String>): Boolean {
    val uppers = cells.map { it.trim().uppercase() }
    val hasBuyerCol = uppers.any {
      it == "NAMA" || it == "NAMA PEMBELI" || it == "NAMA BUYER" || it == "USN" ||
        (!it.contains("BARANG") && !it.contains("NOVEL") && !it.contains("ITEM") && !it.contains("PRODUK") &&
          (it.contains("NAMA") || it.contains("PEMBELI") || it.contains("BUYER") || it.contains("USERNAME") || it.contains("UNAME") || it.contains("CUST")))
    }
    val hasPriceOrItemOrPhoneCol = uppers.any {
      it == "HARGA" || it.contains("TOTAL") || it.contains("NOMINAL") || it.contains("TAGIHAN") ||
        it.contains("HANDPHONE") || it.contains("TELEPON") || it.contains("HP") ||
        it == "WA" || it.contains("WHATSAPP") || it.contains("TELP") ||
        it.contains("BARANG") || it.contains("NOVEL") || it.contains("ITEM") || it.contains("JAJAN") ||
        it.contains("PRODUK") || it == "STATUS" || it == "PAYMENT"
    }
    return hasBuyerCol && hasPriceOrItemOrPhoneCol
  }

  private fun isSectionTitleRow(firstCell: String, nonEmpty: List<String>, upperRow: String): Boolean {
    if (firstCell.toIntOrNull() != null) return false
    if (nonEmpty.size > 3) return false
    val firstUpper = nonEmpty.firstOrNull()?.uppercase().orEmpty()
    if (firstUpper.contains("PROOF PACKING") || firstUpper.startsWith("CLOSE PO")) return false
    return firstUpper.contains("DAFTAR LIST") ||
      firstUpper.startsWith("TABUNGAN") ||
      firstUpper.contains("BATCH") ||
      firstUpper.startsWith("LIST ") ||
      firstUpper.startsWith("REKAP ")
  }

  private fun isSummaryOrSubheaderRow(firstCell: String, cells: List<String>, joinedUpper: String): Boolean {
    if (firstCell.equals("TOTAL", ignoreCase = true) ||
      firstCell.equals("SUBTOTAL", ignoreCase = true) ||
      firstCell.equals("GRAND TOTAL", ignoreCase = true) ||
      firstCell.equals("JUMLAH", ignoreCase = true)
    ) {
      return true
    }
    val uppers = cells.map { it.trim().uppercase() }
    if (uppers.any { it == "PAYMENT" || it == "CHECKOUT" } && !uppers.any { it.contains("DONE") || it.contains("NOT") }) {
      return true
    }
    if (joinedUpper.contains("TOTAL HARGA") || joinedUpper.contains("TOTAL PC") || joinedUpper.contains("TOTAAL BARANG")) {
      return true
    }
    return false
  }

  private fun findColumnIndex(cells: List<String>, predicate: (String) -> Boolean): Int? {
    val idx = cells.indexOfFirst { cell ->
      val upper = cell.trim().uppercase()
      upper.isNotEmpty() && predicate(upper)
    }
    return idx.takeIf { it >= 0 }
  }

  internal fun parsePriceIdr(raw: String): Long {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isEmpty()) return 0L

    // Extract only the primary price before "dp ..." or explanatory suffix
    val primarySegment = trimmed
      .substringBefore(" dp")
      .substringBefore("dp ")
      .trim()

    val shorthand = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(k|rb|ribu)$").matchEntire(primarySegment)
    if (shorthand != null) {
      val num = shorthand.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
      return (num * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    val firstNumToken = Regex("([0-9][0-9.,]*)").find(primarySegment)?.groupValues?.get(1)
      ?: return 0L
    val withoutTwoDec = firstNumToken.replace(Regex("\\.[0-9]{1,2}$"), "")
    return withoutTwoDec.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
  }

  internal fun extractInlineDp(raw: String): Long {
    val match = Regex("\\bdp\\s*([0-9][0-9.,]*\\s*(?:k|rb|ribu)?)", RegexOption.IGNORE_CASE).find(raw)
      ?: return 0L
    return parsePriceIdr(match.groupValues[1])
  }

  internal fun parseInstallmentIdr(raw: String): Long {
    val cleaned = raw.trim().replace(Regex("^\\([0-9]+\\)\\s*"), "")
    return parsePriceIdr(cleaned)
  }

  private fun normalizeBatchTitle(raw: String): String {
    val cleaned = raw.trim().replace(Regex("\\s+"), " ")
    return if (cleaned.uppercase().startsWith("DAFTAR LIST ")) {
      "PO ${cleaned.substring("DAFTAR LIST ".length)}"
    } else {
      cleaned
    }
  }

  /**
   * RFC-4180 compliant multi-line CSV parser supporting quoted commas & newlines.
   */
  private fun parseCsvRecords(input: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentField = StringBuilder()
    var inQuotes = false
    var i = 0
    val len = input.length

    while (i < len) {
      val ch = input[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < len && input[i + 1] == '"') {
            currentField.append('"')
            i += 2
            continue
          } else {
            inQuotes = false
          }
        } else {
          currentField.append(ch)
        }
      } else {
        when (ch) {
          '"' -> inQuotes = true
          ',' -> {
            currentRow.add(currentField.toString())
            currentField.clear()
          }
          '\r' -> {
            if (i + 1 < len && input[i + 1] == '\n') i++
            currentRow.add(currentField.toString())
            currentField.clear()
            records.add(currentRow.toList())
            currentRow.clear()
          }
          '\n' -> {
            currentRow.add(currentField.toString())
            currentField.clear()
            records.add(currentRow.toList())
            currentRow.clear()
          }
          else -> currentField.append(ch)
        }
      }
      i++
    }

    if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentField.toString())
      records.add(currentRow.toList())
    }
    return records
  }
}
