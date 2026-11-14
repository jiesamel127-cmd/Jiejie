package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import java.io.File

private const val QRIS_IMAGE_FILENAME = "jiego_qris_pembelian.jpg"

fun qrisFile(context: Context): File = File(context.filesDir, QRIS_IMAGE_FILENAME)

@Composable
fun QrisPaymentDialog(
  title: String = "QRIS Pembelian JIEGO",
  subtitle: String? = null,
  amountToPay: Long? = null,
  isBuyerMode: Boolean = false,
  onMarkPaidViaQris: (() -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var refreshVersion by remember { mutableIntStateOf(0) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  val qrisBitmap = remember(refreshVersion) {
    val file = qrisFile(context)
    if (file.exists() && file.length() > 0L) {
      runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    } else {
      null
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        val target = qrisFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Gagal membaca gambar QRIS.")
      }.onSuccess {
        errorMsg = null
        refreshVersion++
      }.onFailure { err ->
        errorMsg = err.localizedMessage ?: "Gagal menyimpan foto QRIS."
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 500.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (amountToPay != null && amountToPay > 0L) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
              Text(
                text = "Nominal Tagihan QRIS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = formatIdr(amountToPay),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }

        // QRIS Frame Card
        Surface(
          color = Color.White,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = "QRIS • JIEGO",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              )
            }

            if (qrisBitmap != null) {
              Image(
                bitmap = qrisBitmap,
                contentDescription = "Foto QRIS Pembelian JIEGO",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 220.dp, max = 320.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .testTag("qris_uploaded_image"),
              )
            } else {
              Image(
                painter = painterResource(id = R.drawable.img_jiego_qris),
                contentDescription = "Foto QRIS Resmi JIEGO",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 220.dp, max = 320.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .testTag("qris_default_image"),
              )
            }
            Text(
              text = "Scan QRIS di atas via e-wallet / m-banking lalu konfirmasi pembayaran ke Owner JIEJIE.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }

        if (errorMsg != null) {
          Text(
            text = errorMsg!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (!isBuyerMode) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("pick_qris_photo_button"),
            ) {
              Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Ganti Foto QRIS")
            }

            if (qrisBitmap != null) {
              OutlinedButton(
                onClick = {
                  qrisFile(context).delete()
                  refreshVersion++
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .height(48.dp)
                  .testTag("remove_qris_photo_button"),
              ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Reset ke QRIS bawaan")
              }
            }
          }
        } else {
          Button(
            onClick = {
              val amountNote = if (amountToPay != null && amountToPay > 0L) {
                "Nominal transfer: ${formatIdr(amountToPay)}"
              } else {
                ""
              }
              val detailNote = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, amountNote.takeIf { it.isNotBlank() })
                .joinToString("\n")
              val message = if (detailNote.isNotBlank()) {
                "Halo Owner JIEJIE, saya ingin konfirmasi pembayaran QRIS.\n$detailNote"
              } else {
                JiegoContacts.OWNER_JIEJIE.defaultMessage
              }
              JiegoContacts.openWhatsApp(context, JiegoContacts.OWNER_JIEJIE, message)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("buyer_confirm_qris_wa_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kirim Bukti ke WA Owner JIEJIE")
          }
        }

        if (!isBuyerMode && onMarkPaidViaQris != null && amountToPay != null && amountToPay > 0L) {
          Button(
            onClick = onMarkPaidViaQris,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("confirm_qris_paid_button"),
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Konfirmasi Sudah Bayar QRIS")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}
