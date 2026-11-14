package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.auth.JiegoAuthConfig
import com.example.data.model.GoNotification
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Worker latar belakang periodik (Android WorkManager) yang tetap memeriksa pemberitahuan baru
 * dari Pemilik di Firestore meskipun aplikasi JIEGO sedang tidak dibuka.
 */
class NotificationSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val currentUser = Firebase.auth.currentUser ?: return Result.success()
    val uid = currentUser.uid

    return try {
      val databaseId = applicationContext.getString(R.string.firestore_database_id)
      val db = FirebaseFirestore.getInstance(databaseId)
      val snapshot = db.collection("stores")
        .document(JiegoAuthConfig.STORE_ID)
        .collection("notifications")
        .get()
        .await()

      val notifications = snapshot.toObjects(
        GoNotification::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }

      val unseen = NotificationSyncTracker.consumeNewNotifications(
        context = applicationContext,
        userId = uid,
        notifications = notifications,
      )

      if (unseen.isNotEmpty() && LocalNotificationHelper.hasNotificationPermission(applicationContext)) {
        if (unseen.size <= 3) {
          unseen.forEach { notif ->
            LocalNotificationHelper.showNotification(
              context = applicationContext,
              title = notif.title,
              body = notif.message,
              notificationId = notif.id.hashCode(),
            )
          }
        } else {
          val latest = unseen.first()
          LocalNotificationHelper.showNotification(
            context = applicationContext,
            title = "${unseen.size} Pemberitahuan Baru JIEGO",
            body = "${latest.title}: ${latest.message}",
            notificationId = "jiego_summary_${uid.hashCode()}".hashCode(),
          )
        }
      }
      Result.success()
    } catch (e: Exception) {
      Log.w(TAG, "Background notification check failed: ${e.message}")
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "NotificationSyncWorker"
    private const val WORK_NAME = "jiego_periodic_notif_sync"

    fun schedule(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
        15L,
        TimeUnit.MINUTES,
      )
        .setConstraints(constraints)
        .build()

      WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
  }
}
