package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GoStatus
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.notifications.LocalNotificationHelper
import com.example.ui.components.DeadlinePill
import com.example.ui.components.GoStatusBadge
import com.example.ui.components.JiegoContacts
import com.example.ui.components.JiegoRatesSummaryCard
import com.example.ui.components.MemberSearchMatcher
import com.example.ui.components.MemberStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.formatIdr
import com.example.ui.viewmodel.KPopGoData

private enum class DashboardFilter(val label: String) {
  ALL("Semua"),
  ACTIVE("Aktif"),
  WAITING_PAYMENT("Menunggu Bayar"),
  SHIPPED("Dikirim"),
  COMPLETED("Selesai"),
}

@Composable
fun DashboardScreen(
  data: KPopGoData,
  onOrderClick: (String) -> Unit,
  onOpenCreateOrder: () -> Unit,
  onOpenImportRekapan: () -> Unit,
  onSeedSampleData: () -> Unit,
  onOpenNotifications: () -> Unit,
  isBuyerMode: Boolean = false,
  onOpenCatalog: () -> Unit = {},
  onOpenMyOrders: () -> Unit = {},
  onOpenRatesCalculator: () -> Unit = {},
  onSyncGoogleSheetsNow: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(DashboardFilter.ACTIVE) }
  var hasNotifPermission by remember {
    mutableStateOf(LocalNotificationHelper.hasNotificationPermission(context))
  }
  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasNotifPermission = granted
  }

  val ordersById = remember(data.groupOrders) { data.groupOrders.associateBy { it.id } }
  val itemsByMemberId = remember(data.items) { data.items.groupBy { it.memberId } }
  val normalizedQuery = searchQuery.trim()

  val matchingMembers = remember(data.members, itemsByMemberId, ordersById, normalizedQuery) {
    if (normalizedQuery.isEmpty()) {
      emptyList()
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

  val matchingOrderIdsFromMembers = remember(matchingMembers) {
    matchingMembers.map { it.orderId }.toSet()
  }

  val filteredOrders = remember(data.groupOrders, normalizedQuery, selectedFilter, matchingOrderIdsFromMembers) {
    val q = normalizedQuery.lowercase()
    data.groupOrders.filter { order ->
      val matchesFilter = if (q.isNotEmpty()) {
        // When searching by buyer name/phone/item, search across all GO statuses so no member is hidden
        true
      } else {
        when (selectedFilter) {
          DashboardFilter.ALL -> true
          DashboardFilter.ACTIVE -> order.statusEnum != GoStatus.COMPLETED
          DashboardFilter.WAITING_PAYMENT -> order.statusEnum == GoStatus.WAITING_PAYMENT || order.remainingOrderBalance > 0L
          DashboardFilter.SHIPPED -> order.statusEnum == GoStatus.SHIPPED
          DashboardFilter.COMPLETED -> order.statusEnum == GoStatus.COMPLETED
        }
      }
      val matchesSearch = q.isEmpty() ||
        order.name.lowercase().contains(q) ||
        order.artistGroup.lowercase().contains(q) ||
        order.batchCode.lowercase().contains(q) ||
        order.id in matchingOrderIdsFromMembers
      matchesFilter && matchesSearch
    }
  }

  val emptyImportedOrdersCount = remember(data.groupOrders, data.members) {
    val orderIdsWithMembers = data.members.mapTo(mutableSetOf()) { it.orderId }
    data.groupOrders.count { go ->
      (go.id.startsWith("imp_go_") || go.notes.startsWith("Diimpor dari rekapan")) &&
        go.id !in orderIdsWithMembers
    }
  }

  val deadlineAlerts = remember(data) { data.computeDeadlineAlerts() }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 680.dp

    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      // Hero Dashboard Financial Summary Card
      item(key = "hero_summary") {
        DashboardSummaryHeroCard(
          data = data,
          isBuyerMode = isBuyerMode,
          onOpenCatalog = onOpenCatalog,
          onOpenMyOrders = onOpenMyOrders,
        )
      }

      // Exchange Rates & Gross Price Calculator Summary Card
      item(key = "rates_summary_card") {
        JiegoRatesSummaryCard(
          rates = data.exchangeRates,
          onOpenCalculator = onOpenRatesCalculator,
        )
      }

      // Official Shopee JIEGO Store Quick Link
      item(key = "shopee_quick_card") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { JiegoContacts.openShopee(context) }
            .testTag("dashboard_shopee_button"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                  imageVector = Icons.Default.Storefront,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Toko Resmi Shopee JIEGO",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = JiegoContacts.SHOPEE_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = "Buka toko Shopee JIEGO",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item(key = "notif_permission_dashboard") {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("notif_permission_dashboard_card"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(14.dp),
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Aktifkan Notifikasi Bar HP",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = "Agar update foto, pengumuman, & status order tetap muncul di bar HP walau aplikasi sedang ditutup.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f),
                )
              }
              Spacer(Modifier.width(8.dp))
              Button(
                onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dashboard_allow_notif_button"),
              ) {
                Text("Izinkan")
              }
            }
          }
        }
      }

      // Urgent Deadline Alert Banner if any GO has unpaid members near deadline
      if (deadlineAlerts.isNotEmpty()) {
        item(key = "deadline_banner") {
          val topAlert = deadlineAlerts.first()
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenNotifications() }
              .testTag("deadline_alert_banner"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(14.dp),
            ) {
              Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (topAlert.isOverdue) {
                    "Tenggat Pembayaran Terlewat (${deadlineAlerts.size} GO)"
                  } else {
                    "Pengingat Tenggat Pembayaran (${deadlineAlerts.size} GO)"
                  },
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "${topAlert.order.name}: ${topAlert.unpaidMembersCount} anggota belum lunas (${formatIdr(topAlert.totalRemainingAmount)})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Lihat notifikasi",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      // Search & Filter Controls
      if (data.groupOrders.isNotEmpty() || data.members.isNotEmpty()) {
        item(key = "search_and_filters") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Cari nama pembeli, no. HP, atau grup order...") },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Bersihkan pencarian")
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_search_input"),
            )

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(DashboardFilter.entries) { filter ->
                FilterChip(
                  selected = selectedFilter == filter,
                  onClick = { selectedFilter = filter },
                  label = { Text(filter.label) },
                )
              }
            }

            if (!isBuyerMode) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                OutlinedButton(
                  onClick = onOpenImportRekapan,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("dashboard_import_rekapan_button"),
                ) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Impor Sheet", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                  onClick = onSyncGoogleSheetsNow,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("dashboard_sync_sheets_button"),
                ) {
                  Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Sync Sheets", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
          }
        }
      }

      if (!isBuyerMode && emptyImportedOrdersCount > 0) {
        item(key = "incomplete_sync_warning") {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSyncGoogleSheetsNow() }
              .testTag("incomplete_sync_banner"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(14.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "$emptyImportedOrdersCount batch impor belum memuat daftar anggota",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = "Ketuk di sini atau tombol Sync Sheets untuk memuat seluruh anggota & itemnya.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        }
      }

      if (normalizedQuery.isNotEmpty() && matchingMembers.isNotEmpty()) {
        item(key = "matched_buyers_header") {
          Text(
            text = "Pembeli Ditemukan (${matchingMembers.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        items(matchingMembers.take(25), key = { "search_mem_${it.id}" }) { member ->
          val parentOrder = ordersById[member.orderId]
          val memberItems = itemsByMemberId[member.id].orEmpty()
          DashboardMatchedBuyerCard(
            member = member,
            parentOrder = parentOrder,
            itemCount = memberItems.size,
            onClick = { onOrderClick(member.orderId) },
          )
        }
        if (matchingMembers.size > 25) {
          item(key = "matched_buyers_more") {
            Text(
              text = "+${matchingMembers.size - 25} hasil pembeli lainnya — buka salah satu GO atau tab Pesanan untuk daftar lengkap.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        item(key = "matched_orders_divider_header") {
          Text(
            text = "Grup Order Terkait (${filteredOrders.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // Empty State vs Group Order List
      if (data.groupOrders.isEmpty()) {
        item(key = "empty_state") {
          EmptyGroupOrdersCard(
            isBuyerMode = isBuyerMode,
            onOpenCreateOrder = onOpenCreateOrder,
            onOpenImportRekapan = onOpenImportRekapan,
            onSeedSampleData = onSeedSampleData,
            onOpenCatalog = onOpenCatalog,
          )
        }
      } else if (filteredOrders.isEmpty()) {
        item(key = "empty_filtered") {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(28.dp),
            ) {
              Text(
                text = if (normalizedQuery.isNotEmpty()) {
                  "Tidak ada pembeli atau grup order yang cocok"
                } else {
                  "Tidak ada grup order yang cocok"
                },
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = if (data.members.isEmpty()) {
                  "Data anggota di database masih kosong. Pemilik perlu menekan \"Sync Sheets\" terlebih dahulu."
                } else {
                  "Coba ubah filter status atau kata kunci nama pembeli / no. HP."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(12.dp))
              OutlinedButton(onClick = {
                searchQuery = ""
                selectedFilter = DashboardFilter.ALL
              }) {
                Text("Tampilkan Semua GO")
              }
            }
          }
        }
      } else if (isWide) {
        val rows = filteredOrders.chunked(2)
        items(rows, key = { row -> row.joinToString("_") { it.id } }) { pair ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            pair.forEach { order ->
              GroupOrderSummaryCard(
                order = order,
                onClick = { onOrderClick(order.id) },
                modifier = Modifier.weight(1f),
              )
            }
            if (pair.size == 1) {
              Spacer(Modifier.weight(1f))
            }
          }
        }
      } else {
        items(filteredOrders, key = { it.id }) { order ->
          GroupOrderSummaryCard(
            order = order,
            onClick = { onOrderClick(order.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun DashboardMatchedBuyerCard(
  member: MemberOrder,
  parentOrder: GroupOrder?,
  itemCount: Int,
  onClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("dashboard_matched_buyer_${member.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = member.memberName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          PaymentStatusBadge(status = member.paymentStatus)
        }
        if (member.memberHandle.isNotBlank()) {
          Text(
            text = member.memberHandle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = "${parentOrder?.name ?: "Grup Order"} • $itemCount item",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MemberStatusBadge(status = member.orderStatusEnum)
          Text(
            text = if (member.remainingBalance == 0L) "Lunas" else "Sisa ${formatIdr(member.remainingBalance)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (member.remainingBalance == 0L) {
              MaterialTheme.colorScheme.tertiary
            } else {
              MaterialTheme.colorScheme.primary
            },
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = "Buka grup order ${member.memberName}",
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun DashboardSummaryHeroCard(
  data: KPopGoData,
  isBuyerMode: Boolean,
  onOpenCatalog: () -> Unit,
  onOpenMyOrders: () -> Unit,
) {
  val totalVal = if (isBuyerMode) data.myTotalCost else data.totalActiveOrderValue
  val totalPaid = if (isBuyerMode) data.myTotalPaid else data.totalActivePaidAmount
  val remaining = if (isBuyerMode) data.myTotalRemaining else data.totalActiveRemainingAmount
  val progress = if (totalVal <= 0L) 0f else (totalPaid.toFloat() / totalVal.toFloat()).coerceIn(0f, 1f)

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
              Color(0xFF3B1D5A),
              MaterialTheme.colorScheme.primary,
            )
          )
        )
        .padding(20.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_jiego_mascot),
              contentDescription = stringResource(id = R.string.cd_jiego_logo),
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = if (isBuyerMode) "PORTAL PEMBELI JIEGO" else "RINGKASAN GRUP ORDER JIEGO",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = "${data.activeGroupOrders.size} Grup Order Berjalan",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
              )
            }
          }
          Surface(
            color = Color.White.copy(alpha = 0.16f),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = "${(progress * 100).toInt()}% Terbayar",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          HeroMetricBlock(
            label = if (isBuyerMode) "Belanjaan Saya" else "Total Nilai Pesanan",
            value = formatIdr(totalVal),
            modifier = Modifier.weight(1f),
          )
          HeroMetricBlock(
            label = "Sudah Terbayar",
            value = formatIdr(totalPaid),
            modifier = Modifier.weight(1f),
          )
          HeroMetricBlock(
            label = if (isBuyerMode) "Sisa Tagihan Saya" else "Sisa Tagihan",
            value = formatIdr(remaining),
            modifier = Modifier.weight(1f),
          )
        }

        if (isBuyerMode) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Surface(
              color = Color.White.copy(alpha = 0.18f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenMyOrders)
                .testTag("buyer_hero_my_orders_shortcut"),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .height(48.dp)
                  .padding(horizontal = 12.dp),
              ) {
                Text(
                  text = "Cek Tagihan Saya",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Surface(
              color = Color.White.copy(alpha = 0.18f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenCatalog)
                .testTag("buyer_hero_catalog_shortcut"),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .height(48.dp)
                  .padding(horizontal = 12.dp),
              ) {
                Text(
                  text = "Lihat Katalog",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HeroMetricBlock(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color.White.copy(alpha = 0.12f))
      .padding(10.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.8f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(3.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      color = Color.White,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupOrderSummaryCard(
  order: GroupOrder,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .testTag("go_card_${order.id}"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = order.artistGroup.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              )
            }
            if (order.batchCode.isNotBlank()) {
              Text(
                text = "• ${order.batchCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = order.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        GoStatusBadge(status = order.statusEnum)
      }

      // Financials & Member Count
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        SummaryStatChip(
          label = "Anggota",
          value = "${order.memberCount} orang",
          icon = Icons.Default.Group,
          modifier = Modifier.weight(0.85f),
        )
        SummaryStatChip(
          label = "Total Pesanan",
          value = formatIdr(order.totalOrderValue),
          icon = null,
          modifier = Modifier.weight(1.1f),
        )
        SummaryStatChip(
          label = "Terbayar",
          value = formatIdr(order.totalPaidAmount),
          icon = null,
          highlightColor = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.weight(1.1f),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Progress Pelunasan (${(order.paidProgress * 100).toInt()}%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Sisa: ${formatIdr(order.remainingOrderBalance)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (order.remainingOrderBalance == 0L && order.totalOrderValue > 0L) {
              MaterialTheme.colorScheme.tertiary
            } else {
              MaterialTheme.colorScheme.primary
            },
          )
        }
        LinearProgressIndicator(
          progress = { order.paidProgress },
          color = if (order.remainingOrderBalance == 0L && order.totalOrderValue > 0L) {
            MaterialTheme.colorScheme.tertiary
          } else {
            MaterialTheme.colorScheme.primary
          },
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50)),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        DeadlinePill(label = "Tutup GO", targetMillis = order.closingDateMillis)
        DeadlinePill(label = "Batas Bayar", targetMillis = order.paymentDeadlineMillis)
      }
    }
  }
}

@Composable
private fun SummaryStatChip(
  label: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  modifier: Modifier = Modifier,
  highlightColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = highlightColor,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall,
        color = highlightColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun EmptyGroupOrdersCard(
  isBuyerMode: Boolean,
  onOpenCreateOrder: () -> Unit,
  onOpenImportRekapan: () -> Unit,
  onSeedSampleData: () -> Unit,
  onOpenCatalog: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(24.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_orders),
        contentDescription = stringResource(id = R.string.cd_empty_illustration),
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(140.dp)
          .clip(RoundedCornerShape(20.dp)),
      )
      Text(
        text = if (isBuyerMode) "Belum Ada Grup Order Aktif" else "Belum Ada Grup Order K-Pop",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = if (isBuyerMode) {
          "Grup order yang dibuka oleh Owner JIEJIE akan muncul di sini. Anda bisa langsung melihat Katalog JIEGO untuk memesan item."
        } else {
          "Buat grup order baru atau impor langsung file Excel (.xlsx) semua sheet maupun file CSV untuk memuat semua batch, pembeli, item, dan sisa tagihan sekaligus."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (isBuyerMode) {
        Button(
          onClick = onOpenCatalog,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("buyer_empty_open_catalog_button"),
        ) {
          Text("Lihat Katalog JIEGO")
        }
      } else {
        Button(
          onClick = onOpenImportRekapan,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("empty_import_rekapan_button"),
        ) {
          Icon(Icons.Default.UploadFile, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Impor Rekapan Excel / CSV (Semua Sheet)")
        }
        OutlinedButton(
          onClick = onOpenCreateOrder,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("empty_create_go_button"),
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Buat Grup Order Baru")
        }
        OutlinedButton(
          onClick = onSeedSampleData,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("seed_sample_data_button"),
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Muat Contoh Data K-Pop GO")
        }
      }
    }
  }
}
