package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.OrderItem
import com.example.data.model.PaymentStatus
import com.example.ui.components.DeadlinePill
import com.example.ui.components.JiegoContacts
import com.example.ui.components.JiegoWhatsAppFooterCard
import com.example.ui.components.MemberSearchMatcher
import com.example.ui.components.MemberStatusBadge
import com.example.ui.components.OrderTimelineStepper
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.formatIdr
import com.example.ui.viewmodel.KPopGoData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyOrdersScreen(
  data: KPopGoData,
  onOpenOrderDetail: (String) -> Unit,
  onRecordPayment: (orderName: String, member: MemberOrder, newPaidAmount: Long, method: String) -> Unit,
  onOpenQrisPayment: (orderName: String, member: MemberOrder) -> Unit,
  onOpenGeneralQris: () -> Unit,
  onNavigateToDashboard: () -> Unit,
  isBuyerMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  var paymentDialogTarget by remember { mutableStateOf<Pair<GroupOrder?, MemberOrder>?>(null) }
  var buyerSearchQuery by remember { mutableStateOf("") }
  val ordersById = remember(data.groupOrders) { data.groupOrders.associateBy { it.id } }
  val itemsByMemberId = remember(data.items) { data.items.groupBy { it.memberId } }

  if (paymentDialogTarget != null) {
    val (go, member) = paymentDialogTarget!!
    RecordPaymentDialog(
      member = member,
      onDismiss = { paymentDialogTarget = null },
      onConfirm = { newPaid, method ->
        onRecordPayment(go?.name ?: "Grup Order", member, newPaid, method)
        paymentDialogTarget = null
      },
    )
  }

  val normalizedQuery = buyerSearchQuery.trim().lowercase()
  val displayedOrders = remember(data.members, data.myOrders, itemsByMemberId, ordersById, normalizedQuery) {
    if (normalizedQuery.isEmpty()) {
      data.myOrders
    } else {
      data.members.filter { member ->
        MemberSearchMatcher.matchesMember(
          member = member,
          query = normalizedQuery,
          memberItems = itemsByMemberId[member.id].orEmpty(),
          parentOrder = ordersById[member.orderId],
        )
      }
    }
  }

  val quickSuggestions = remember(data.members) {
    val unpaidNames = data.members
      .filter { it.remainingBalance > 0L }
      .map { it.memberName.trim() }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase() }
    val paidNames = data.members
      .filter { it.remainingBalance == 0L }
      .map { it.memberName.trim() }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase() }
    (unpaidNames + paidNames).distinctBy { it.lowercase() }.take(8)
  }

  val totalCost = if (normalizedQuery.isEmpty()) data.myTotalCost else displayedOrders.sumOf { it.totalCost }
  val totalPaid = if (normalizedQuery.isEmpty()) data.myTotalPaid else displayedOrders.sumOf { it.paidAmount }
  val totalRemaining = if (normalizedQuery.isEmpty()) data.myTotalRemaining else displayedOrders.sumOf { it.remainingBalance }
  val progress = if (totalCost <= 0L) 0f else (totalPaid.toFloat() / totalCost.toFloat()).coerceIn(0f, 1f)

  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    // Buyer Bill Summary Header
    item(key = "buyer_bill_header") {
      Surface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .background(
              Brush.linearGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.secondary,
                  MaterialTheme.colorScheme.primary,
                )
              )
            )
            .padding(20.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (normalizedQuery.isEmpty()) {
                    "SISA TAGIHAN BELANJAAN SAYA"
                  } else {
                    "TAGIHAN PENCARIAN: ${buyerSearchQuery.trim().uppercase()}"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.8f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = formatIdr(totalRemaining),
                  style = MaterialTheme.typography.displaySmall,
                  color = Color.White,
                )
              }
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.AccountBalanceWallet,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            LinearProgressIndicator(
              progress = { progress },
              color = Color(0xFF34D399),
              trackColor = Color.White.copy(alpha = 0.22f),
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            )

            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                Text(
                  text = "Total Belanjaan",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                )
                Text(
                  text = formatIdr(totalCost),
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Sudah Dibayarkan",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                )
                Text(
                  text = formatIdr(totalPaid),
                  style = MaterialTheme.typography.titleMedium,
                  color = Color(0xFFA7F3D0),
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }

    // Quick QRIS Button
    item(key = "qris_banner_btn") {
      OutlinedButton(
        onClick = onOpenGeneralQris,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("my_orders_open_qris_button"),
      ) {
        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Lihat Foto QRIS Pembelian JIEGO")
      }
    }

    // Buyer Search Bar (Lookup by Name / Phone Number across all batches)
    item(key = "buyer_search_box") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = buyerSearchQuery,
          onValueChange = { buyerSearchQuery = it },
          label = { Text("Cari Nama atau No. HP Pembeli") },
          placeholder = { Text("Misal: elan, rindi, yunia, atau 0851... / 62851...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (buyerSearchQuery.isNotEmpty()) {
              IconButton(onClick = { buyerSearchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Bersihkan pencarian pembeli")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("my_orders_buyer_search"),
        )

        if (normalizedQuery.isNotEmpty()) {
          Text(
            text = "Ditemukan ${displayedOrders.size} pesanan cocok untuk \"${buyerSearchQuery.trim()}\"",
            style = MaterialTheme.typography.labelMedium,
            color = if (displayedOrders.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
          )
        }

        if (quickSuggestions.isNotEmpty()) {
          Text(
            text = if (normalizedQuery.isEmpty()) "Cek cepat pembeli dari rekapan:" else "Pilih cepat pembeli:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            quickSuggestions.forEach { suggestedName ->
              AssistChip(
                onClick = { buyerSearchQuery = suggestedName },
                label = { Text(suggestedName) },
              )
            }
          }
        }
      }
    }

    if (displayedOrders.isEmpty()) {
      item(key = "empty_my_orders") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp),
          ) {
            Icon(
              imageVector = if (normalizedQuery.isEmpty()) Icons.Default.ShoppingBag else Icons.Default.PersonSearch,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = if (normalizedQuery.isEmpty()) {
                "Belum Ada Pesanan Pribadi"
              } else {
                "Pembeli Tidak Ditemukan"
              },
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = if (normalizedQuery.isEmpty()) {
                if (data.members.isNotEmpty()) {
                  if (isBuyerMode) {
                    "Ketik nama atau nomor HP Anda di kolom pencarian di atas untuk melihat rincian belanjaan, status pengiriman, dan sisa tagihan dari rekapan JIEGO."
                  } else {
                    "Ketik nama atau nomor HP di kolom pencarian di atas untuk langsung mengecek belanjaan, status, dan sisa tagihan dari rekapan—atau tandai anggota dengan opsi \"Pesanan Saya\" di detail GO."
                  }
                } else {
                  if (isBuyerMode) {
                    "Belum ada daftar anggota yang tersinkron di database. Minta Pemilik (jiego4sho@gmail.com) menekan \"Sync Sheets\" di Dashboard terlebih dahulu, lalu cari nama atau nomor HP Anda di atas."
                  } else {
                    "Belum ada data anggota yang tersimpan. Tekan \"Sync Sheets\" di Dashboard untuk memuat seluruh pembeli dari Google Sheets, atau tambah anggota di detail GO."
                  }
                }
              } else if (data.members.isEmpty()) {
                "Belum ada data pembeli di database saat ini. Pemilik (jiego4sho@gmail.com) perlu menekan \"Sync Sheets\" di Dashboard agar daftar pembeli & nomor HP tersedia untuk dicari."
              } else {
                "Tidak ditemukan pembeli yang cocok dengan \"$buyerSearchQuery\". Pastikan ejaan nama atau coba 4 digit terakhir nomor HP."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (normalizedQuery.isNotEmpty()) {
              OutlinedButton(
                onClick = { buyerSearchQuery = "" },
                shape = RoundedCornerShape(14.dp),
              ) {
                Text("Reset Pencarian")
              }
            } else {
              Button(
                onClick = onNavigateToDashboard,
                shape = RoundedCornerShape(14.dp),
              ) {
                Text("Buka Dashboard Grup Order")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    } else {
      items(displayedOrders, key = { it.id }) { memberOrder ->
        val parentOrder = ordersById[memberOrder.orderId]
        val memberItems = data.itemsForMember(memberOrder.id)
        BuyerOrderCard(
          order = parentOrder,
          memberOrder = memberOrder,
          items = memberItems,
          isBuyerMode = isBuyerMode,
          onOpenGroupOrder = { onOpenOrderDetail(memberOrder.orderId) },
          onMarkPaidFull = {
            onRecordPayment(
              parentOrder?.name ?: "Grup Order",
              memberOrder,
              memberOrder.totalCost,
              memberOrder.lastPaymentMethod.ifBlank { "QRIS" },
            )
          },
          onOpenPaymentDialog = { paymentDialogTarget = parentOrder to memberOrder },
          onOpenQrisPayment = { onOpenQrisPayment(parentOrder?.name ?: "Grup Order", memberOrder) },
        )
      }
    }

    item(key = "my_orders_wa_footer") {
      JiegoWhatsAppFooterCard(
        customMessageSuffix = if (normalizedQuery.isNotEmpty()) {
          "Nama pembeli: ${buyerSearchQuery.trim()} (Sisa tagihan: ${formatIdr(totalRemaining)})"
        } else null,
      )
    }
  }
}

@Composable
private fun BuyerOrderCard(
  order: GroupOrder?,
  memberOrder: MemberOrder,
  items: List<OrderItem>,
  isBuyerMode: Boolean,
  onOpenGroupOrder: () -> Unit,
  onMarkPaidFull: () -> Unit,
  onOpenPaymentDialog: () -> Unit,
  onOpenQrisPayment: () -> Unit,
) {
  val context = LocalContext.current
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
      .testTag("my_order_card_${memberOrder.id}"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      // Header
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = order?.artistGroup?.uppercase() ?: "K-POP GO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = order?.name ?: "Grup Order",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onOpenGroupOrder),
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = if (memberOrder.memberHandle.isNotBlank()) {
              "Pembeli: ${memberOrder.memberName} (${memberOrder.memberHandle})"
            } else {
              "Pembeli: ${memberOrder.memberName}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.width(8.dp))
        PaymentStatusBadge(status = memberOrder.paymentStatus)
      }

      // Status Timeline Stepper
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Status Belanjaan:", style = MaterialTheme.typography.labelMedium)
        MemberStatusBadge(status = memberOrder.orderStatusEnum)
      }
      OrderTimelineStepper(
        currentStatus = memberOrder.orderStatusEnum,
        isPaidFull = memberOrder.paymentStatus == PaymentStatus.LUNAS,
      )

      if (order != null) {
        DeadlinePill(label = "Batas Pelunasan", targetMillis = order.paymentDeadlineMillis)
      }

      HorizontalDivider()

      // Belanjaan Apa Aja (Itemized Breakdown)
      Text(
        text = "Belanjaan Kamu (${items.size} jenis item)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (items.isEmpty()) {
        Text(
          text = "Belum ada item tercatat.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items.forEach { item ->
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(10.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.itemName,
                  style = MaterialTheme.typography.titleSmall,
                )
                if (item.variant.isNotBlank()) {
                  Text(
                    text = "Varian: ${item.variant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = "${item.quantity} × ${formatIdr(item.unitPrice)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = formatIdr(item.lineTotal),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      // Bill Summary
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(12.dp),
        ) {
          BillRow("Subtotal Item", formatIdr(memberOrder.totalItemsCost))
          BillRow("Ongkir / EMS / Admin", formatIdr(memberOrder.shippingFee))
          HorizontalDivider()
          BillRow("Total Biaya", formatIdr(memberOrder.totalCost), bold = true)
          BillRow(
            label = if (memberOrder.lastPaymentMethod.isNotBlank()) {
              "Sudah Dibayar (${memberOrder.lastPaymentMethod})"
            } else {
              "Sudah Dibayar"
            },
            value = formatIdr(memberOrder.paidAmount),
            valueColor = MaterialTheme.colorScheme.tertiary,
          )
          BillRow(
            label = "Sisa Tagihan",
            value = formatIdr(memberOrder.remainingBalance),
            bold = true,
            valueColor = if (memberOrder.remainingBalance == 0L) {
              MaterialTheme.colorScheme.tertiary
            } else {
              MaterialTheme.colorScheme.primary
            },
          )
        }
      }

      // Action Buttons
      if (isBuyerMode) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (memberOrder.remainingBalance > 0L) {
            Button(
              onClick = onOpenQrisPayment,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("buyer_qris_button_${memberOrder.id}"),
            ) {
              Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Bayar via QRIS")
            }
          }
          OutlinedButton(
            onClick = {
              val itemSummary = items.take(3).joinToString(", ") { "${it.itemName} (x${it.quantity})" }
              val msg = buildString {
                append("Halo Owner JIEJIE, saya ingin konfirmasi pesanan atas nama ${memberOrder.memberName}")
                append(" di ${order?.name ?: "Grup Order JIEGO"}.\n")
                if (itemSummary.isNotBlank()) append("Item: $itemSummary\n")
                append("Total: ${formatIdr(memberOrder.totalCost)} | Terbayar: ${formatIdr(memberOrder.paidAmount)} | Sisa: ${formatIdr(memberOrder.remainingBalance)}")
              }
              JiegoContacts.openWhatsApp(context, JiegoContacts.OWNER_JIEJIE, msg)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("buyer_wa_confirm_${memberOrder.id}"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Konfirmasi WA")
          }
        }
      } else {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (memberOrder.remainingBalance > 0L) {
            Button(
              onClick = onMarkPaidFull,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Tandai Lunas")
            }
          }
          OutlinedButton(
            onClick = onOpenPaymentDialog,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (memberOrder.remainingBalance > 0L) "Catat Bayar" else "Ubah Bayar")
          }
          if (memberOrder.remainingBalance > 0L) {
            OutlinedButton(
              onClick = onOpenQrisPayment,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .height(48.dp)
                .testTag("card_qris_button_${memberOrder.id}"),
            ) {
              Icon(Icons.Default.QrCode2, contentDescription = "Bayar via QRIS")
            }
          }
        }
      }

      TextButton(
        onClick = onOpenGroupOrder,
        modifier = Modifier.align(Alignment.End),
      ) {
        Text("Lihat Detail Grup Order")
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
fun BillRow(
  label: String,
  value: String,
  bold: Boolean = false,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
    Text(
      text = value,
      style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      color = valueColor,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
    )
  }
}
