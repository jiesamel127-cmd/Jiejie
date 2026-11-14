package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GoStatus
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.MemberOrderStatus
import com.example.data.model.OrderItem
import com.example.data.model.PaymentStatus
import com.example.data.repository.DraftOrderItem
import com.example.ui.components.AddMemberWithItemsDialog
import com.example.ui.components.AddOrderItemDialog
import com.example.ui.components.DeadlinePill
import com.example.ui.components.EditMemberAndStatusDialog
import com.example.ui.components.GoStatusBadge
import com.example.ui.components.GroupOrderFormDialog
import com.example.ui.components.JiegoContacts
import com.example.ui.components.MemberSearchMatcher
import com.example.ui.components.MemberStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.formatIdr
import com.example.ui.viewmodel.KPopGoData

private enum class MemberBillFilter(val label: String) {
  ALL("Semua"),
  UNPAID("Belum Lunas"),
  PAID("Lunas"),
  MINE("Pesanan Saya"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupOrderDetailScreen(
  orderId: String,
  data: KPopGoData,
  onBack: () -> Unit,
  onEditOrderInfo: (GroupOrder, String, String, String, Long, Long, String) -> Unit,
  onUpdateOrderStatus: (GroupOrder, GoStatus) -> Unit,
  onDeleteOrder: (String) -> Unit,
  onAddMemberWithItems: (GroupOrder, String, String, Boolean, Long, Long, String, String, List<DraftOrderItem>) -> Unit,
  onAddOrderItem: (String, String, String, String, Long, Long) -> Unit,
  onDeleteOrderItem: (String, String, String) -> Unit,
  onRecordPayment: (String, MemberOrder, Long, String) -> Unit,
  onUpdateMemberDetailsAndStatus: (String, MemberOrder, String, String, Boolean, Long, MemberOrderStatus, String) -> Unit,
  onSendPaymentReminder: (GroupOrder, MemberOrder) -> Unit,
  isBuyerMode: Boolean = false,
  onOpenQrisForMember: (GroupOrder, MemberOrder) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val order = remember(data.groupOrders, orderId) {
    data.groupOrders.find { it.id == orderId }
  }
  val orderMembers = remember(data.members, orderId) {
    data.membersForOrder(orderId)
  }
  val itemsByMemberId = remember(data.items) {
    data.items.groupBy { it.memberId }
  }

  var memberFilter by remember { mutableStateOf(MemberBillFilter.ALL) }
  var memberSearchQuery by remember { mutableStateOf("") }
  var showEditOrderDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showCompleteConfirm by remember { mutableStateOf(false) }
  var showStatusMenu by remember { mutableStateOf(false) }
  var showAddMemberDialog by remember { mutableStateOf(false) }
  var addItemTargetMember by remember { mutableStateOf<MemberOrder?>(null) }
  var paymentTargetMember by remember { mutableStateOf<MemberOrder?>(null) }
  var editTargetMember by remember { mutableStateOf<MemberOrder?>(null) }

  if (order == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Detail Grup Order") },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
            }
          },
        )
      }
    ) { padding ->
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
      ) {
        Text("Grup order tidak ditemukan atau sudah dihapus.")
      }
    }
    return
  }

  val isCompleted = order.statusEnum == GoStatus.COMPLETED

  if (showEditOrderDialog) {
    GroupOrderFormDialog(
      existingOrder = order,
      onDismiss = { showEditOrderDialog = false },
      onConfirm = { name, artist, batch, closing, deadline, notes ->
        onEditOrderInfo(order, name, artist, batch, closing, deadline, notes)
        showEditOrderDialog = false
      },
    )
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Hapus Grup Order?") },
      text = { Text("Semua daftar anggota dan item di \"${order.name}\" akan ikut terhapus permanen.") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDeleteOrder(order.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Hapus") }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
      },
    )
  }

  if (showCompleteConfirm) {
    AlertDialog(
      onDismissRequest = { showCompleteConfirm = false },
      title = { Text("Selesaikan Grup Order?") },
      text = { Text("Setelah berstatus Selesai, detail utama grup order ini dikunci sebagai arsip riwayat.") },
      confirmButton = {
        Button(onClick = {
          showCompleteConfirm = false
          onUpdateOrderStatus(order, GoStatus.COMPLETED)
        }) { Text("Tandai Selesai") }
      },
      dismissButton = {
        TextButton(onClick = { showCompleteConfirm = false }) { Text("Batal") }
      },
    )
  }

  if (showAddMemberDialog) {
    AddMemberWithItemsDialog(
      exchangeRates = data.exchangeRates,
      onDismiss = { showAddMemberDialog = false },
      onConfirm = { mName, mHandle, isMe, ship, paid, method, notes, items ->
        onAddMemberWithItems(order, mName, mHandle, isMe, ship, paid, method, notes, items)
        showAddMemberDialog = false
      },
    )
  }

  addItemTargetMember?.let { target ->
    AddOrderItemDialog(
      memberName = target.memberName,
      exchangeRates = data.exchangeRates,
      onDismiss = { addItemTargetMember = null },
      onConfirm = { itemName, variant, qty, price ->
        onAddOrderItem(order.id, target.id, itemName, variant, qty, price)
        addItemTargetMember = null
      },
    )
  }

  paymentTargetMember?.let { target ->
    RecordPaymentDialog(
      member = target,
      onDismiss = { paymentTargetMember = null },
      onConfirm = { newPaid, method ->
        onRecordPayment(order.name, target, newPaid, method)
        paymentTargetMember = null
      },
    )
  }

  editTargetMember?.let { target ->
    EditMemberAndStatusDialog(
      member = target,
      onDismiss = { editTargetMember = null },
      onConfirm = { mName, mHandle, isMe, ship, newStatus, notes ->
        onUpdateMemberDetailsAndStatus(order.name, target, mName, mHandle, isMe, ship, newStatus, notes)
        editTargetMember = null
      },
    )
  }

  val cleanMemberQuery = memberSearchQuery.trim()
  val filteredMembers = remember(orderMembers, memberFilter, cleanMemberQuery, itemsByMemberId, order) {
    orderMembers.filter { m ->
      val matchesTab = when (memberFilter) {
        MemberBillFilter.ALL -> true
        MemberBillFilter.UNPAID -> m.remainingBalance > 0L
        MemberBillFilter.PAID -> m.paymentStatus == PaymentStatus.LUNAS
        MemberBillFilter.MINE -> m.isCurrentUser
      }
      val matchesSearch = cleanMemberQuery.isEmpty() ||
        MemberSearchMatcher.matchesMember(
          member = m,
          query = cleanMemberQuery,
          memberItems = itemsByMemberId[m.id].orEmpty(),
          parentOrder = order,
        )
      matchesTab && matchesSearch
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = order.artistGroup,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
          }
        },
        actions = {
          if (!isBuyerMode) {
            if (!isCompleted) {
              IconButton(onClick = { showEditOrderDialog = true }, modifier = Modifier.testTag("edit_go_button")) {
                Icon(Icons.Default.Edit, contentDescription = "Edit info grup order")
              }
            }
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.testTag("delete_go_button")) {
              Icon(Icons.Default.Delete, contentDescription = "Hapus grup order")
            }
          }
        },
      )
    },
    modifier = modifier,
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = innerPadding.calculateTopPadding() + 8.dp,
        bottom = innerPadding.calculateBottomPadding() + 32.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      // Order Overview Header Card
      item(key = "order_overview") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = order.name,
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (order.batchCode.isNotBlank()) {
                  Text(
                    text = "Batch: ${order.batchCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Spacer(Modifier.width(8.dp))
              Box {
                GoStatusBadge(
                  status = order.statusEnum,
                  modifier = if (!isCompleted && !isBuyerMode) {
                    Modifier.clickable { showStatusMenu = true }
                  } else Modifier,
                )
                DropdownMenu(
                  expanded = showStatusMenu,
                  onDismissRequest = { showStatusMenu = false },
                ) {
                  GoStatus.entries.forEach { st ->
                    DropdownMenuItem(
                      text = { Text(st.labelId) },
                      onClick = {
                        showStatusMenu = false
                        if (st == GoStatus.COMPLETED) {
                          showCompleteConfirm = true
                        } else if (st != order.statusEnum) {
                          onUpdateOrderStatus(order, st)
                        }
                      },
                    )
                  }
                }
              }
            }

            if (order.notes.isNotBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = order.notes,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              DeadlinePill(label = "Tutup GO", targetMillis = order.closingDateMillis)
              DeadlinePill(label = "Batas Bayar", targetMillis = order.paymentDeadlineMillis)
            }

            HorizontalDivider()

            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                Text("Total Pesanan (${order.memberCount} org)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatIdr(order.totalOrderValue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Terbayar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  formatIdr(order.totalPaidAmount),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.tertiary,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("Sisa Tagihan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  formatIdr(order.remainingOrderBalance),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            LinearProgressIndicator(
              progress = { order.paidProgress },
              color = MaterialTheme.colorScheme.tertiary,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            )

            // Quick Actions Row (Owner only)
            if (!isCompleted && !isBuyerMode) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Button(
                  onClick = { showAddMemberDialog = true },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("add_member_button"),
                ) {
                  Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Tambah Anggota")
                }
                OutlinedButton(
                  onClick = { showStatusMenu = true },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.height(48.dp),
                ) {
                  Text("Ubah Status")
                }
              }
            }
          }
        }
      }

      // Interactive Bill Breakdown Header & Filters
      item(key = "bill_section_header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Rincian Tagihan Interaktif (${orderMembers.size} Anggota)",
            style = MaterialTheme.typography.titleLarge,
          )
          if (orderMembers.isNotEmpty()) {
            OutlinedTextField(
              value = memberSearchQuery,
              onValueChange = { memberSearchQuery = it },
              placeholder = { Text("Cari nama pembeli, no. HP, atau item di GO ini...") },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = {
                if (memberSearchQuery.isNotEmpty()) {
                  IconButton(onClick = { memberSearchQuery = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Bersihkan pencarian anggota")
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("detail_member_search_input"),
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            MemberBillFilter.entries.forEach { f ->
              FilterChip(
                selected = memberFilter == f,
                onClick = { memberFilter = f },
                label = { Text(f.label) },
              )
            }
          }
        }
      }

      if (filteredMembers.isEmpty()) {
        item(key = "empty_members") {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Text(
                text = if (orderMembers.isEmpty()) "Belum Ada Anggota di Grup Order Ini" else "Tidak ada anggota sesuai filter",
                style = MaterialTheme.typography.titleMedium,
              )
              if (orderMembers.isEmpty() && !isCompleted && !isBuyerMode) {
                Text(
                  text = "Tambahkan anggota beserta item yang dibeli untuk memantau rincian tagihan dan pelunasan.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { showAddMemberDialog = true }) {
                  Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Tambah Anggota Pertama")
                }
              }
            }
          }
        }
      } else {
        items(filteredMembers, key = { it.id }) { member ->
          val memberItems = data.itemsForMember(member.id)
          MemberBillBreakdownCard(
            orderName = order.name,
            member = member,
            items = memberItems,
            isOrderLocked = isCompleted,
            isBuyerMode = isBuyerMode,
            onMarkPaidFull = {
              onRecordPayment(
                order.name,
                member,
                member.totalCost,
                member.lastPaymentMethod.ifBlank { "QRIS" },
              )
            },
            onOpenPaymentDialog = { paymentTargetMember = member },
            onAddItemClick = { addItemTargetMember = member },
            onDeleteItem = { itemId -> onDeleteOrderItem(order.id, member.id, itemId) },
            onEditMemberClick = { editTargetMember = member },
            onSendReminder = { onSendPaymentReminder(order, member) },
            onOpenQrisPayment = { onOpenQrisForMember(order, member) },
          )
        }
      }
    }
  }
}

