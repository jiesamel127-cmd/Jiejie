package com.example.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GoNotification
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class NotificationSyncTrackerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("jiego_notif_sync_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun firstRunSetsBaselineAndSubsequentRunsDeliverNewNotificationsOnce() {
    val now = System.currentTimeMillis()
    val oldNotif = GoNotification(
      id = "n_old",
      title = "Lama",
      message = "Pesan lama",
      isRead = false,
      createdAt = Timestamp(Date(now - 600_000L)),
    )

    // Initial call establishes baseline -> should not spam historical notifications
    val initial = NotificationSyncTracker.consumeNewNotifications(context, "uid_buyer", listOf(oldNotif))
    assertTrue(initial.isEmpty())

    // New notification arrives later
    val newNotif = GoNotification(
      id = "n_new",
      title = "Pengumuman Baru",
      message = "Paket Batch 10 tiba",
      isRead = false,
      createdAt = Timestamp(Date(now + 10_000L)),
    )
    val second = NotificationSyncTracker.consumeNewNotifications(context, "uid_buyer", listOf(newNotif, oldNotif))
    assertEquals(1, second.size)
    assertEquals("n_new", second.first().id)

    // Background worker running right after should NOT re-notify for n_new
    val third = NotificationSyncTracker.consumeNewNotifications(context, "uid_buyer", listOf(newNotif, oldNotif))
    assertTrue(third.isEmpty())
  }
}
