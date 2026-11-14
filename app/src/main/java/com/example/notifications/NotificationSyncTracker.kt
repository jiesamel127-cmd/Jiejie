package com.example.notifications

import android.content.Context
import com.example.data.model.GoNotification
import kotlin.math.max

/**
 * Mencatat ID dan waktu notifikasi yang sudah pernah ditampilkan sebagai notifikasi bar HP,
 * agar listener real-time maupun pengecekan latar belakang (WorkManager) tidak memunculkan
 * notifikasi berulang atau membanjiri pengguna baru dengan riwayat lama.
 */
object NotificationSyncTracker {
  private const val PREFS_NAME = "jiego_notif_sync_prefs"
  private const val MAX_STORED_IDS = 120

  fun isInitialized(context: Context, userId: String): Boolean {
    if (userId.isBlank()) return false
    return prefs(context).getBoolean("baseline_set_$userId", false)
  }

  fun markBaseline(context: Context, userId: String, notifications: List<GoNotification>) {
    if (userId.isBlank()) return
    val p = prefs(context)
    val existingIds = p.getStringSet("shown_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
    var maxTs = p.getLong("last_ts_ms_$userId", 0L)
    notifications.forEach { n ->
      if (n.id.isNotBlank()) existingIds.add(n.id)
      val ts = n.createdAt?.toDate()?.time ?: 0L
      if (ts > maxTs) maxTs = ts
    }
    if (maxTs == 0L) {
      maxTs = System.currentTimeMillis()
    }
    p.edit()
      .putBoolean("baseline_set_$userId", true)
      .putLong("last_ts_ms_$userId", maxTs)
      .putStringSet("shown_ids_$userId", trimIds(existingIds))
      .apply()
  }

  fun markLocallyShown(context: Context, userId: String, notificationId: String, createdAtMs: Long = System.currentTimeMillis()) {
    if (userId.isBlank() || notificationId.isBlank()) return
    val p = prefs(context)
    val existingIds = p.getStringSet("shown_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
    existingIds.add(notificationId)
    val prevTs = p.getLong("last_ts_ms_$userId", 0L)
    p.edit()
      .putBoolean("baseline_set_$userId", true)
      .putLong("last_ts_ms_$userId", max(prevTs, createdAtMs))
      .putStringSet("shown_ids_$userId", trimIds(existingIds))
      .apply()
  }

  /**
   * Memfilter notifikasi yang benar-benar baru sejak baseline terakhir dan langsung
   * menandainya sudah ditampilkan sehingga aman dari duplikasi.
   */
  @Synchronized
  fun consumeNewNotifications(
    context: Context,
    userId: String,
    notifications: List<GoNotification>,
  ): List<GoNotification> {
    if (userId.isBlank()) return emptyList()
    if (!isInitialized(context, userId)) {
      markBaseline(context, userId, notifications)
      return emptyList()
    }

    val p = prefs(context)
    val shownIds = p.getStringSet("shown_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
    val lastTsMs = p.getLong("last_ts_ms_$userId", 0L)
    val cutoffMs = (lastTsMs - 120_000L).coerceAtLeast(0L)

    val unseen = notifications.filter { notif ->
      if (notif.id.isBlank() || notif.id in shownIds || notif.isRead) {
        false
      } else {
        val createdMs = notif.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        createdMs >= cutoffMs
      }
    }

    if (unseen.isNotEmpty()) {
      var newestTs = lastTsMs
      unseen.forEach { n ->
        shownIds.add(n.id)
        val ts = n.createdAt?.toDate()?.time ?: 0L
        if (ts > newestTs) newestTs = ts
      }
      p.edit()
        .putLong("last_ts_ms_$userId", max(newestTs, System.currentTimeMillis()))
        .putStringSet("shown_ids_$userId", trimIds(shownIds))
        .apply()
    }
    return unseen
  }

  private fun trimIds(ids: Set<String>): Set<String> {
    if (ids.size <= MAX_STORED_IDS) return ids
    return ids.toList().takeLast(MAX_STORED_IDS).toSet()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