@Composable
private fun MemberBillBreakdownCard(
  orderName: String,
  member: MemberOrder,
  items: List<OrderItem>,
  isOrderLocked: Boolean,
  isBuyerMode: Boolean,
  onMarkPaidFull: () -> Unit,
  onOpenPaymentDialog: () -> Unit,
  onAddItemClick: () -> Unit,
  onDeleteItem: (String) -> Unit,
  onEditMemberClick: () -> Unit,
  onSendReminder: () -> Unit,
  onOpenQrisPayment: () -> Unit,
) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(true) }
  val canEditMember = !isOrderLocked && !isBuyerMode

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
      .testTag("member_bill_card_${member.id}"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      // Member Header Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { expanded = !expanded },
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = member.memberName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            if (member.isCurrentUser) {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = "Saya",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
          if (member.memberHandle.isNotBlank()) {
            Text(
              text = member.memberHandle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PaymentStatusBadge(status = member.paymentStatus)
          Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Tutup rincian" else "Buka rincian",
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        MemberStatusBadge(status = member.orderStatusEnum)
        Text(
          text = "Sisa Tagihan: ${formatIdr(member.remainingBalance)}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = if (member.remainingBalance == 0L) {
            MaterialTheme.colorScheme.tertiary
          } else {
            MaterialTheme.colorScheme.primary
          },
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HorizontalDivider()

          // Item List Header
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Daftar Item Dibeli (${items.size})",
              style = MaterialTheme.typography.labelLarge,
            )
            if (canEditMember) {
              TextButton(onClick = onAddItemClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah Item")
              }
            }
          }

          if (items.isEmpty()) {
            Text(
              text = "Belum ada item untuk anggota ini.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              items.forEach { item ->
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(item.itemName, style = MaterialTheme.typography.titleSmall)
                    val meta = buildString {
                      if (item.variant.isNotBlank()) append("${item.variant} • ")
                      append("${item.quantity} × ${formatIdr(item.unitPrice)}")
                    }
                    Text(
                      text = meta,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = formatIdr(item.lineTotal),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                  if (canEditMember) {
                    IconButton(
                      onClick = { onDeleteItem(item.id) },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus ${item.itemName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          // Financial Breakdown Box
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(5.dp),
              modifier = Modifier.padding(12.dp),
            ) {
              BillRow("Subtotal Item", formatIdr(member.totalItemsCost))
              BillRow("Ongkir / EMS / Admin", formatIdr(member.shippingFee))
              HorizontalDivider()
              BillRow("Total Biaya", formatIdr(member.totalCost), bold = true)
              BillRow(
                label = if (member.lastPaymentMethod.isNotBlank()) {
                  "Sudah Dibayar (${member.lastPaymentMethod})"
                } else {
                  "Sudah Dibayar"
                },
                value = formatIdr(member.paidAmount),
                valueColor = MaterialTheme.colorScheme.tertiary,
              )
              BillRow(
                label = "Sisa Tagihan Harus Dilunasi",
                value = formatIdr(member.remainingBalance),
                bold = true,
                valueColor = if (member.remainingBalance == 0L) {
                  MaterialTheme.colorScheme.tertiary
                } else {
                  MaterialTheme.colorScheme.primary
                },
              )
            }
          }

          if (member.notes.isNotBlank()) {
            Text(
              text = "Catatan: ${member.notes}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Interactive Payment & Status Actions
          if (isBuyerMode) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (member.remainingBalance > 0L) {
                Button(
                  onClick = onOpenQrisPayment,
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("buyer_detail_qris_${member.id}"),
                ) {
                  Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Bayar via QRIS")
                }
              }
              OutlinedButton(
                onClick = {
                  val msg = buildString {
                    append("Halo Owner JIEJIE, saya ingin konfirmasi pesanan atas nama ${member.memberName}")
                    append(" di $orderName.\n")
                    append("Total: ${formatIdr(member.totalCost)} | Sudah bayar: ${formatIdr(member.paidAmount)} | Sisa: ${formatIdr(member.remainingBalance)}")
                  }
                  JiegoContacts.openWhatsApp(context, JiegoContacts.OWNER_JIEJIE, msg)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("buyer_detail_wa_${member.id}"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Konfirmasi WA")
              }
            }
          } else {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (member.remainingBalance > 0L) {
                Button(
                  onClick = onMarkPaidFull,
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("mark_paid_full_${member.id}"),
                ) {
                  Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Tandai Lunas")
                }
              }
              OutlinedButton(
                onClick = onOpenPaymentDialog,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("record_payment_${member.id}"),
              ) {
                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (member.remainingBalance > 0L) "Catat Bayar" else "Ubah Bayar")
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (member.remainingBalance > 0L) {
                TextButton(
                  onClick = onSendReminder,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Kirim Pengingat")
                }
              }
              TextButton(
                onClick = onEditMemberClick,
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit Status & Info")
              }
            }
          }
        }
      }
    }
  }
}
