package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GoStatus
import com.example.data.model.MemberOrderStatus
import com.example.data.model.NotificationType
import com.example.data.model.PaymentStatus
import com.example.ui.theme.AmberContainerLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldContainerLight
import com.example.ui.theme.EmeraldTertiary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val idrFormat: NumberFormat by lazy {
  NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
}

private val dateFormat: SimpleDateFormat by lazy {
  SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
}

private val dateTimeFormat: SimpleDateFormat by lazy {
  SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("id-ID"))
}

fun formatIdr(amount: Long): String = "Rp ${idrFormat.format(amount)}"

fun formatDate(millis: Long): String {
  if (millis <= 0L) return "-"
  return dateFormat.format(Date(millis))
}

fun formatDateTime(date: Date?): String {
  if (date == null) return "Baru saja"
  return dateTimeFormat.format(date)
}

fun daysUntilLabel(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): Pair<String, Boolean> {
  if (targetMillis <= 0L) return "-" to false
  val diffDays = (targetMillis - nowMillis) / 86_400_000L
  return when {
    diffDays < 0L -> "Lewat ${-diffDays} hari" to true
    diffDays == 0L -> "Hari ini!" to true
    diffDays <= 3L -> "$diffDays hari lagi" to true
    else -> "$diffDays hari lagi" to false
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun GoStatusBadge(status: GoStatus, modifier: Modifier = Modifier) {
  val (bg, fg) = when (status) {
    GoStatus.OPEN -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    GoStatus.CONFIRMED -> EmeraldContainerLight to EmeraldTertiary
    GoStatus.WAITING_PAYMENT -> AmberContainerLight to AmberWarning
    GoStatus.ORDERED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    GoStatus.SHIPPED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    GoStatus.COMPLETED -> EmeraldContainerLight to EmeraldTertiary
  }
  StatusPill(text = status.labelId, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun MemberStatusBadge(status: MemberOrderStatus, modifier: Modifier = Modifier) {
  val (bg, fg) = when (status) {
    MemberOrderStatus.CONFIRMED -> EmeraldContainerLight to EmeraldTertiary
    MemberOrderStatus.WAITING_PAYMENT -> AmberContainerLight to AmberWarning
    MemberOrderStatus.ORDERED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    MemberOrderStatus.SHIPPED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    MemberOrderStatus.COMPLETED -> EmeraldContainerLight to EmeraldTertiary
  }
  StatusPill(text = status.labelId, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun PaymentStatusBadge(status: PaymentStatus, modifier: Modifier = Modifier) {
  val (bg, fg) = when (status) {
    PaymentStatus.LUNAS -> EmeraldContainerLight to EmeraldTertiary
    PaymentStatus.DP -> AmberContainerLight to AmberWarning
    PaymentStatus.BELUM_BAYAR -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
  }
  StatusPill(text = status.labelId, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun NotificationTypeChip(type: NotificationType, modifier: Modifier = Modifier) {
  val (bg, fg, icon) = when (type) {
    NotificationType.ORDER_STATUS -> Triple(
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.onSecondaryContainer,
      Icons.Default.LocalShipping,
    )
    NotificationType.PAYMENT_REMINDER -> Triple(
      AmberContainerLight,
      AmberWarning,
      Icons.Default.Payments,
    )
    NotificationType.GO_UPDATE -> Triple(
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer,
      Icons.Default.Update,
    )
    NotificationType.PHOTO_UPDATE -> Triple(
      MaterialTheme.colorScheme.tertiaryContainer,
      MaterialTheme.colorScheme.onTertiaryContainer,
      Icons.Default.PhotoCamera,
    )
  }
  Surface(
    color = bg,
    contentColor = fg,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(13.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = type.labelId,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun OrderTimelineStepper(
  currentStatus: MemberOrderStatus,
  isPaidFull: Boolean,
  modifier: Modifier = Modifier,
) {
  val currentStep = when (currentStatus) {
    MemberOrderStatus.WAITING_PAYMENT -> if (isPaidFull) 1 else 0
    MemberOrderStatus.CONFIRMED -> 1
    MemberOrderStatus.ORDERED -> 2
    MemberOrderStatus.SHIPPED -> 3
    MemberOrderStatus.COMPLETED -> 4
  }
  val steps = listOf("Konfirmasi", "Lunas", "Diproses", "Dikirim", "Selesai")

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      steps.forEachIndexed { index, label ->
        val reached = index <= currentStep
        val dotColor = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.weight(1f),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                  if (index == 0) Color.Transparent
                  else if (index <= currentStep) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant
                )
            )
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (reached) dotColor else MaterialTheme.colorScheme.surface)
                .border(2.dp, dotColor, CircleShape),
            ) {
              if (reached) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = label,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(12.dp),
                )
              }
            }
            Box(
              modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                  if (index == steps.lastIndex) Color.Transparent
                  else if (index < currentStep) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant
                )
            )
          }
          Spacer(Modifier.height(4.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun DeadlinePill(
  label: String,
  targetMillis: Long,
  modifier: Modifier = Modifier,
) {
  val (rel, urgent) = daysUntilLabel(targetMillis)
  val bg = if (urgent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
  val fg = if (urgent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
    color = bg,
    contentColor = fg,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = if (urgent) Icons.Default.NotificationsActive else Icons.Default.Schedule,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = "$label: ${formatDate(targetMillis)} ($rel)",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
