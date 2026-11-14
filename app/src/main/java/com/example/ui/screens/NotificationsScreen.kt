package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.JiegoAuthConfig
import com.example.data.model.GoNotification
import com.example.data.model.NotificationType
import com.example.notifications.LocalNotificationHelper
import com.example.ui.components.NotificationTypeChip
import com.example.ui.components.formatDate
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatIdr
import com.example.ui.viewmodel.DeadlineAlert
import com.example.ui.viewmodel.KPopGoData

private enum class NotifFilter(val label: String, val type: NotificationType?) {
  ALL("Semua", null),
  PHOTO("Update Foto", NotificationType.PHOTO_UPDATE),
  STATUS("Status Pesanan", NotificationType.ORDER_STATUS),
  REMINDER("Pengingat Bayar", NotificationType.PAYMENT_REMINDER),
  GO_UPDATE("Info Grup Order", NotificationType.GO_UPDATE),
}

@Composable
fun NotificationsScreen(
  data: KPopGoData,
  onOpenOrderDetail: (String) -> Unit,
  onOpenPhotos: () -> Unit = {},
  onOpenCatalog: () -> Unit = {},
  onMarkRead: (String, Boolean) -> Unit,
  onMarkAllRead: (List<GoNotification>) -> Unit,
  isOwnerMode: Boolean = false,
  onSendOwnerAnnouncement: (title: String, message: String) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedFilter by remember { mutableStateOf(NotifFilter.ALL) }
  var showAnnouncementDialog by remember { mutableStateOf(false) }
  var hasNotifPermission by remember {
    mutableStateOf(LocalNotificationHelper.hasNotificationPermission(context))
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasNotifPermission = granted
  }

  val buyerSubscriberEmails = remember(data.subscribers) {
    data.subscribers
      .map { it.email.trim() }
      .filter { it.isNotBlank() && !it.equals(JiegoAuthConfig.OWNER_EMAIL, ignoreCase = true) }
      .distinctBy { it.lowercase() }
  }

  if (showAnnouncementDialog && isOwnerMode) {
    BroadcastAnnouncementDialog(
      buyerEmails = buyerSubscriberEmails,
      onDismiss = { showAnnouncementDialog = false },
      onSendAppNotification = { title, msg ->
        onSendOwnerAnnouncement(title, msg)
        showAnnouncementDialog = false
      },
      onLaunchEmailComposer = { title, msg ->
        launchEmailAnnouncement(
          context = context,
          recipientEmails = buyerSubscriberEmails,
          subject = title,
          body = msg,
        )
      },
    )
  }

  val deadlineAlerts = remember(data) { data.computeDeadlineAlerts() }
  val filteredNotifications = remember(data.notifications, selectedFilter) {
    val t = selectedFilter.type
    if (t == null) data.notifications else data.notifications.filter { it.typeEnum == t }
  }

  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    // Owner Broadcast Announcement Card
    if (isOwnerMode) {
      item(key = "owner_broadcast_card") {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Kirim Pengumuman ke Semua Pembeli",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "${buyerSubscriberEmails.size} email pembeli terdaftar • Otomatis muncul di tab & bar notifikasi HP pembeli.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
                )
              }
            }
            Button(
              onClick = { showAnnouncementDialog = true },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("create_announcement_button"),
            ) {
              Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Buat Pengumuman / Kirim Email Pembeli")
            }
          }
        }
      }
    }

    // Android 13+ Push Notification Opt-in Card or Active Background Status Card
    if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      item(key = "notif_permission_card") {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
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
                text = "Aktifkan Notifikasi Perangkat",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text = "Terima peringatan status pesanan, update foto, & pengumuman langsung di bar notifikasi meski aplikasi ditutup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
              )
            }
            Spacer(Modifier.width(8.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              shape = RoundedCornerShape(10.dp),
            ) {
              Text("Izinkan")
            }
          }
        }
      }
    } else {
      item(key = "notif_bg_status_card") {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Notifikasi Bar Aktif: pembaruan dicek langsung saat aplikasi dibuka dan otomatis setiap ±15 menit saat aplikasi ditutup.",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Active Payment Deadline Reminders (Live Calculated)
    if (deadlineAlerts.isNotEmpty()) {
      item(key = "deadline_alerts_header") {
        Text(
          text = "Pengingat Tenggat Pembayaran Aktif",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      items(deadlineAlerts, key = { "alert_${it.order.id}" }) { alert ->
        DeadlineAlertCard(
          alert = alert,
          onClick = { onOpenOrderDetail(alert.order.id) },
        )
      }
    }

    // Notification Center Header + Filter Chips
    item(key = "notif_filter_bar") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Riwayat Pemberitahuan (${data.unreadNotificationsCount} belum dibaca)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          if (data.unreadNotificationsCount > 0) {
            TextButton(onClick = { onMarkAllRead(data.notifications) }) {
              Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Baca Semua")
            }
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(NotifFilter.entries) { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { selectedFilter = filter },
              label = { Text(filter.label) },
            )
          }
        }
      }
    }

    if (filteredNotifications.isEmpty()) {
      item(key = "empty_notifications") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(28.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Notifications,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(40.dp),
            )
            Text("Belum Ada Notifikasi", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Setiap perubahan status pesanan, pengingat tenggat pembayaran, atau pembaruan informasi grup order akan muncul otomatis di sini.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredNotifications, key = { it.id }) { notif ->
        NotificationItemCard(
          notification = notif,
          onClick = {
            if (!notif.isRead) {
              onMarkRead(notif.id, true)
            }
            if (notif.orderId == "photo_gallery") {
              onOpenPhotos()
            } else if (notif.orderId == "catalog_store") {
              onOpenCatalog()
            } else if (notif.orderId != "store_broadcast" && notif.orderId.isNotBlank()) {
              onOpenOrderDetail(notif.orderId)
            }
          },
          onToggleRead = { onMarkRead(notif.id, !notif.isRead) },
        )
      }
    }
  }
}

