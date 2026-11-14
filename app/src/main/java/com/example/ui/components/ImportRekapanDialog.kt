package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.importer.GoogleSheetsFetcher
import com.example.data.importer.ParsedRekapanResult
import com.example.data.importer.RekapanCsvParser
import com.example.data.importer.RekapanSheetInput
import com.example.data.importer.XlsxWorkbookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
fun ImportRekapanDialog(
  onDismiss: () -> Unit,
  onConfirmImport: (ParsedRekapanResult) -> Unit,
  initialSheetUrl: String = GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL,
  initialAutoSyncEnabled: Boolean = true,
  onSaveSheetSettings: (url: String, autoSyncEnabled: Boolean) -> Unit = { _, _ -> },
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var artistGroup by remember { mutableStateOf("K-Pop Photocard") }
  var sheetUrl by remember { mutableStateOf(initialSheetUrl.ifBlank { GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL }) }
  var autoSyncEnabled by remember { mutableStateOf(initialAutoSyncEnabled) }
  var sourceLabel by remember { mutableStateOf<String?>(null) }
  var rawCsvText by remember { mutableStateOf("") }
  var parsedFromFiles by remember { mutableStateOf<ParsedRekapanResult?>(null) }
  var parseError by remember { mutableStateOf<String?>(null) }
  var isLoadingSheet by remember { mutableStateOf(false) }

  val previewResult = remember(rawCsvText, artistGroup, parsedFromFiles) {
    parsedFromFiles ?: if (rawCsvText.isBlank()) {
      null
    } else {
      runCatching {
        RekapanCsvParser.parse(rawCsvText, defaultArtistGroup = artistGroup.ifBlank { "K-Pop Photocard" })
      }.getOrNull()
    }
  }

  val multiFilePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) {
      scope.launch {
        isLoadingSheet = true
        parseError = null
        val resultPair = withContext(Dispatchers.IO) {
          runCatching {
            parseSelectedRekapanUris(
              context = context,
              uris = uris,
              defaultArtistGroup = artistGroup.ifBlank { "K-Pop Photocard" },
            )
          }
        }
        isLoadingSheet = false
        resultPair.onSuccess { (label, result) ->
          parsedFromFiles = result
          rawCsvText = ""
          sourceLabel = label
          parseError = if (result.batches.isEmpty()) {
            "File terbaca, namun belum ada baris pembeli yang cocok (pastikan ada kolom NAMA dan HARGA/TOTAL)."
          } else {
            null
          }
        }.onFailure { err ->
          parseError = err.localizedMessage ?: "Gagal membaca file Excel/CSV."
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Impor Semua Sheet Rekapan", style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Tarik langsung semua tab Google Sheets (War, Lelang, LS LNGSHOT, KPOP, Tabungan Swish, Barchin) atau pilih file Excel (.xlsx) / CSV. Tab Novel otomatis dilewati.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 1. Direct Google Sheets Link & 1-Tap Full Workbook Card
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Impor Langsung Google Sheets JIEGO",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
              )
            }
            OutlinedTextField(
              value = sheetUrl,
              onValueChange = {
                sheetUrl = it
                onSaveSheetSettings(it, autoSyncEnabled)
              },
              label = { Text("Link Google Sheets") },
              leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("google_sheet_url_input"),
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = "Sinkron otomatis saat Pemilik buka aplikasi",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Baris & batch baru di Google Sheets otomatis ditambahkan tanpa duplikat.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
              }
              Switch(
                checked = autoSyncEnabled,
                onCheckedChange = { checked ->
                  autoSyncEnabled = checked
                  onSaveSheetSettings(sheetUrl, checked)
                },
                modifier = Modifier.testTag("auto_sync_sheets_switch"),
              )
            }
            Button(
              onClick = {
                if (isLoadingSheet) return@Button
                onSaveSheetSettings(sheetUrl, autoSyncEnabled)
                scope.launch {
                  isLoadingSheet = true
                  parseError = null
                  val fetchResult = runCatching {
                    GoogleSheetsFetcher.fetchAndParse(
                      rawUrlOrId = sheetUrl,
                      defaultArtistGroup = artistGroup.ifBlank { "K-Pop Photocard" },
                    )
                  }
                  isLoadingSheet = false
                  fetchResult.onSuccess { result ->
                    parsedFromFiles = result
                    rawCsvText = ""
                    sourceLabel = "Google Sheets Live (${result.sheetNames.size} Sheet)"
                    parseError = if (result.batches.isEmpty()) {
                      "Spreadsheet terbaca, tetapi tidak ditemukan baris pembeli."
                    } else {
                      null
                    }
                  }.onFailure { err ->
                    parseError = err.localizedMessage ?: "Gagal mengambil data dari Google Sheets."
                  }
                }
              },
              enabled = !isLoadingSheet && sheetUrl.isNotBlank(),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("fetch_google_sheet_button"),
            ) {
              if (isLoadingSheet) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Mengunduh Semua Sheet...")
              } else {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ambil Live dari Link Google Sheets")
              }
            }
            OutlinedButton(
              onClick = {
                if (isLoadingSheet) return@OutlinedButton
                scope.launch {
                  isLoadingSheet = true
                  parseError = null
                  val bundledResult = withContext(Dispatchers.IO) {
                    runCatching {
                      context.resources.openRawResource(R.raw.rekapan_jie_full).use { input ->
                        val sheets = XlsxWorkbookParser.parse(input)
                        RekapanCsvParser.parseSheets(
                          sheets = sheets.map { RekapanSheetInput(it.sheetName, it.rows) },
                          defaultArtistGroup = artistGroup.ifBlank { "K-Pop Photocard" },
                        )
                      }
                    }
                  }
                  isLoadingSheet = false
                  bundledResult.onSuccess { result ->
                    parsedFromFiles = result
                    rawCsvText = ""
                    sourceLabel = "Rekapan JIEGO Lengkap (${result.sheetNames.size} Sheet)"
                  }.onFailure { err ->
                    parseError = err.localizedMessage ?: "Gagal memuat rekapan bawaan."
                  }
                }
              },
              enabled = !isLoadingSheet,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("load_bundled_rekapan_button"),
            ) {
              Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Muat Cepat Semua Sheet Bawaan")
            }
          }
        }

        // 2. Local Excel (.xlsx) & Multi-CSV File Picker
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
          ) {
            Text(
              text = "Atau Pilih File dari Memori HP (.xlsx / CSV)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            OutlinedButton(
              onClick = {
                multiFilePickerLauncher.launch(
                  arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/csv",
                    "text/comma-separated-values",
                    "text/*",
                    "application/csv",
                    "*/*",
                  )
                )
              },
              enabled = !isLoadingSheet,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pick_csv_file_button"),
            ) {
              Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Pilih File Excel (.xlsx) / Banyak CSV")
            }
          }
        }

        OutlinedTextField(
          value = artistGroup,
          onValueChange = { artistGroup = it.take(60) },
          label = { Text("Label Default Artis / Kategori GO") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = if (rawCsvText.length > 1500) rawCsvText.take(1500) + "\n... (terpotong untuk pratinjau)" else rawCsvText,
          onValueChange = {
            parsedFromFiles = null
            rawCsvText = it
            sourceLabel = "Teks CSV Tempelan"
            parseError = null
          },
          label = { Text("Atau Tempel Teks CSV di Sini") },
          placeholder = { Text("DAFTAR LIST WAR BATCH 1\nNO,FOTO PC,NAMA,NOMOR HANDPHONE,HARGA,STATUS...") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )

        if (parseError != null) {
          Text(
            text = parseError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (previewResult != null) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(12.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = sourceLabel ?: "Pratinjau Rekapan",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              if (previewResult.batches.isEmpty()) {
                Text(
                  text = "Belum terdeteksi baris pembeli. Pastikan format kolom memiliki NAMA dan HARGA/TOTAL.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              } else {
                val sheetLine = if (previewResult.sheetNames.isNotEmpty()) {
                  "• Sheet terbaca (${previewResult.sheetNames.size}): ${previewResult.sheetNames.joinToString(", ")}\n"
                } else {
                  ""
                }
                Text(
                  text = sheetLine +
                    "• ${previewResult.totalBatches} Grup Order (Batch/Sheet)\n" +
                    "• ${previewResult.totalMembers} Pembeli • ${previewResult.totalItems} Item\n" +
                    "• Total Nilai: ${formatIdr(previewResult.totalValue)}\n" +
                    "• Belum Lunas: ${previewResult.unpaidItemsCount} item (${formatIdr(previewResult.totalRemaining)})",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (previewResult != null && previewResult.batches.isNotEmpty()) {
            onConfirmImport(previewResult)
          }
        },
        enabled = !isLoadingSheet && previewResult != null && previewResult.batches.isNotEmpty(),
        modifier = Modifier.testTag("confirm_import_rekapan_button"),
      ) {
        Text("Impor Sekarang")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

private fun parseSelectedRekapanUris(
  context: Context,
  uris: List<Uri>,
  defaultArtistGroup: String,
): Pair<String, ParsedRekapanResult> {
  val allSheets = mutableListOf<RekapanSheetInput>()
  val fileNames = mutableListOf<String>()

  for (uri in uris) {
    val displayName = queryFileName(context, uri)
    fileNames.add(displayName)
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()

    // Check ZIP magic header without loading large media entries into RAM
    val isZipXlsx = context.contentResolver.openInputStream(uri)?.use { stream ->
      val hdr = ByteArray(4)
      val count = stream.read(hdr)
      count == 4 && hdr[0] == 0x50.toByte() && hdr[1] == 0x4B.toByte() && hdr[2] == 0x03.toByte() && hdr[3] == 0x04.toByte()
    } ?: false

    if (isZipXlsx || displayName.endsWith(".xlsx", ignoreCase = true) || mime.contains("spreadsheetml")) {
      val workbookSheets = context.contentResolver.openInputStream(uri)?.use { stream ->
        XlsxWorkbookParser.parse(stream)
      } ?: throw IllegalStateException("File $displayName tidak bisa dibaca.")
      workbookSheets.forEach { sheet ->
        allSheets.add(RekapanSheetInput(sheetName = sheet.sheetName, rows = sheet.rows))
      }
    } else {
      val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: throw IllegalStateException("File $displayName tidak bisa dibaca.")
      val cleanSheetName = displayName.substringBeforeLast('.').ifBlank { "CSV" }
      allSheets.add(
        RekapanSheetInput(
          sheetName = cleanSheetName,
          rows = splitCsvViaParser(text),
        )
      )
    }
  }

  val finalResult = RekapanCsvParser.parseSheets(
    sheets = allSheets,
    defaultArtistGroup = defaultArtistGroup,
  )
  val label = if (fileNames.size == 1) {
    "${fileNames.first()} (${finalResult.sheetNames.size.coerceAtLeast(1)} Sheet)"
  } else {
    "${fileNames.size} File (${finalResult.sheetNames.size.coerceAtLeast(1)} Sheet)"
  }
  return label to finalResult
}

private fun splitCsvViaParser(csvText: String): List<List<String>> {
  val records = mutableListOf<List<String>>()
  val currentRow = mutableListOf<String>()
  val currentField = StringBuilder()
  var inQuotes = false
  var i = 0
  val len = csvText.length

  while (i < len) {
    val ch = csvText[i]
    if (inQuotes) {
      if (ch == '"') {
        if (i + 1 < len && csvText[i + 1] == '"') {
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
          if (i + 1 < len && csvText[i + 1] == '\n') i++
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

private fun queryFileName(context: Context, uri: Uri): String {
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx >= 0) {
        val name = cursor.getString(idx)
        if (!name.isNullOrBlank()) return name
      }
    }
  }
  return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "rekapan" } ?: "rekapan"
}
