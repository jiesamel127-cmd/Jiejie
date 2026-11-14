package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.auth.JiegoAuthConfig
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.importer.ParsedRekapanResult
import com.example.data.model.CatalogItem
import com.example.data.model.ExchangeRates
import com.example.data.model.GoNotification
import com.example.data.model.GoStatus
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.MemberOrderStatus
import com.example.data.model.NotificationSubscriber
import com.example.data.model.NotificationType
import com.example.data.model.OrderItem
import com.example.data.model.PhotoCategory
import com.example.data.model.PhotoUpdate
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale

data class DraftOrderItem(
  val itemName: String,
  val variant: String,
  val quantity: Long,
  val unitPrice: Long,
)

data class ImportSummary(
  val batchesImported: Int,
  val membersImported: Int,
  val itemsImported: Int,
  val unpaidItemsCount: Int,
  val updatedMembersCount: Int = 0,
  val updatedItemsCount: Int = 0,
  val updatedBatchesCount: Int = 0,
) {
  val hasChanges: Boolean
    get() = batchesImported > 0 ||
      membersImported > 0 ||
      itemsImported > 0 ||
      updatedMembersCount > 0 ||
      updatedItemsCount > 0 ||
      updatedBatchesCount > 0
}

class GroupOrderRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("Pengguna harus masuk dengan akun Google terlebih dahulu.")
  }

  private fun storeDoc() = db.collection("stores").document(JiegoAuthConfig.STORE_ID)

  fun observeGroupOrders(userId: String): Flow<List<GroupOrder>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/groupOrders"
    emitAll(
      storeDoc().collection("groupOrders")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(GroupOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  fun observeMembers(userId: String): Flow<List<MemberOrder>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/members"
    emitAll(
      storeDoc().collection("members")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(MemberOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  fun observeItems(userId: String): Flow<List<OrderItem>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/items"
    emitAll(
      storeDoc().collection("items")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(OrderItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  fun observeNotifications(userId: String): Flow<List<GoNotification>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/notifications"
    emitAll(
      storeDoc().collection("notifications")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(GoNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  fun observeCatalogItems(userId: String): Flow<List<CatalogItem>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/catalogItems"
    emitAll(
      storeDoc().collection("catalogItems")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CatalogItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  fun observeExchangeRates(userId: String): Flow<ExchangeRates> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/settings/exchangeRates"
    emitAll(
      storeDoc().collection("settings").document("exchangeRates")
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(ExchangeRates::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: ExchangeRates(userId = userId)
          } else {
            ExchangeRates(userId = userId)
          }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.GET, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  suspend fun saveExchangeRates(rates: ExchangeRates): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("settings").document("exchangeRates")
    val payload = mapOf(
      "userId" to uid,
      "china" to rates.china.coerceIn(0.01, 1_000_000.0),
      "korea" to rates.korea.coerceIn(0.01, 1_000_000.0),
      "thailand" to rates.thailand.coerceIn(0.01, 1_000_000.0),
      "jepang" to rates.jepang.coerceIn(0.01, 1_000_000.0),
      "vietnam" to rates.vietnam.coerceIn(0.01, 1_000_000.0),
      "taiwan" to rates.taiwan.coerceIn(0.01, 1_000_000.0),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  fun observePhotoUpdates(userId: String): Flow<List<PhotoUpdate>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/photoUpdates"
    emitAll(
      storeDoc().collection("photoUpdates")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(PhotoUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  suspend fun addPhotoUpdate(
    category: PhotoCategory,
    uploaderRole: String,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ): Result<Pair<String, Pair<String, String>>> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanName = uploaderName.trim().take(80)
    val cleanPhone = uploaderPhone.trim().take(30)
    val cleanCaption = caption.trim().take(300)
    val cleanOrderId = orderId.trim().take(128)
    val cleanOrderName = orderName.trim().take(120)

    if (category == PhotoCategory.TESTI && (cleanName.isEmpty() || cleanPhone.length < 5)) {
      return Result.failure(IllegalArgumentException("Nama pembeli dan nomor HP wajib diisi untuk foto testi."))
    }
    if (imageBase64.length < 10) {
      return Result.failure(IllegalArgumentException("Silakan pilih foto terlebih dahulu."))
    }

    val docRef = storeDoc().collection("photoUpdates").document()
    val payload = mapOf(
      "userId" to uid,
      "category" to category.code,
      "uploaderRole" to if (uploaderRole == "BUYER") "BUYER" else "OWNER",
      "uploaderName" to cleanName,
      "uploaderPhone" to cleanPhone,
      "orderId" to cleanOrderId,
      "orderName" to cleanOrderName,
      "caption" to cleanCaption,
      "imageBase64" to imageBase64,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      docRef.set(payload).await()
      val (notifTitle, notifMsg) = buildPhotoNotificationText(
        isUpdate = false,
        category = category,
        uploaderRole = uploaderRole,
        uploaderName = cleanName,
        uploaderPhone = cleanPhone,
        orderName = cleanOrderName,
        caption = cleanCaption,
      )
      createNotificationInternal(
        uid = uid,
        orderId = cleanOrderId.ifBlank { "photo_gallery" },
        orderName = cleanOrderName.ifBlank { "Halaman Foto JIEGO" },
        memberId = "",
        type = NotificationType.PHOTO_UPDATE,
        title = notifTitle,
        message = notifMsg,
      )
      Result.success(docRef.id to (notifTitle to notifMsg))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updatePhotoUpdate(
    existing: PhotoUpdate,
    category: PhotoCategory,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ): Result<Pair<String, String>> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanName = uploaderName.trim().take(80)
    val cleanPhone = uploaderPhone.trim().take(30)
    val cleanCaption = caption.trim().take(300)
    val cleanOrderId = orderId.trim().take(128)
    val cleanOrderName = orderName.trim().take(120)

    if (category == PhotoCategory.TESTI && (cleanName.isEmpty() || cleanPhone.length < 5)) {
      return Result.failure(IllegalArgumentException("Nama pembeli dan nomor HP wajib diisi untuk foto testi."))
    }

    val docRef = storeDoc().collection("photoUpdates").document(existing.id)
    val updates = mapOf(
      "category" to category.code,
      "uploaderName" to cleanName,
      "uploaderPhone" to cleanPhone,
      "orderId" to cleanOrderId,
      "orderName" to cleanOrderName,
      "caption" to cleanCaption,
      "imageBase64" to imageBase64,
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      docRef.update(updates).await()
      val (notifTitle, notifMsg) = buildPhotoNotificationText(
        isUpdate = true,
        category = category,
        uploaderRole = existing.uploaderRole,
        uploaderName = cleanName,
        uploaderPhone = cleanPhone,
        orderName = cleanOrderName,
        caption = cleanCaption,
      )
      createNotificationInternal(
        uid = uid,
        orderId = cleanOrderId.ifBlank { "photo_gallery" },
        orderName = cleanOrderName.ifBlank { "Halaman Foto JIEGO" },
        memberId = "",
        type = NotificationType.PHOTO_UPDATE,
        title = notifTitle,
        message = notifMsg,
      )
      Result.success(notifTitle to notifMsg)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deletePhotoUpdate(photoId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("photoUpdates").document(photoId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  private fun buildPhotoNotificationText(
    isUpdate: Boolean,
    category: PhotoCategory,
    uploaderRole: String,
    uploaderName: String,
    uploaderPhone: String,
    orderName: String,
    caption: String,
  ): Pair<String, String> {
    val actionWord = if (isUpdate) "memperbarui" else "mengunggah"
    val title = if (category == PhotoCategory.TESTI || uploaderRole == "BUYER") {
      if (isUpdate) "Update Foto Testi (${uploaderName.ifBlank { "Pembeli" }})"
      else "Foto Testi Baru (${uploaderName.ifBlank { "Pembeli" }})"
    } else {
      if (isUpdate) "Update ${category.labelId}" else "Foto Baru: ${category.labelId}"
    }
    val who = if (category == PhotoCategory.TESTI || uploaderRole == "BUYER") {
      val phoneSuffix = if (uploaderPhone.isNotBlank()) " ($uploaderPhone)" else ""
      "${uploaderName.ifBlank { "Pembeli" }}$phoneSuffix"
    } else {
      uploaderName.ifBlank { "Owner JIEJIE" }
    }
    val orderPart = if (orderName.isNotBlank()) " untuk $orderName" else ""
    val captionPart = if (caption.isNotBlank()) " • $caption" else ""
    val message = "$who $actionWord ${category.labelId}$orderPart$captionPart".take(400)
    return title.take(120) to message
  }

  private fun logStreamErrorIfActive(
    error: Throwable,
    operationType: OperationType,
    path: String,
    expectedUid: String,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(toLog, operationType, path, auth)
    }
  }

  suspend fun createGroupOrder(
    name: String,
    artistGroup: String,
    batchCode: String,
    closingDateMillis: Long,
    paymentDeadlineMillis: Long,
    notes: String,
    emitNotification: Boolean = true,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("groupOrders").document()
    val payload = mapOf(
      "userId" to uid,
      "name" to name.trim().take(120),
      "artistGroup" to artistGroup.trim().ifBlank { "K-Pop" }.take(60),
      "batchCode" to batchCode.trim().take(40),
      "status" to GoStatus.OPEN.code,
      "closingDateMillis" to closingDateMillis,
      "paymentDeadlineMillis" to paymentDeadlineMillis,
      "notes" to notes.trim().take(500),
      "memberCount" to 0L,
      "totalOrderValue" to 0L,
      "totalPaidAmount" to 0L,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      if (emitNotification) {
        createNotificationInternal(
          uid = uid,
          orderId = docRef.id,
          orderName = name.trim().take(120),
          memberId = "",
          type = NotificationType.GO_UPDATE,
          title = "Grup Order Dibuat",
          message = "GO \"${name.trim().take(80)}\" (${artistGroup.trim().take(40)}) telah dibuka dan siap menerima pesanan.",
        )
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateGroupOrderInfo(
    order: GroupOrder,
    name: String,
    artistGroup: String,
    batchCode: String,
    closingDateMillis: Long,
    paymentDeadlineMillis: Long,
    notes: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("groupOrders").document(order.id)
    val updates = mapOf(
      "name" to name.trim(),
      "artistGroup" to artistGroup.trim(),
      "batchCode" to batchCode.trim(),
      "closingDateMillis" to closingDateMillis,
      "paymentDeadlineMillis" to paymentDeadlineMillis,
      "notes" to notes.trim(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.update(updates).await()
      createNotificationInternal(
        uid = uid,
        orderId = order.id,
        orderName = name.trim(),
        memberId = "",
        type = NotificationType.GO_UPDATE,
        title = "Info Grup Order Diperbarui",
        message = "Detail informasi atau jadwal pada GO \"${name.trim()}\" telah diperbarui.",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateGroupOrderStatus(
    order: GroupOrder,
    newStatus: GoStatus,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("groupOrders").document(order.id)
    return try {
      docRef.update(
        mapOf(
          "status" to newStatus.code,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      createNotificationInternal(
        uid = uid,
        orderId = order.id,
        orderName = order.name,
        memberId = "",
        type = NotificationType.ORDER_STATUS,
        title = "Status GO: ${newStatus.labelId}",
        message = "Status grup order \"${order.name}\" berubah menjadi ${newStatus.labelId}.",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteGroupOrder(orderId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val goRef = storeDoc().collection("groupOrders").document(orderId)
    return try {
      val itemsSnap = storeDoc().collection("items").whereEqualTo("orderId", orderId).get().await()
      val membersSnap = storeDoc().collection("members").whereEqualTo("orderId", orderId).get().await()
      val batch = db.batch()
      itemsSnap.documents.forEach { batch.delete(it.reference) }
      membersSnap.documents.forEach { batch.delete(it.reference) }
      batch.delete(goRef)
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, goRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun addMemberWithItems(
    order: GroupOrder,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
    items: List<DraftOrderItem>,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val memberRef = storeDoc().collection("members").document()
    val totalItemsCost = items.sumOf { it.quantity * it.unitPrice }
    val initialStatus = if (initialPaidAmount >= totalItemsCost + shippingFee && totalItemsCost + shippingFee > 0) {
      MemberOrderStatus.CONFIRMED.code
    } else {
      MemberOrderStatus.WAITING_PAYMENT.code
    }

    val memberPayload = mapOf(
      "userId" to uid,
      "orderId" to order.id,
      "memberName" to memberName.trim(),
      "memberHandle" to memberHandle.trim(),
      "isCurrentUser" to isCurrentUser,
      "shippingFee" to shippingFee.coerceAtLeast(0L),
      "totalItemsCost" to totalItemsCost,
      "paidAmount" to initialPaidAmount.coerceAtLeast(0L),
      "orderStatus" to initialStatus,
      "lastPaymentMethod" to paymentMethod.trim(),
      "notes" to notes.trim(),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      memberRef.set(memberPayload).await()
      for (item in items) {
        val itemRef = storeDoc().collection("items").document()
        itemRef.set(
          mapOf(
            "userId" to uid,
            "orderId" to order.id,
            "memberId" to memberRef.id,
            "itemName" to item.itemName.trim(),
            "variant" to item.variant.trim(),
            "quantity" to item.quantity.coerceAtLeast(1L),
            "unitPrice" to item.unitPrice.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      recalculateGroupOrderTotals(uid, order.id)
      createNotificationInternal(
        uid = uid,
        orderId = order.id,
        orderName = order.name,
        memberId = memberRef.id,
        type = NotificationType.ORDER_STATUS,
        title = "Pesanan Baru: ${memberName.trim()}",
        message = "${memberName.trim()} bergabung di ${order.name} dengan ${items.size} jenis item.",
      )
      Result.success(memberRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, memberRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun addOrderItem(
    orderId: String,
    memberId: String,
    itemName: String,
    variant: String,
    quantity: Long,
    unitPrice: Long,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val itemRef = storeDoc().collection("items").document()
    val payload = mapOf(
      "userId" to uid,
      "orderId" to orderId,
      "memberId" to memberId,
      "itemName" to itemName.trim(),
      "variant" to variant.trim(),
      "quantity" to quantity.coerceAtLeast(1L),
      "unitPrice" to unitPrice.coerceAtLeast(0L),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      itemRef.set(payload).await()
      recalculateMemberAndOrderTotals(uid, orderId, memberId)
      Result.success(itemRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, itemRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteOrderItem(
    orderId: String,
    memberId: String,
    itemId: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val itemRef = storeDoc().collection("items").document(itemId)
    return try {
      itemRef.delete().await()
      recalculateMemberAndOrderTotals(uid, orderId, memberId)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, itemRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun recordMemberPayment(
    orderName: String,
    member: MemberOrder,
    newPaidAmount: Long,
    paymentMethod: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val memberRef = storeDoc().collection("members").document(member.id)
    val clampedPaid = newPaidAmount.coerceAtLeast(0L)
    return try {
      memberRef.update(
        mapOf(
          "paidAmount" to clampedPaid,
          "lastPaymentMethod" to paymentMethod.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      recalculateGroupOrderTotals(uid, member.orderId)

      val remaining = (member.totalCost - clampedPaid).coerceAtLeast(0L)
      val title = if (remaining == 0L && member.totalCost > 0L) {
        "Pembayaran Lunas (${member.memberName})"
      } else {
        "Pembayaran Dicatat (${member.memberName})"
      }
      val msg = if (remaining == 0L && member.totalCost > 0L) {
        "Tagihan ${member.memberName} pada $orderName telah lunas via ${paymentMethod.ifBlank { "Transfer" }}."
      } else {
        "${member.memberName} membayar ${formatIdr(clampedPaid)} ($orderName). Sisa tagihan: ${formatIdr(remaining)}."
      }
      createNotificationInternal(
        uid = uid,
        orderId = member.orderId,
        orderName = orderName,
        memberId = member.id,
        type = NotificationType.ORDER_STATUS,
        title = title,
        message = msg,
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, memberRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateMemberDetailsAndStatus(
    orderName: String,
    member: MemberOrder,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    newStatus: MemberOrderStatus,
    notes: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val memberRef = storeDoc().collection("members").document(member.id)
    return try {
      memberRef.update(
        mapOf(
          "memberName" to memberName.trim(),
          "memberHandle" to memberHandle.trim(),
          "isCurrentUser" to isCurrentUser,
          "shippingFee" to shippingFee.coerceAtLeast(0L),
          "orderStatus" to newStatus.code,
          "notes" to notes.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      recalculateGroupOrderTotals(uid, member.orderId)
      if (member.orderStatus != newStatus.code) {
        createNotificationInternal(
          uid = uid,
          orderId = member.orderId,
          orderName = orderName,
          memberId = member.id,
          type = NotificationType.ORDER_STATUS,
          title = newStatus.labelId,
          message = "Status pesanan ${memberName.trim()} di $orderName diperbarui menjadi: ${newStatus.labelId}.",
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, memberRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun sendPaymentReminder(
    order: GroupOrder,
    member: MemberOrder,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val remaining = member.remainingBalance
    return try {
      val id = createNotificationInternal(
        uid = uid,
        orderId = order.id,
        orderName = order.name,
        memberId = member.id,
        type = NotificationType.PAYMENT_REMINDER,
        title = "Pengingat Pelunasan Tagihan",
        message = "Halo ${member.memberName}, sisa tagihan ${formatIdr(remaining)} untuk ${order.name} segera jatuh tempo.",
      )
      Result.success(id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "stores/${JiegoAuthConfig.STORE_ID}/notifications", auth)
      Result.failure(e)
    }
  }

  suspend fun markNotificationRead(notificationId: String, isRead: Boolean): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("notifications").document(notificationId)
    return try {
      docRef.update(
        mapOf(
          "isRead" to isRead,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun markAllNotificationsRead(notificationIds: List<String>): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (notificationIds.isEmpty()) return Result.success(Unit)
    return try {
      val batch = db.batch()
      notificationIds.forEach { id ->
        val ref = storeDoc().collection("notifications").document(id)
        batch.update(ref, mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()))
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "stores/${JiegoAuthConfig.STORE_ID}/notifications", auth)
      Result.failure(e)
    }
  }

  suspend fun seedSampleKPopData(): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val now = System.currentTimeMillis()
    val day = 86_400_000L

    return try {
      // Sample GO 1: SEVENTEEN
      val go1Id = createGroupOrder(
        name = "PO SEVENTEEN Spill The Feels (Weverse POB)",
        artistGroup = "SEVENTEEN",
        batchCode = "SVT-BATCH-04",
        closingDateMillis = now + 2 * day,
        paymentDeadlineMillis = now + 3 * day,
        notes = "Estimasi EMS & tax sudah termasuk. Pelunasan wajib sebelum tanggal tutup GO.",
      ).getOrThrow()

      val go1Snap = storeDoc().collection("groupOrders").document(go1Id).get().await()
        .toObject(GroupOrder::class.java) ?: error("Failed to load created sample GO")

      addMemberWithItems(
        order = go1Snap,
        memberName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Saya (Pembeli)",
        memberHandle = "@my_carat_acc",
        isCurrentUser = true,
        shippingFee = 35_000L,
        initialPaidAmount = 200_000L,
        paymentMethod = "QRIS",
        notes = "Req bias Mingyu / Wonwoo",
        items = listOf(
          DraftOrderItem("Album Spill The Feels (Carat Ver.)", "Mingyu Ver.", 1L, 215_000L),
          DraftOrderItem("Album Standard Ver. 0", "Sealed + POB Weverse", 1L, 285_000L),
        ),
      ).getOrThrow()

      addMemberWithItems(
        order = go1Snap,
        memberName = "Nadia Putri",
        memberHandle = "@nad_seventeen",
        isCurrentUser = false,
        shippingFee = 25_000L,
        initialPaidAmount = 310_000L,
        paymentMethod = "Transfer BCA",
        notes = "Sudah lunas + ongkir domestik",
        items = listOf(
          DraftOrderItem("Album Standard Ver. 1", "Sealed + POB", 1L, 285_000L),
        ),
      ).getOrThrow()

      addMemberWithItems(
        order = go1Snap,
        memberName = "Rina Oktavia",
        memberHandle = "@rina_dk",
        isCurrentUser = false,
        shippingFee = 40_000L,
        initialPaidAmount = 0L,
        paymentMethod = "",
        notes = "Menunggu transfer DP",
        items = listOf(
          DraftOrderItem("Kit Ver. Album", "Fullset", 1L, 340_000L),
          DraftOrderItem("Mini Photocard Binder", "DK", 1L, 125_000L),
        ),
      ).getOrThrow()

      // Sample GO 2: NewJeans
      val go2Id = createGroupOrder(
        name = "GO NewJeans Bunnies Camp Tokyo Merch",
        artistGroup = "NewJeans",
        batchCode = "NJ-JP-02",
        closingDateMillis = now + 6 * day,
        paymentDeadlineMillis = now + 5 * day,
        notes = "Pengiriman via EMS Japan ke warehouse Jakarta.",
      ).getOrThrow()

      val go2Snap = storeDoc().collection("groupOrders").document(go2Id).get().await()
        .toObject(GroupOrder::class.java) ?: error("Failed to load sample GO 2")

      updateGroupOrderStatus(go2Snap, GoStatus.SHIPPED).getOrThrow()

      addMemberWithItems(
        order = go2Snap,
        memberName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Saya (Pembeli)",
        memberHandle = "@bunnies_id",
        isCurrentUser = true,
        shippingFee = 45_000L,
        initialPaidAmount = 495_000L,
        paymentMethod = "Transfer Mandiri",
        notes = "Resi JNE menyusul",
        items = listOf(
          DraftOrderItem("Binky Bong Keyring Mini", "Official", 1L, 240_000L),
          DraftOrderItem("Bunnies Camp T-Shirt", "Size M - Hanni", 1L, 210_000L),
        ),
      ).getOrThrow()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "stores/${JiegoAuthConfig.STORE_ID}/groupOrders", auth)
      Result.failure(e)
    }
  }

  suspend fun importRekapanResult(
    parsed: ParsedRekapanResult,
    isAutoSync: Boolean = false,
  ): Result<ImportSummary> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (parsed.batches.isEmpty()) {
      return Result.failure(IllegalArgumentException("Tidak ada data batch atau pembeli yang ditemukan pada file rekapan."))
    }

    val now = System.currentTimeMillis()
    val day = 86_400_000L

    return try {
      // Load existing store records so re-imports & auto-syncs perform smart upsert without duplicates
      val existingGosMap = storeDoc().collection("groupOrders").get().await()
        .documents.mapNotNull { doc -> doc.toObject(GroupOrder::class.java)?.let { doc.id to it.copy(id = doc.id) } }
        .toMap()
      val existingMembersList = storeDoc().collection("members").get().await()
        .documents.mapNotNull { doc -> doc.toObject(MemberOrder::class.java)?.let { it.copy(id = doc.id) } }
      val existingMembersMap = existingMembersList.associateBy { it.id }
      val existingItemsMap = storeDoc().collection("items").get().await()
        .documents.mapNotNull { doc -> doc.toObject(OrderItem::class.java)?.let { doc.id to it.copy(id = doc.id) } }
        .toMap()

      val goCreateOps = mutableListOf<PreparedBatchOp>()
      val goTotalsUpdateOps = mutableListOf<PreparedBatchOp>()
      val goStatusUpdateOps = mutableListOf<PreparedBatchOp>()
      val memberCreateOps = mutableListOf<PreparedBatchOp>()
      val memberPaymentUpdateOps = mutableListOf<PreparedBatchOp>()
      val memberProfileUpdateOps = mutableListOf<PreparedBatchOp>()
      val memberCostUpdateOps = mutableListOf<PreparedBatchOp>()
      val itemOps = mutableListOf<PreparedBatchOp>()
      val notifOps = mutableListOf<PreparedBatchOp>()
      val deleteOps = mutableListOf<PreparedBatchOp>()

      val goIdsToRemove = existingGosMap.entries.mapNotNull { (goId, go) ->
        val isImportedGo = goId.startsWith("imp_go_") || go.notes.startsWith("Diimpor dari rekapan")
        val isExcludedNovel = go.artistGroup.contains("NOVEL", ignoreCase = true) ||
          go.name.contains("WAR NOVEL", ignoreCase = true)
        val isLegacyGhostEmpty = !goId.startsWith("imp_go_") &&
          go.notes.startsWith("Diimpor dari rekapan") &&
          existingMembersList.none { it.orderId == goId }
        if (isImportedGo && (isExcludedNovel || isLegacyGhostEmpty)) goId else null
      }.toSet()

      if (goIdsToRemove.isNotEmpty()) {
        existingItemsMap.values
          .filter { it.orderId in goIdsToRemove }
          .forEach { deleteOps.add(PreparedBatchOp.DeleteOp(storeDoc().collection("items").document(it.id))) }
        existingMembersList
          .filter { it.orderId in goIdsToRemove }
          .forEach { deleteOps.add(PreparedBatchOp.DeleteOp(storeDoc().collection("members").document(it.id))) }
        goIdsToRemove.forEach { goId ->
          deleteOps.add(PreparedBatchOp.DeleteOp(storeDoc().collection("groupOrders").document(goId)))
        }
      }

      var batchesCreated = 0
      var batchesUpdated = 0
      var membersCreated = 0
      var membersUpdated = 0
      var itemsCreated = 0
      var itemsUpdated = 0

      var firstChangedOrderId = ""
      var firstChangedOrderName = ""

      val seenGoKeys = mutableMapOf<String, Int>()

      parsed.batches.forEachIndexed { idx, batch ->
        val rawGoKey = "${batch.sheetName.trim().lowercase()}|${batch.batchCode.trim().lowercase()}|${batch.batchTitle.trim().lowercase()}"
        val goOccur = seenGoKeys.getOrDefault(rawGoKey, 0)
        seenGoKeys[rawGoKey] = goOccur + 1
        val uniqueGoKey = if (goOccur == 0) rawGoKey else "$rawGoKey#$goOccur"
        val goId = deterministicDocId("imp_go_", uniqueGoKey)
        val goRef = storeDoc().collection("groupOrders").document(goId)
        val existingGo = existingGosMap[goId]

        val closingMillis = now + (idx + 2L) * day
        val deadlineMillis = now + (idx + 1L) * day
        val unpaidCount = batch.unpaidItemsCount
        val goNotes = if (unpaidCount > 0) {
          "Diimpor dari rekapan • $unpaidCount item belum lunas."
        } else {
          "Diimpor dari rekapan • Semua pembayaran telah masuk."
        }

        // Track member aggregates under this GO (preserving any non-sheet manual members)
        val goMembersTotals = existingMembersList
          .filter { it.orderId == goId }
          .associate { it.id to Pair(it.totalItemsCost + it.shippingFee, it.paidAmount) }
          .toMutableMap()

        if (existingGo == null) {
          batchesCreated++
          if (firstChangedOrderId.isEmpty()) {
            firstChangedOrderId = goId
            firstChangedOrderName = batch.batchTitle
          }
          goCreateOps.add(
            PreparedBatchOp.SetOp(
              ref = goRef,
              data = mapOf(
                "userId" to uid,
                "name" to batch.batchTitle.take(120),
                "artistGroup" to batch.artistGroup.ifBlank { "K-Pop Photocard" }.take(60),
                "batchCode" to batch.batchCode.take(40),
                "status" to batch.derivedGoStatus.code,
                "closingDateMillis" to closingMillis,
                "paymentDeadlineMillis" to deadlineMillis,
                "notes" to goNotes.take(500),
                "memberCount" to batch.members.size.toLong(),
                "totalOrderValue" to batch.totalValue,
                "totalPaidAmount" to batch.totalPaid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          )
        }

        val seenMemberKeys = mutableMapOf<String, Int>()
        batch.members.forEach { member ->
          val cleanMemberName = member.memberName.trim().ifBlank { "Pembeli" }.take(80)
          val cleanPhone = member.phoneNumber.trim().take(60)
          val rawMemKey = "$goId|${cleanMemberName.lowercase()}|${cleanPhone.filter { it.isDigit() }}"
          val memOccur = seenMemberKeys.getOrDefault(rawMemKey, 0)
          seenMemberKeys[rawMemKey] = memOccur + 1
          val uniqueMemKey = if (memOccur == 0) rawMemKey else "$rawMemKey#$memOccur"

          val memberId = deterministicDocId("imp_mem_", uniqueMemKey)
          val memberRef = storeDoc().collection("members").document(memberId)
          val existingMem = existingMembersMap[memberId]

          val memberNote = buildString {
            if (member.items.any { it.checkoutDone }) append("Checkout: Ya")
            if (member.items.any { it.isShipped }) {
              if (isNotEmpty()) append(" • ")
              append("Paket dikirim")
            }
            if (member.items.any { it.isReceived }) {
              if (isNotEmpty()) append(" • ")
              append("Paket diterima")
            }
          }.take(300)

          val targetPaidAmount = if (isAutoSync && existingMem != null) {
            maxOf(existingMem.paidAmount, member.paidAmount).coerceAtMost(member.totalCost.coerceAtLeast(existingMem.paidAmount))
          } else {
            member.paidAmount
          }

          val targetStatus = if (
            targetPaidAmount >= member.totalCost &&
            member.totalCost > 0L &&
            member.derivedStatus == MemberOrderStatus.WAITING_PAYMENT
          ) {
            MemberOrderStatus.CONFIRMED.code
          } else {
            member.derivedStatus.code
          }

          val effectiveShipping = existingMem?.shippingFee ?: 0L
          goMembersTotals[memberId] = (member.totalCost + effectiveShipping) to targetPaidAmount

          if (existingMem == null) {
            membersCreated++
            if (firstChangedOrderId.isEmpty()) {
              firstChangedOrderId = goId
              firstChangedOrderName = batch.batchTitle
            }
            memberCreateOps.add(
              PreparedBatchOp.SetOp(
                ref = memberRef,
                data = mapOf(
                  "userId" to uid,
                  "orderId" to goId,
                  "memberName" to cleanMemberName,
                  "memberHandle" to cleanPhone,
                  "isCurrentUser" to false,
                  "shippingFee" to 0L,
                  "totalItemsCost" to member.totalCost,
                  "paidAmount" to targetPaidAmount,
                  "orderStatus" to targetStatus,
                  "lastPaymentMethod" to if (targetPaidAmount > 0L) "Rekapan" else "",
                  "notes" to memberNote,
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            )
          } else {
            var memChanged = false
            val targetPaymentMethod = if (targetPaidAmount > 0L) {
              existingMem.lastPaymentMethod.ifBlank { "Rekapan" }.take(50)
            } else {
              ""
            }
            if (targetPaidAmount != existingMem.paidAmount) {
              memChanged = true
              memberPaymentUpdateOps.add(
                PreparedBatchOp.UpdateOp(
                  ref = memberRef,
                  data = mapOf(
                    "paidAmount" to targetPaidAmount,
                    "lastPaymentMethod" to targetPaymentMethod,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  ),
                )
              )
            }
            if (
              cleanMemberName != existingMem.memberName ||
              cleanPhone != existingMem.memberHandle ||
              targetStatus != existingMem.orderStatus ||
              memberNote != existingMem.notes
            ) {
              memChanged = true
              memberProfileUpdateOps.add(
                PreparedBatchOp.UpdateOp(
                  ref = memberRef,
                  data = mapOf(
                    "memberName" to cleanMemberName,
                    "memberHandle" to cleanPhone,
                    "isCurrentUser" to existingMem.isCurrentUser,
                    "shippingFee" to existingMem.shippingFee,
                    "orderStatus" to targetStatus,
                    "notes" to memberNote,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  ),
                )
              )
            }
            if (member.totalCost != existingMem.totalItemsCost) {
              memChanged = true
              memberCostUpdateOps.add(
                PreparedBatchOp.UpdateOp(
                  ref = memberRef,
                  data = mapOf(
                    "totalItemsCost" to member.totalCost,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  ),
                )
              )
            }
            if (memChanged) {
              membersUpdated++
              if (firstChangedOrderId.isEmpty()) {
                firstChangedOrderId = goId
                firstChangedOrderName = batch.batchTitle
              }
            }
          }

          member.items.forEachIndexed { itemIdx, item ->
            val cleanItemName = item.itemName.trim().ifBlank { "Item Rekapan" }.take(120)
            val cleanVariant = item.variant.trim().take(80)
            val safeQty = item.quantity.coerceIn(1L, 999L)
            val safePrice = item.unitPrice.coerceAtLeast(0L)
            val itemId = deterministicDocId("imp_itm_", "$memberId|$itemIdx|${cleanItemName.lowercase()}")
            val itemRef = storeDoc().collection("items").document(itemId)
            val existingItem = existingItemsMap[itemId]

            if (existingItem == null) {
              itemsCreated++
              if (firstChangedOrderId.isEmpty()) {
                firstChangedOrderId = goId
                firstChangedOrderName = batch.batchTitle
              }
              itemOps.add(
                PreparedBatchOp.SetOp(
                  ref = itemRef,
                  data = mapOf(
                    "userId" to uid,
                    "orderId" to goId,
                    "memberId" to memberId,
                    "itemName" to cleanItemName,
                    "variant" to cleanVariant,
                    "quantity" to safeQty,
                    "unitPrice" to safePrice,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                  ),
                )
              )
            } else if (
              cleanItemName != existingItem.itemName ||
              cleanVariant != existingItem.variant ||
              safeQty != existingItem.quantity ||
              safePrice != existingItem.unitPrice
            ) {
              itemsUpdated++
              if (firstChangedOrderId.isEmpty()) {
                firstChangedOrderId = goId
                firstChangedOrderName = batch.batchTitle
              }
              itemOps.add(
                PreparedBatchOp.UpdateOp(
                  ref = itemRef,
                  data = mapOf(
                    "itemName" to cleanItemName,
                    "variant" to cleanVariant,
                    "quantity" to safeQty,
                    "unitPrice" to safePrice,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  ),
                )
              )
            }
          }
        }

        if (existingGo != null && existingGo.status != GoStatus.COMPLETED.code) {
          val finalCount = goMembersTotals.size.toLong()
          val finalTotalValue = goMembersTotals.values.sumOf { it.first }
          val finalTotalPaid = goMembersTotals.values.sumOf { it.second }
          var goChanged = false

          if (
            finalCount != existingGo.memberCount ||
            finalTotalValue != existingGo.totalOrderValue ||
            finalTotalPaid != existingGo.totalPaidAmount
          ) {
            goChanged = true
            goTotalsUpdateOps.add(
              PreparedBatchOp.UpdateOp(
                ref = goRef,
                data = mapOf(
                  "memberCount" to finalCount,
                  "totalOrderValue" to finalTotalValue,
                  "totalPaidAmount" to finalTotalPaid,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            )
          }
          if (batch.derivedGoStatus.code != existingGo.status) {
            goChanged = true
            goStatusUpdateOps.add(
              PreparedBatchOp.UpdateOp(
                ref = goRef,
                data = mapOf(
                  "status" to batch.derivedGoStatus.code,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            )
          }
          if (goChanged) batchesUpdated++
        }
      }

      val summary = ImportSummary(
        batchesImported = batchesCreated,
        membersImported = membersCreated,
        itemsImported = itemsCreated,
        unpaidItemsCount = parsed.unpaidItemsCount,
        updatedMembersCount = membersUpdated,
        updatedItemsCount = itemsUpdated,
        updatedBatchesCount = batchesUpdated + goIdsToRemove.size,
      )

      if (!summary.hasChanges) {
        return Result.success(summary)
      }

      if (firstChangedOrderId.isNotEmpty()) {
        val isIncremental = batchesCreated == 0 && existingGosMap.isNotEmpty()
        val notifTitle = if (isIncremental) {
          "Sinkronisasi Rekapan (+$membersCreated Pembeli, $membersUpdated Update)"
        } else {
          "Impor Rekapan Berhasil ($batchesCreated Batch Baru)"
        }
        val notifMsg = if (isIncremental) {
          "Update Google Sheets: +$membersCreated pembeli baru, +$itemsCreated item baru, $membersUpdated pembeli & $itemsUpdated item diperbarui."
        } else {
          "Berhasil mengimpor $membersCreated pembeli & $itemsCreated item senilai ${formatIdr(parsed.totalValue)}."
        }
        notifOps.add(
          PreparedBatchOp.SetOp(
            ref = storeDoc().collection("notifications").document(),
            data = mapOf(
              "userId" to uid,
              "orderId" to firstChangedOrderId,
              "orderName" to firstChangedOrderName.ifBlank { "Rekapan JIEGO" }.take(120),
              "memberId" to "",
              "type" to NotificationType.GO_UPDATE.code,
              "title" to notifTitle.take(120),
              "message" to notifMsg.take(400),
              "isRead" to false,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        )
        if (!isIncremental && parsed.unpaidItemsCount > 0) {
          notifOps.add(
            PreparedBatchOp.SetOp(
              ref = storeDoc().collection("notifications").document(),
              data = mapOf(
                "userId" to uid,
                "orderId" to firstChangedOrderId,
                "orderName" to firstChangedOrderName.ifBlank { "Rekapan JIEGO" }.take(120),
                "memberId" to "",
                "type" to NotificationType.PAYMENT_REMINDER.code,
                "title" to "${parsed.unpaidItemsCount} Item Belum Lunas",
                "message" to "Total sisa tagihan dari rekapan yang diimpor: ${formatIdr(parsed.totalRemaining)}.",
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          )
        }
      }

      suspend fun commitChunked(ops: List<PreparedBatchOp>, chunkSize: Int = 200) {
        ops.chunked(chunkSize).forEach { chunk ->
          val batch = db.batch()
          chunk.forEach { op ->
            when (op) {
              is PreparedBatchOp.SetOp -> batch.set(op.ref, op.data)
              is PreparedBatchOp.UpdateOp -> batch.update(op.ref, op.data)
              is PreparedBatchOp.DeleteOp -> batch.delete(op.ref)
            }
          }
          batch.commit().await()
        }
      }

      // Sequential stages ensure parent documents exist before sub-entities check exists() in Firestore Rules,
      // prevent updating the same document twice in one WriteBatch, and stay under Firestore's 20 exists()/batch quota.
      commitChunked(deleteOps, chunkSize = 200)
      commitChunked(goCreateOps, chunkSize = 200)
      commitChunked(goTotalsUpdateOps, chunkSize = 200)
      commitChunked(goStatusUpdateOps, chunkSize = 200)
      commitChunked(memberCreateOps, chunkSize = 10)
      commitChunked(memberPaymentUpdateOps, chunkSize = 200)
      commitChunked(memberProfileUpdateOps, chunkSize = 200)
      commitChunked(memberCostUpdateOps, chunkSize = 200)
      commitChunked(itemOps, chunkSize = 8)
      commitChunked(notifOps, chunkSize = 200)
      Result.success(summary)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "stores/${JiegoAuthConfig.STORE_ID}/groupOrders", auth)
      Result.failure(e)
    }
  }

  private fun deterministicDocId(prefix: String, key: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
    return "$prefix$hex"
  }

  suspend fun addCatalogItem(
    title: String,
    artistGroup: String,
    category: String,
    price: Long,
    dpAmount: Long,
    stock: Long,
    components: String,
    imageKey: String = "",
    imageBase64: String = "",
    notes: String = "",
    emitNotification: Boolean = true,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val safePrice = price.coerceAtLeast(0L)
    val safeDp = dpAmount.coerceIn(0L, safePrice)
    val safeStock = stock.coerceIn(0L, 10_000L)
    val cleanTitle = title.trim().take(120)
    val cleanArtist = artistGroup.trim().ifBlank { "K-Pop" }.take(60)
    val cleanImageBase64 = imageBase64.take(599_000)
    val docRef = storeDoc().collection("catalogItems").document()
    val payload = mapOf(
      "userId" to uid,
      "title" to cleanTitle,
      "artistGroup" to cleanArtist,
      "category" to category.trim().ifBlank { "Merchandise" }.take(40),
      "price" to safePrice,
      "dpAmount" to safeDp,
      "stock" to safeStock,
      "components" to components.trim().take(400),
      "imageKey" to imageKey.trim().take(60),
      "imageBase64" to cleanImageBase64,
      "notes" to notes.trim().take(400),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      if (emitNotification) {
        createNotificationInternal(
          uid = uid,
          orderId = "catalog_store",
          orderName = "Katalog JIEGO",
          memberId = "",
          type = NotificationType.GO_UPDATE,
          title = "Produk Katalog Baru: $cleanTitle",
          message = "$cleanArtist • Harga ${formatIdr(safePrice)} (DP ${formatIdr(safeDp)}) tersedia di Katalog JIEGO.",
        )
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun seedLngshotLightstick(): Result<String> {
    return addCatalogItem(
      title = "LIGHTSTICK LNGSHOT",
      artistGroup = "LNGSHOT",
      category = "Lightstick",
      price = 760_000L,
      dpAmount = 600_000L,
      stock = 1L,
      components = "Package Box | Light Stick | Strap | User Manual | Photocard (4EA, 1SET)",
      imageKey = "lngshot_lightstick",
      imageBase64 = "",
      notes = "Official Light Stick LNGSHOT. Harga penuh Rp 760.000 • Minimal DP Rp 600.000.",
      emitNotification = false,
    )
  }

  suspend fun updateCatalogItemStock(catalogId: String, newStock: Long): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("catalogItems").document(catalogId)
    return try {
      docRef.update(
        mapOf(
          "stock" to newStock.coerceIn(0L, 10_000L),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateCatalogItemPhoto(
    catalogId: String,
    imageKey: String,
    imageBase64: String,
    itemTitle: String = "",
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("catalogItems").document(catalogId)
    return try {
      docRef.update(
        mapOf(
          "imageKey" to imageKey.trim().take(60),
          "imageBase64" to imageBase64.take(599_000),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      if (imageBase64.isNotBlank() || imageKey.isNotBlank()) {
        val label = itemTitle.trim().ifBlank { "Produk Katalog JIEGO" }
        createNotificationInternal(
          uid = uid,
          orderId = "catalog_store",
          orderName = "Katalog JIEGO",
          memberId = "",
          type = NotificationType.PHOTO_UPDATE,
          title = "Foto Produk Diperbarui",
          message = "Owner memperbarui foto untuk \"$label\" di Katalog JIEGO.",
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateCatalogItemDetails(
    item: CatalogItem,
    title: String,
    artistGroup: String,
    category: String,
    price: Long,
    dpAmount: Long,
    stock: Long,
    components: String,
    imageKey: String,
    imageBase64: String,
    notes: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val safePrice = price.coerceAtLeast(0L)
    val safeDp = dpAmount.coerceIn(0L, safePrice)
    val safeStock = stock.coerceIn(0L, 10_000L)
    val cleanTitle = title.trim().take(120)
    val cleanArtist = artistGroup.trim().ifBlank { "K-Pop" }.take(60)
    val docRef = storeDoc().collection("catalogItems").document(item.id)
    return try {
      docRef.update(
        mapOf(
          "title" to cleanTitle,
          "artistGroup" to cleanArtist,
          "category" to category.trim().ifBlank { "Merchandise" }.take(40),
          "price" to safePrice,
          "dpAmount" to safeDp,
          "stock" to safeStock,
          "components" to components.trim().take(400),
          "imageKey" to imageKey.trim().take(60),
          "imageBase64" to imageBase64.take(599_000),
          "notes" to notes.trim().take(400),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      createNotificationInternal(
        uid = uid,
        orderId = "catalog_store",
        orderName = "Katalog JIEGO",
        memberId = "",
        type = NotificationType.GO_UPDATE,
        title = "Update Katalog: $cleanTitle",
        message = "$cleanArtist • Harga ${formatIdr(safePrice)} (Stok $safeStock) diperbarui oleh Owner.",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteCatalogItem(catalogId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = storeDoc().collection("catalogItems").document(catalogId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun placeCatalogOrderIntoGroupOrder(
    catalogItem: CatalogItem,
    targetOrder: GroupOrder?,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val now = System.currentTimeMillis()
    val day = 86_400_000L

    return try {
      val resolvedOrder = if (targetOrder != null) {
        targetOrder
      } else {
        val newOrderId = createGroupOrder(
          name = "PO ${catalogItem.title}",
          artistGroup = catalogItem.artistGroup,
          batchCode = "KATALOG-01",
          closingDateMillis = now + 7 * day,
          paymentDeadlineMillis = now + 5 * day,
          notes = catalogItem.components.ifBlank { catalogItem.notes },
          emitNotification = false,
        ).getOrThrow()
        storeDoc().collection("groupOrders").document(newOrderId).get().await()
          .toObject(GroupOrder::class.java)
          ?: error("Gagal memuat Grup Order katalog.")
      }

      val memberId = addMemberWithItems(
        order = resolvedOrder,
        memberName = memberName,
        memberHandle = memberHandle,
        isCurrentUser = isCurrentUser,
        shippingFee = 0L,
        initialPaidAmount = initialPaidAmount,
        paymentMethod = paymentMethod,
        notes = notes,
        items = listOf(
          DraftOrderItem(
            itemName = catalogItem.title,
            variant = catalogItem.components.take(80),
            quantity = 1L,
            unitPrice = catalogItem.price,
          )
        ),
      ).getOrThrow()

      if (catalogItem.stock > 0L) {
        updateCatalogItemStock(catalogItem.id, (catalogItem.stock - 1L).coerceAtLeast(0L)).getOrThrow()
      }

      Result.success(memberId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "stores/${JiegoAuthConfig.STORE_ID}/catalogItems", auth)
      Result.failure(e)
    }
  }

  private suspend fun recalculateMemberAndOrderTotals(uid: String, orderId: String, memberId: String) {
    val itemsSnap = storeDoc().collection("items")
      .whereEqualTo("memberId", memberId)
      .get()
      .await()
    val items = itemsSnap.toObjects(OrderItem::class.java)
    val totalItemsCost = items.sumOf { it.quantity * it.unitPrice }
    storeDoc().collection("members").document(memberId)
      .update(
        mapOf(
          "totalItemsCost" to totalItemsCost,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
    recalculateGroupOrderTotals(uid, orderId)
  }

  private suspend fun recalculateGroupOrderTotals(uid: String, orderId: String) {
    val goSnap = storeDoc().collection("groupOrders").document(orderId).get().await()
    if (!goSnap.exists()) return
    val status = goSnap.getString("status") ?: GoStatus.OPEN.code
    if (status == GoStatus.COMPLETED.code) return

    val membersSnap = storeDoc().collection("members")
      .whereEqualTo("orderId", orderId)
      .get()
      .await()
    val members = membersSnap.toObjects(MemberOrder::class.java)
    val memberCount = members.size.toLong()
    val totalOrderValue = members.sumOf { it.totalItemsCost + it.shippingFee }
    val totalPaidAmount = members.sumOf { it.paidAmount }

    storeDoc().collection("groupOrders").document(orderId)
      .update(
        mapOf(
          "memberCount" to memberCount,
          "totalOrderValue" to totalOrderValue,
          "totalPaidAmount" to totalPaidAmount,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun registerNotificationSubscriber(
    email: String,
    displayName: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanEmail = email.trim().take(160)
    if (cleanEmail.length < 3 || !cleanEmail.contains("@")) {
      return Result.success(Unit)
    }
    val docRef = storeDoc().collection("subscribers").document(uid)
    val cleanName = displayName.trim().take(80)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "email" to cleanEmail,
            "displayName" to cleanName,
            "platform" to "ANDROID",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "email" to cleanEmail,
            "displayName" to cleanName,
            "platform" to "ANDROID",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  fun observeNotificationSubscribers(userId: String): Flow<List<NotificationSubscriber>> = flow {
    val path = "stores/${JiegoAuthConfig.STORE_ID}/subscribers"
    emitAll(
      storeDoc().collection("subscribers")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(NotificationSubscriber::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
        .flowOn(Dispatchers.Default)
    )
  }

  suspend fun broadcastOwnerAnnouncement(
    title: String,
    message: String,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanTitle = title.trim().take(120)
    val cleanMsg = message.trim().take(400)
    if (cleanTitle.isEmpty() || cleanMsg.isEmpty()) {
      return Result.failure(IllegalArgumentException("Judul dan isi pengumuman wajib diisi."))
    }
    val path = "stores/${JiegoAuthConfig.STORE_ID}/notifications"
    return try {
      val notifId = createNotificationInternal(
        uid = uid,
        orderId = "store_broadcast",
        orderName = "Pengumuman Toko JIEGO",
        memberId = "",
        type = NotificationType.GO_UPDATE,
        title = cleanTitle,
        message = cleanMsg,
      )
      Result.success(notifId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  private suspend fun createNotificationInternal(
    uid: String,
    orderId: String,
    orderName: String,
    memberId: String,
    type: NotificationType,
    title: String,
    message: String,
  ): String {
    val ref = storeDoc().collection("notifications").document()
    ref.set(
      mapOf(
        "userId" to uid,
        "orderId" to orderId,
        "orderName" to orderName.take(120),
        "memberId" to memberId,
        "type" to type.code,
        "title" to title.take(120),
        "message" to message.take(400),
        "isRead" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()
    return ref.id
  }

  private sealed class PreparedBatchOp {
    data class SetOp(val ref: DocumentReference, val data: Map<String, Any>) : PreparedBatchOp()
    data class UpdateOp(val ref: DocumentReference, val data: Map<String, Any>) : PreparedBatchOp()
    data class DeleteOp(val ref: DocumentReference) : PreparedBatchOp()
  }

  companion object {
    fun formatIdr(amount: Long): String {
      val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
      return "Rp ${formatter.format(amount)}"
    }
  }
}