@Composable
private fun DeadlineAlertCard(
  alert: DeadlineAlert,
  onClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(14.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Schedule,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (alert.isOverdue) {
            "Jatuh Tempo Terlewat: ${alert.order.name}"
          } else if (alert.daysRemaining == 0L) {
            "Batas Bayar Hari Ini: ${alert.order.name}"
          } else {
            "Batas Bayar ${alert.daysRemaining} Hari Lagi: ${alert.order.name}"
          },
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${alert.unpaidMembersCount} anggota belum lunas • Sisa ${formatIdr(alert.totalRemainingAmount)} (Batas: ${formatDate(alert.order.paymentDeadlineMillis)})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
        )
      }
    }
  }
}

@Composable
private fun NotificationItemCard(
  notification: GoNotification,
  onClick: () -> Unit,
  onToggleRead: () -> Unit,
) {
  val borderColor = if (!notification.isRead) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  } else {
    MaterialTheme.colorScheme.outlineVariant
  }

  Surface(
    color = if (!notification.isRead) {
      MaterialTheme.colorScheme.surface
    } else {
      MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    },
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, borderColor, RoundedCornerShape(18.dp))
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .testTag("notif_card_${notification.id}"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!notification.isRead) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
            )
          }
          NotificationTypeChip(type = notification.typeEnum)
        }
        Text(
          text = formatDateTime(notification.createdAt?.toDate()),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = notification.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
      )
      Text(
        text = notification.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = notification.orderName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        OutlinedButton(
          onClick = onToggleRead,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(32.dp),
        ) {
          Text(
            text = if (notification.isRead) "Tandai Belum Dibaca" else "Tandai Dibaca",
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }
  }
}

@Composable
private fun BroadcastAnnouncementDialog(
  buyerEmails: List<String>,
  onDismiss: () -> Unit,
  onSendAppNotification: (title: String, message: String) -> Unit,
  onLaunchEmailComposer: (title: String, message: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  val canSubmit = title.trim().isNotEmpty() && message.trim().isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pengumuman ke Semua Pembeli") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Judul Pengumuman *") },
          placeholder = { Text("Misal: Barang Batch 12 Sudah Tiba di Admin!") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("announcement_title_input"),
        )
        OutlinedTextField(
          value = message,
          onValueChange = { message = it },
          label = { Text("Isi Pesan *") },
          placeholder = { Text("Tulis info pelunasan, update resi, atau pengumuman...") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("announcement_message_input"),
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(10.dp),
          ) {
            Text(
              text = "${buyerEmails.size} email pembeli sudah login tercatat",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Kirim Notifikasi Aplikasi akan langsung masuk ke tab & bar notifikasi pembeli. Tombol Email membuka Gmail/Email dengan alamat pembeli otomatis terisi di BCC.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (buyerEmails.isNotEmpty()) {
          OutlinedButton(
            onClick = { onLaunchEmailComposer(title.trim(), message.trim()) },
            enabled = canSubmit,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("send_email_announcement_button"),
          ) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buka Aplikasi Email (BCC ${buyerEmails.size} Pembeli)")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSendAppNotification(title, message) },
        enabled = canSubmit,
        modifier = Modifier.testTag("send_app_announcement_button"),
      ) {
        Text("Kirim Notifikasi")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    },
  )
}

private fun launchEmailAnnouncement(
  context: Context,
  recipientEmails: List<String>,
  subject: String,
  body: String,
) {
  val sendIntent = Intent(Intent.ACTION_SEND).apply {
    type = "message/rfc822"
    if (recipientEmails.isNotEmpty()) {
      putExtra(Intent.EXTRA_BCC, recipientEmails.toTypedArray())
    }
    putExtra(Intent.EXTRA_SUBJECT, "[JIEGO] $subject")
    putExtra(Intent.EXTRA_TEXT, body)
  }
  try {
    context.startActivity(Intent.createChooser(sendIntent, "Kirim Pengumuman ke Email Pembeli"))
  } catch (_: Exception) {
    val fallback = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
      if (recipientEmails.isNotEmpty()) {
        putExtra(Intent.EXTRA_BCC, recipientEmails.toTypedArray())
      }
      putExtra(Intent.EXTRA_SUBJECT, "[JIEGO] $subject")
      putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(fallback) }
  }
}
