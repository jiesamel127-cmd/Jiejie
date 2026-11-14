package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class JiegoWhatsAppContact(
  val role: String,
  val name: String,
  val displayNumber: String,
  val waDigits: String,
  val defaultMessage: String,
)

object JiegoContacts {
  const val SHOPEE_URL = "https://id.shp.ee/dABi8Yvg"

  val OWNER_JIEJIE = JiegoWhatsAppContact(
    role = "Owner",
    name = "Owner JIEJIE",
    displayNumber = "082190937138",
    waDigits = "6282190937138",
    defaultMessage = "Halo Owner JIEJIE, saya ingin konfirmasi pesanan / pembayaran di JIEGO.",
  )

  val ADMIN_ISE = JiegoWhatsAppContact(
    role = "Admin Rekapan",
    name = "Admin Rekapan ISE",
    displayNumber = "0882002741313",
    waDigits = "62882002741313",
    defaultMessage = "Halo Admin Rekapan ISE, saya ingin cek status rekapan pesanan di JIEGO.",
  )

  val ADMIN_PACKING_ER = JiegoWhatsAppContact(
    role = "Admin Packing",
    name = "Admin Packing ER",
    displayNumber = "08816623574",
    waDigits = "628816623574",
    defaultMessage = "Halo Admin Packing ER, saya ingin konfirmasi packing pesanan JIEGO.",
  )

  val ALL = listOf(OWNER_JIEJIE, ADMIN_ISE, ADMIN_PACKING_ER)

  fun openWhatsApp(context: Context, contact: JiegoWhatsAppContact, customMessage: String? = null) {
    val msg = customMessage ?: contact.defaultMessage
    val url = "https://wa.me/${contact.waDigits}?text=${Uri.encode(msg)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
      context.startActivity(intent)
    }.onFailure {
      Toast.makeText(
        context,
        "Tidak dapat membuka WhatsApp. Hubungi ${contact.name}: ${contact.displayNumber}",
        Toast.LENGTH_LONG,
      ).show()
    }
  }

  fun openShopee(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHOPEE_URL)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
      context.startActivity(intent)
    }.onFailure {
      Toast.makeText(
        context,
        "Tidak dapat membuka Shopee. Kunjungi toko resmi: $SHOPEE_URL",
        Toast.LENGTH_LONG,
      ).show()
    }
  }
}

@Composable
fun JiegoContactsDialog(
  customOrderContext: String? = null,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Kontak & Toko JIEGO", style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Kunjungi toko Shopee resmi JIEGO atau hubungi Owner, Admin Rekapan, dan Admin Packing ER langsung via WhatsApp.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Toko Shopee Resmi JIEGO",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = JiegoContacts.SHOPEE_URL,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Button(
              onClick = { JiegoContacts.openShopee(context) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("dialog_shopee_button"),
            ) {
              Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Buka Shopee JIEGO")
              Spacer(Modifier.width(6.dp))
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }

        JiegoContacts.ALL.forEachIndexed { index, contact ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(14.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "WhatsApp: ${contact.displayNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Button(
                onClick = {
                  val fullMsg = if (!customOrderContext.isNullOrBlank()) {
                    "${contact.defaultMessage}\n\n$customOrderContext"
                  } else {
                    contact.defaultMessage
                  }
                  JiegoContacts.openWhatsApp(context, contact, fullMsg)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("wa_contact_button_$index"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chat ${contact.name}")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}

@Composable
fun JiegoWhatsAppFooterCard(
  modifier: Modifier = Modifier,
  customMessageSuffix: String? = null,
) {
  val context = LocalContext.current

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.SupportAgent,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Kontak & Toko Resmi JIEGO",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Butuh konfirmasi QRIS, cek rekapan, info packing, atau checkout via Shopee? Hubungi tim kami atau kunjungi toko resmi:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Button(
        onClick = { JiegoContacts.openShopee(context) },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("footer_shopee_button"),
      ) {
        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Shopee JIEGO • id.shp.ee/dABi8Yvg",
          modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
      }

      HorizontalDivider()

      JiegoContacts.ALL.forEachIndexed { idx, contact ->
        OutlinedButton(
          onClick = {
            val msg = if (!customMessageSuffix.isNullOrBlank()) {
              "${contact.defaultMessage}\n$customMessageSuffix"
            } else {
              contact.defaultMessage
            }
            JiegoContacts.openWhatsApp(context, contact, msg)
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("footer_wa_button_$idx"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "${contact.name} • ${contact.displayNumber}",
            modifier = Modifier.weight(1f),
          )
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}
