package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ExchangeRates
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.MemberOrderStatus
import com.example.data.repository.DraftOrderItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupOrderFormDialog(
  existingOrder: GroupOrder? = null,
  onDismiss: () -> Unit,
  onConfirm: (
    name: String,
    artistGroup: String,
    batchCode: String,
    closingDateMillis: Long,
    paymentDeadlineMillis: Long,
    notes: String,
  ) -> Unit,
) {
  val now = remember { System.currentTimeMillis() }
  var name by remember { mutableStateOf(existingOrder?.name ?: "") }
  var artistGroup by remember { mutableStateOf(existingOrder?.artistGroup ?: "") }
  var batchCode by remember { mutableStateOf(existingOrder?.batchCode ?: "") }
  var closingDateMillis by remember {
    mutableStateOf(existingOrder?.closingDateMillis ?: (now + 7L * 86_400_000L))
  }
  var paymentDeadlineMillis by remember {
    mutableStateOf(existingOrder?.paymentDeadlineMillis ?: (now + 5L * 86_400_000L))
  }
  var notes by remember { mutableStateOf(existingOrder?.notes ?: "") }
  var pickingClosingDate by remember { mutableStateOf(false) }
  var pickingDeadlineDate by remember { mutableStateOf(false) }

  if (pickingClosingDate) {
    val state = rememberDatePickerState(initialSelectedDateMillis = closingDateMillis)
    DatePickerDialog(
      onDismissRequest = { pickingClosingDate = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let { closingDateMillis = it }
          pickingClosingDate = false
        }) { Text("Pilih") }
      },
      dismissButton = {
        TextButton(onClick = { pickingClosingDate = false }) { Text("Batal") }
      },
    ) {
      DatePicker(state = state)
    }
  }

  if (pickingDeadlineDate) {
    val state = rememberDatePickerState(initialSelectedDateMillis = paymentDeadlineMillis)
    DatePickerDialog(
      onDismissRequest = { pickingDeadlineDate = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let { paymentDeadlineMillis = it }
          pickingDeadlineDate = false
        }) { Text("Pilih") }
      },
      dismissButton = {
        TextButton(onClick = { pickingDeadlineDate = false }) { Text("Batal") }
      },
    ) {
      DatePicker(state = state)
    }
  }

  val isValid = name.trim().isNotEmpty() && artistGroup.trim().isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (existingOrder == null) "Buat Grup Order Baru" else "Edit Informasi Grup Order",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 460.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(120) },
          label = { Text("Nama Grup Order *") },
          placeholder = { Text("PO SEVENTEEN Spill The Feels") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("go_name_input"),
        )
        OutlinedTextField(
          value = artistGroup,
          onValueChange = { artistGroup = it.take(60) },
          label = { Text("Artis / Grup K-Pop *") },
          placeholder = { Text("SEVENTEEN, NewJeans, aespa...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("go_artist_input"),
        )
        OutlinedTextField(
          value = batchCode,
          onValueChange = { batchCode = it.take(40) },
          label = { Text("Kode Batch (Opsional)") },
          placeholder = { Text("BATCH-01") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
          onClick = { pickingClosingDate = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text("Tutup GO: ${formatDate(closingDateMillis)}")
        }
        OutlinedButton(
          onClick = { pickingDeadlineDate = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text("Tenggat Bayar: ${formatDate(paymentDeadlineMillis)}")
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(500) },
          label = { Text("Catatan / Info Tambahan") },
          placeholder = { Text("Info EMS, tax, atau ketentuan pelunasan") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(name, artistGroup, batchCode, closingDateMillis, paymentDeadlineMillis, notes)
        },
        enabled = isValid,
        modifier = Modifier.testTag("save_go_button"),
      ) {
        Text(if (existingOrder == null) "Simpan GO" else "Simpan Perubahan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

private data class MutableDraftItem(
  val itemName: String = "",
  val variant: String = "",
  val quantityText: String = "1",
  val unitPriceText: String = "",
)

@Composable
fun AddMemberWithItemsDialog(
  exchangeRates: ExchangeRates = ExchangeRates(),
  onDismiss: () -> Unit,
  onConfirm: (
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
    items: List<DraftOrderItem>,
  ) -> Unit,
) {
  var memberName by remember { mutableStateOf("") }
  var memberHandle by remember { mutableStateOf("") }
  var isCurrentUser by remember { mutableStateOf(false) }
  var shippingFeeText by remember { mutableStateOf("25000") }
  var paidAmountText by remember { mutableStateOf("0") }
  var paymentMethod by remember { mutableStateOf("QRIS") }
  var notes by remember { mutableStateOf("") }

  val draftItems = remember { mutableStateListOf(MutableDraftItem()) }

  val parsedItems = draftItems.mapNotNull { draft ->
    val cleanName = draft.itemName.trim()
    val qty = draft.quantityText.toLongOrNull() ?: 0L
    val price = draft.unitPriceText.toLongOrNull() ?: -1L
    if (cleanName.isNotEmpty() && qty >= 1L && price >= 0L) {
      DraftOrderItem(cleanName, draft.variant.trim(), qty, price)
    } else null
  }
  val isValid = memberName.trim().isNotEmpty() && parsedItems.size == draftItems.size && parsedItems.isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Tambah Anggota & Belanjaan", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = memberName,
          onValueChange = { memberName = it.take(80) },
          label = { Text("Nama Pembeli / Anggota *") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("member_name_input"),
        )
        OutlinedTextField(
          value = memberHandle,
          onValueChange = { memberHandle = it.take(60) },
          label = { Text("Username / Kontak (@handle)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Tandai sebagai Pesanan Saya", style = MaterialTheme.typography.titleSmall)
            Text(
              "Tampilkan di tab Belanjaan Saya",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = isCurrentUser, onCheckedChange = { isCurrentUser = it })
        }

        HorizontalDivider()
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Daftar Item Dibeli (${draftItems.size})", style = MaterialTheme.typography.titleSmall)
          TextButton(onClick = { draftItems.add(MutableDraftItem()) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Tambah Item")
          }
        }

        draftItems.forEachIndexed { idx, item ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Item #${idx + 1}", style = MaterialTheme.typography.labelMedium)
                if (draftItems.size > 1) {
                  IconButton(
                    onClick = { draftItems.removeAt(idx) },
                    modifier = Modifier.size(28.dp),
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus item", modifier = Modifier.size(18.dp))
                  }
                }
              }
              OutlinedTextField(
                value = item.itemName,
                onValueChange = { draftItems[idx] = item.copy(itemName = it.take(120)) },
                label = { Text("Nama Item *") },
                placeholder = { Text("Album Carat Ver.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = item.variant,
                onValueChange = { draftItems[idx] = item.copy(variant = it.take(80)) },
                label = { Text("Varian / Bias / Versi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = item.quantityText,
                  onValueChange = { v -> draftItems[idx] = item.copy(quantityText = v.filter { it.isDigit() }.take(3)) },
                  label = { Text("Qty *") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(0.35f),
                )
                OutlinedTextField(
                  value = item.unitPriceText,
                  onValueChange = { v -> draftItems[idx] = item.copy(unitPriceText = v.filter { it.isDigit() }.take(9)) },
                  label = { Text("Harga/Item (Rp) *") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(0.65f),
                )
              }
              InlineRateConverterHelper(
                rates = exchangeRates,
                onApplyIdrPrice = { idrPrice ->
                  draftItems[idx] = item.copy(unitPriceText = idrPrice.toString())
                },
              )
            }
          }
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = shippingFeeText,
            onValueChange = { v -> shippingFeeText = v.filter { it.isDigit() }.take(8) },
            label = { Text("Ongkir/EMS (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = paidAmountText,
            onValueChange = { v -> paidAmountText = v.filter { it.isDigit() }.take(9) },
            label = { Text("Sudah Bayar (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = paymentMethod,
          onValueChange = { paymentMethod = it.take(50) },
          label = { Text("Metode Pembayaran (Jika ada DP/Lunas)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(300) },
          label = { Text("Catatan Anggota") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            memberName,
            memberHandle,
            isCurrentUser,
            shippingFeeText.toLongOrNull() ?: 0L,
            paidAmountText.toLongOrNull() ?: 0L,
            paymentMethod,
            notes,
            parsedItems,
          )
        },
        enabled = isValid,
        modifier = Modifier.testTag("save_member_button"),
      ) {
        Text("Simpan Anggota")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@Composable
fun AddOrderItemDialog(
  memberName: String,
  exchangeRates: ExchangeRates = ExchangeRates(),
  onDismiss: () -> Unit,
  onConfirm: (itemName: String, variant: String, quantity: Long, unitPrice: Long) -> Unit,
) {
  var itemName by remember { mutableStateOf("") }
  var variant by remember { mutableStateOf("") }
  var quantityText by remember { mutableStateOf("1") }
  var unitPriceText by remember { mutableStateOf("") }

  val qty = quantityText.toLongOrNull() ?: 0L
  val price = unitPriceText.toLongOrNull() ?: -1L
  val isValid = itemName.trim().isNotEmpty() && qty >= 1L && price >= 0L

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tambah Item untuk $memberName", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 460.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = itemName,
          onValueChange = { itemName = it.take(120) },
          label = { Text("Nama Item *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = variant,
          onValueChange = { variant = it.take(80) },
          label = { Text("Varian / Member / Versi") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = quantityText,
            onValueChange = { v -> quantityText = v.filter { it.isDigit() }.take(3) },
            label = { Text("Qty *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.35f),
          )
          OutlinedTextField(
            value = unitPriceText,
            onValueChange = { v -> unitPriceText = v.filter { it.isDigit() }.take(9) },
            label = { Text("Harga/Item (Rp) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.65f),
          )
        }
        InlineRateConverterHelper(
          rates = exchangeRates,
          onApplyIdrPrice = { idrPrice ->
            unitPriceText = idrPrice.toString()
          },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(itemName, variant, qty, price) },
        enabled = isValid,
      ) { Text("Tambah") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordPaymentDialog(
  member: MemberOrder,
  onDismiss: () -> Unit,
  onConfirm: (newTotalPaidAmount: Long, paymentMethod: String) -> Unit,
) {
  val totalCost = member.totalCost
  var paidText by remember {
    mutableStateOf(if (member.remainingBalance > 0L) totalCost.toString() else member.paidAmount.toString())
  }
  var paymentMethod by remember {
    mutableStateOf(member.lastPaymentMethod.ifBlank { "QRIS" })
  }
  val methods = listOf("QRIS", "Transfer BCA", "Mandiri", "GoPay", "Dana", "ShopeePay")
  val parsedAmount = paidText.toLongOrNull() ?: -1L
  val previewRemaining = if (parsedAmount >= 0L) (totalCost - parsedAmount).coerceAtLeast(0L) else member.remainingBalance

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Catat Pembayaran", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp),
          ) {
            Text(member.memberName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Total Tagihan: ${formatIdr(totalCost)}", style = MaterialTheme.typography.bodySmall)
            Text("Sudah Dibayar Sebelumnya: ${formatIdr(member.paidAmount)}", style = MaterialTheme.typography.bodySmall)
            Text(
              "Sisa Setelah Update: ${formatIdr(previewRemaining)}",
              style = MaterialTheme.typography.titleSmall,
              color = if (previewRemaining == 0L) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
          }
        }

        Text("Pilihan Cepat Total Terbayar:", style = MaterialTheme.typography.labelMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          FilterChip(
            selected = parsedAmount == totalCost,
            onClick = { paidText = totalCost.toString() },
            label = { Text("Lunasi (${formatIdr(totalCost)})") },
          )
          FilterChip(
            selected = parsedAmount == totalCost / 2L,
            onClick = { paidText = (totalCost / 2L).toString() },
            label = { Text("DP 50%") },
          )
          FilterChip(
            selected = parsedAmount == 0L,
            onClick = { paidText = "0" },
            label = { Text("Reset Rp 0") },
          )
        }

        OutlinedTextField(
          value = paidText,
          onValueChange = { v -> paidText = v.filter { it.isDigit() }.take(10) },
          label = { Text("Total Sudah Dibayar (Rp)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_amount_input"),
        )

        Text("Metode Pembayaran:", style = MaterialTheme.typography.labelMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          methods.forEach { method ->
            FilterChip(
              selected = paymentMethod.equals(method, ignoreCase = true),
              onClick = { paymentMethod = method },
              label = { Text(method) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(parsedAmount.coerceAtLeast(0L), paymentMethod) },
        enabled = parsedAmount >= 0L,
        modifier = Modifier.testTag("confirm_payment_button"),
      ) {
        Text("Simpan Pembayaran")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMemberAndStatusDialog(
  member: MemberOrder,
  onDismiss: () -> Unit,
  onConfirm: (
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    newStatus: MemberOrderStatus,
    notes: String,
  ) -> Unit,
) {
  var memberName by remember { mutableStateOf(member.memberName) }
  var memberHandle by remember { mutableStateOf(member.memberHandle) }
  var isCurrentUser by remember { mutableStateOf(member.isCurrentUser) }
  var shippingFeeText by remember { mutableStateOf(member.shippingFee.toString()) }
  var selectedStatus by remember { mutableStateOf(member.orderStatusEnum) }
  var notes by remember { mutableStateOf(member.notes) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Anggota & Status Pesanan", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 460.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = memberName,
          onValueChange = { memberName = it.take(80) },
          label = { Text("Nama Pembeli *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = memberHandle,
          onValueChange = { memberHandle = it.take(60) },
          label = { Text("Username (@handle)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = shippingFeeText,
          onValueChange = { v -> shippingFeeText = v.filter { it.isDigit() }.take(8) },
          label = { Text("Ongkir / EMS / Admin (Rp)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Ini Pesanan Saya", style = MaterialTheme.typography.titleSmall)
          Switch(checked = isCurrentUser, onCheckedChange = { isCurrentUser = it })
        }
        Text("Status Pesanan:", style = MaterialTheme.typography.labelMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MemberOrderStatus.entries.forEach { st ->
            FilterChip(
              selected = selectedStatus == st,
              onClick = { selectedStatus = st },
              label = { Text(st.labelId) },
            )
          }
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(300) },
          label = { Text("Catatan / Resi") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            memberName,
            memberHandle,
            isCurrentUser,
            shippingFeeText.toLongOrNull() ?: 0L,
            selectedStatus,
            notes,
          )
        },
        enabled = memberName.trim().isNotEmpty(),
      ) { Text("Simpan") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}
