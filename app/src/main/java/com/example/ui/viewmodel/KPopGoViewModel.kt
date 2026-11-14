package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importer.GoogleSheetsFetcher
import com.example.data.importer.ParsedRekapanResult
import com.example.data.model.CatalogItem
import com.example.data.model.ExchangeRates
import com.example.data.model.GoNotification
import com.example.data.model.GoStatus
import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.MemberOrderStatus
import com.example.data.model.NotificationSubscriber
import com.example.data.model.OrderItem
import com.example.data.model.PhotoCategory
import com.example.data.model.PhotoUpdate
import com.example.data.repository.DraftOrderItem
import com.example.data.repository.GroupOrderRepository
import com.example.data.repository.ImportSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class DeadlineAlert(
  val order: GroupOrder,
  val unpaidMembersCount: Int,
  val totalRemainingAmount: Long,
  val isOverdue: Boolean,
  val daysRemaining: Long,
)

data class KPopGoData(
  val groupOrders: List<GroupOrder> = emptyList(),
  val members: List<MemberOrder> = emptyList(),
  val items: List<OrderItem> = emptyList(),
  val notifications: List<GoNotification> = emptyList(),
  val catalogItems: List<CatalogItem> = emptyList(),
  val exchangeRates: ExchangeRates = ExchangeRates(),
  val photoUpdates: List<PhotoUpdate> = emptyList(),
  val subscribers: List<NotificationSubscriber> = emptyList(),
) {
  val activeGroupOrders: List<GroupOrder>
    get() = groupOrders.filter { it.statusEnum != GoStatus.COMPLETED }

  val totalActiveOrderValue: Long
    get() = activeGroupOrders.sumOf { it.totalOrderValue }

  val totalActivePaidAmount: Long
    get() = activeGroupOrders.sumOf { it.totalPaidAmount }

  val totalActiveRemainingAmount: Long
    get() = (totalActiveOrderValue - totalActivePaidAmount).coerceAtLeast(0L)

  val myOrders: List<MemberOrder>
    get() = members.filter { it.isCurrentUser }

  val myTotalCost: Long
    get() = myOrders.sumOf { it.totalCost }

  val myTotalPaid: Long
    get() = myOrders.sumOf { it.paidAmount }

  val myTotalRemaining: Long
    get() = myOrders.sumOf { it.remainingBalance }

  val unreadNotificationsCount: Int
    get() = notifications.count { !it.isRead }

  fun membersForOrder(orderId: String): List<MemberOrder> =
    members.filter { it.orderId == orderId }

  fun itemsForMember(memberId: String): List<OrderItem> =
    items.filter { it.memberId == memberId }

  fun computeDeadlineAlerts(nowMillis: Long = System.currentTimeMillis()): List<DeadlineAlert> {
    val threeDaysMs = 3L * 86_400_000L
    val membersByOrder = members.groupBy { it.orderId }
    return activeGroupOrders.mapNotNull { order ->
      val orderMembers = membersByOrder[order.id].orEmpty()
      val unpaidMembers = orderMembers.filter { it.remainingBalance > 0L }
      if (unpaidMembers.isEmpty()) return@mapNotNull null

      val diffMs = order.paymentDeadlineMillis - nowMillis
      if (diffMs > threeDaysMs) return@mapNotNull null

      val days = diffMs / 86_400_000L
      DeadlineAlert(
        order = order,
        unpaidMembersCount = unpaidMembers.size,
        totalRemainingAmount = unpaidMembers.sumOf { it.remainingBalance },
        isOverdue = diffMs < 0L,
        daysRemaining = days,
      )
    }.sortedBy { it.order.paymentDeadlineMillis }
  }
}

sealed interface UiEvent {
  data class ShowMessage(val message: String) : UiEvent
  data class TriggerLocalNotification(val title: String, val body: String) : UiEvent
}

class KPopGoViewModel(
  private val repository: GroupOrderRepository,
  private val currentUserId: String,
  private val isOwnerAccount: Boolean = false,
) : ViewModel() {

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
  val events: SharedFlow<UiEvent> = _events.asSharedFlow()

  private val subscribersFlow = if (isOwnerAccount) {
    repository.observeNotificationSubscribers(currentUserId)
  } else {
    flowOf(emptyList())
  }

  val uiState: StateFlow<UiState<KPopGoData>> = combine(
    combine(
      repository.observeGroupOrders(currentUserId),
      repository.observeMembers(currentUserId),
      repository.observeItems(currentUserId),
    ) { orders, members, items -> Triple(orders, members, items) },
    combine(
      repository.observeNotifications(currentUserId),
      repository.observeCatalogItems(currentUserId),
      repository.observeExchangeRates(currentUserId),
      repository.observePhotoUpdates(currentUserId),
      subscribersFlow,
    ) { notifications, catalogItems, exchangeRates, photoUpdates, subscribers ->
      SecondGroupState(notifications, catalogItems, exchangeRates, photoUpdates, subscribers)
    },
  ) { firstThree, secondGroup ->
    UiState.Success(
      KPopGoData(
        groupOrders = firstThree.first,
        members = firstThree.second,
        items = firstThree.third,
        notifications = secondGroup.notifications,
        catalogItems = secondGroup.catalogItems,
        exchangeRates = secondGroup.exchangeRates,
        photoUpdates = secondGroup.photoUpdates,
        subscribers = secondGroup.subscribers,
      )
    ) as UiState<KPopGoData>
  }.flowOn(Dispatchers.Default)
  .catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Error observing KPopGO data", rootError)
    emit(UiState.Error(rootError.message ?: "Gagal memuat data grup order."))
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    initialValue = UiState.Loading,
  )

  fun createGroupOrder(
    name: String,
    artistGroup: String,
    batchCode: String,
    closingDateMillis: Long,
    paymentDeadlineMillis: Long,
    notes: String,
  ) {
    launchBusy {
      repository.createGroupOrder(
        name = name,
        artistGroup = artistGroup,
        batchCode = batchCode,
        closingDateMillis = closingDateMillis,
        paymentDeadlineMillis = paymentDeadlineMillis,
        notes = notes,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Grup order \"$name\" berhasil dibuat."))
        _events.tryEmit(
          UiEvent.TriggerLocalNotification(
            title = "Grup Order Dibuka",
            body = "$name ($artistGroup) siap menerima pesanan.",
          )
        )
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal membuat grup order."))
      }
    }
  }

  fun updateGroupOrderInfo(
    order: GroupOrder,
    name: String,
    artistGroup: String,
    batchCode: String,
    closingDateMillis: Long,
    paymentDeadlineMillis: Long,
    notes: String,
  ) {
    launchBusy {
      repository.updateGroupOrderInfo(
        order = order,
        name = name,
        artistGroup = artistGroup,
        batchCode = batchCode,
        closingDateMillis = closingDateMillis,
        paymentDeadlineMillis = paymentDeadlineMillis,
        notes = notes,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Informasi grup order diperbarui & notifikasi dikirim."))
        _events.tryEmit(
          UiEvent.TriggerLocalNotification(
            title = "Info Grup Order Berubah",
            body = "Detail atau jadwal untuk $name telah diperbarui.",
          )
        )
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memperbarui grup order."))
      }
    }
  }

  fun updateGroupOrderStatus(order: GroupOrder, newStatus: GoStatus) {
    launchBusy {
      repository.updateGroupOrderStatus(order, newStatus)
        .onSuccess {
          _events.tryEmit(UiEvent.ShowMessage("Status GO diubah ke ${newStatus.labelId}."))
          _events.tryEmit(
            UiEvent.TriggerLocalNotification(
              title = "Status GO: ${newStatus.labelId}",
              body = "Pesanan ${order.name} kini berstatus ${newStatus.labelId}.",
            )
          )
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengubah status GO."))
        }
    }
  }

  fun deleteGroupOrder(orderId: String, onDeleted: () -> Unit) {
    launchBusy {
      repository.deleteGroupOrder(orderId)
        .onSuccess {
          _events.tryEmit(UiEvent.ShowMessage("Grup order dihapus."))
          onDeleted()
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menghapus grup order."))
        }
    }
  }

  fun addMemberWithItems(
    order: GroupOrder,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
    items: List<DraftOrderItem>,
  ) {
    launchBusy {
      repository.addMemberWithItems(
        order = order,
        memberName = memberName,
        memberHandle = memberHandle,
        isCurrentUser = isCurrentUser,
        shippingFee = shippingFee,
        initialPaidAmount = initialPaidAmount,
        paymentMethod = paymentMethod,
        notes = notes,
        items = items,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Anggota $memberName berhasil ditambahkan."))
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menambahkan anggota."))
      }
    }
  }

  fun addOrderItem(
    orderId: String,
    memberId: String,
    itemName: String,
    variant: String,
    quantity: Long,
    unitPrice: Long,
  ) {
    launchBusy {
      repository.addOrderItem(orderId, memberId, itemName, variant, quantity, unitPrice)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Item \"$itemName\" ditambahkan.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menambah item.")) }
    }
  }

  fun deleteOrderItem(orderId: String, memberId: String, itemId: String) {
    launchBusy {
      repository.deleteOrderItem(orderId, memberId, itemId)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Item dihapus dari rincian.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menghapus item.")) }
    }
  }

  fun recordMemberPayment(
    orderName: String,
    member: MemberOrder,
    newPaidAmount: Long,
    paymentMethod: String,
  ) {
    launchBusy {
      repository.recordMemberPayment(orderName, member, newPaidAmount, paymentMethod)
        .onSuccess {
          val remaining = (member.totalCost - newPaidAmount).coerceAtLeast(0L)
          val msg = if (remaining == 0L && member.totalCost > 0L) {
            "Tagihan ${member.memberName} ditandai LUNAS!"
          } else {
            "Pembayaran ${member.memberName} diperbarui."
          }
          _events.tryEmit(UiEvent.ShowMessage(msg))
          _events.tryEmit(
            UiEvent.TriggerLocalNotification(
              title = if (remaining == 0L) "Tagihan Lunas" else "Pembayaran Dicatat",
              body = "$msg ($orderName)",
            )
          )
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mencatat pembayaran."))
        }
    }
  }

  fun updateMemberDetailsAndStatus(
    orderName: String,
    member: MemberOrder,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    shippingFee: Long,
    newStatus: MemberOrderStatus,
    notes: String,
  ) {
    launchBusy {
      repository.updateMemberDetailsAndStatus(
        orderName = orderName,
        member = member,
        memberName = memberName,
        memberHandle = memberHandle,
        isCurrentUser = isCurrentUser,
        shippingFee = shippingFee,
        newStatus = newStatus,
        notes = notes,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Data pesanan ${member.memberName} diperbarui."))
        if (member.orderStatus != newStatus.code) {
          _events.tryEmit(
            UiEvent.TriggerLocalNotification(
              title = newStatus.labelId,
              body = "Pesanan $memberName di $orderName: ${newStatus.labelId}.",
            )
          )
        }
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memperbarui anggota."))
      }
    }
  }

  fun sendPaymentReminder(order: GroupOrder, member: MemberOrder) {
    launchBusy {
      repository.sendPaymentReminder(order, member)
        .onSuccess {
          _events.tryEmit(UiEvent.ShowMessage("Pengingat pembayaran dikirim untuk ${member.memberName}."))
          _events.tryEmit(
            UiEvent.TriggerLocalNotification(
              title = "Pengingat Tenggat Pembayaran",
              body = "${member.memberName}: sisa tagihan ${GroupOrderRepository.formatIdr(member.remainingBalance)} untuk ${order.name}.",
            )
          )
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengirim pengingat."))
        }
    }
  }

  fun markNotificationRead(notificationId: String, isRead: Boolean) {
    viewModelScope.launch {
      repository.markNotificationRead(notificationId, isRead)
    }
  }

  fun markAllNotificationsRead(notifications: List<GoNotification>) {
    val unreadIds = notifications.filter { !it.isRead }.map { it.id }
    if (unreadIds.isEmpty()) return
    viewModelScope.launch {
      repository.markAllNotificationsRead(unreadIds)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Semua notifikasi ditandai sudah dibaca.")) }
    }
  }

  fun seedSampleKPopData() {
    launchBusy {
      repository.seedSampleKPopData()
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Contoh data grup order K-Pop berhasil dimuat!")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memuat contoh data.")) }
    }
  }

  fun importRekapanResult(parsed: ParsedRekapanResult) {
    launchBusy {
      repository.importRekapanResult(parsed, isAutoSync = false)
        .onSuccess { summary ->
          emitSyncSummaryFeedback(summary, parsed.sheetNames, isAutoSync = false)
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengimpor data rekapan."))
        }
    }
  }

  fun syncGoogleSheets(
    sheetUrl: String = GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL,
    isAutoSync: Boolean = false,
  ) {
    if (sheetUrl.isBlank()) return
    val block: suspend () -> Unit = {
      runCatching {
        val parsed = GoogleSheetsFetcher.fetchAndParse(sheetUrl)
        val summary = repository.importRekapanResult(parsed, isAutoSync = isAutoSync).getOrThrow()
        parsed to summary
      }.onSuccess { (parsed, summary) ->
        emitSyncSummaryFeedback(summary, parsed.sheetNames, isAutoSync = isAutoSync)
      }.onFailure { err ->
        if (!isAutoSync) {
          _events.tryEmit(
            UiEvent.ShowMessage(err.localizedMessage ?: "Gagal menyinkronkan Google Sheets.")
          )
        } else {
          Log.w("KPopGoViewModel", "Background Google Sheets sync skipped: ${err.message}")
        }
      }
    }
    if (isAutoSync) {
      viewModelScope.launch { block() }
    } else {
      launchBusy { block() }
    }
  }

  private fun emitSyncSummaryFeedback(
    summary: ImportSummary,
    sheetNames: List<String>,
    isAutoSync: Boolean,
  ) {
    if (!summary.hasChanges) {
      if (!isAutoSync) {
        _events.tryEmit(UiEvent.ShowMessage("Rekapan sudah paling baru (tidak ada penambahan/perubahan)."))
      }
      return
    }
    val sheetInfo = if (sheetNames.size > 1) {
      " (${sheetNames.size} sheet)"
    } else ""

    val msg = if (summary.batchesImported > 0) {
      val updatePart = if (summary.updatedMembersCount > 0 || summary.updatedItemsCount > 0) {
        ", ${summary.updatedMembersCount} pembeli diperbarui"
      } else ""
      "Sinkronisasi selesai$sheetInfo: +${summary.batchesImported} batch, +${summary.membersImported} pembeli, +${summary.itemsImported} item$updatePart."
    } else {
      "Update Google Sheets$sheetInfo: +${summary.membersImported} pembeli baru, +${summary.itemsImported} item baru, ${summary.updatedMembersCount} pembeli & ${summary.updatedItemsCount} item diperbarui."
    }
    _events.tryEmit(UiEvent.ShowMessage(msg))
    _events.tryEmit(
      UiEvent.TriggerLocalNotification(
        title = if (summary.batchesImported > 0) "Rekapan Disinkronkan" else "Update Baru dari Google Sheets",
        body = msg,
      )
    )
  }

  fun addCatalogItem(
    title: String,
    artistGroup: String,
    category: String,
    price: Long,
    dpAmount: Long,
    stock: Long,
    components: String,
    imageBase64: String,
    notes: String,
  ) {
    launchBusy {
      val resolvedImageKey = if (imageBase64.isBlank() && title.contains("LNGSHOT", ignoreCase = true)) {
        "lngshot_lightstick"
      } else ""
      repository.addCatalogItem(
        title = title,
        artistGroup = artistGroup,
        category = category,
        price = price,
        dpAmount = dpAmount,
        stock = stock,
        components = components,
        imageKey = resolvedImageKey,
        imageBase64 = imageBase64,
        notes = notes,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Produk \"$title\" ditambahkan ke katalog JIEGO."))
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menambah produk katalog."))
      }
    }
  }

  fun seedLngshotLightstick(silent: Boolean = false) {
    launchBusy {
      repository.seedLngshotLightstick()
        .onSuccess {
          if (!silent) {
            _events.tryEmit(UiEvent.ShowMessage("Katalog LIGHTSTICK LNGSHOT ditambahkan."))
          }
        }
        .onFailure {
          if (!silent) {
            _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memuat katalog LNGSHOT."))
          }
        }
    }
  }

  fun updateCatalogItemStock(catalogId: String, newStock: Long) {
    launchBusy {
      repository.updateCatalogItemStock(catalogId, newStock)
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengubah stok.")) }
    }
  }

  fun updateCatalogItemPhoto(
    item: CatalogItem,
    imageKey: String,
    imageBase64: String,
  ) {
    launchBusy {
      repository.updateCatalogItemPhoto(item.id, imageKey, imageBase64, item.title)
        .onSuccess {
          val msg = if (imageBase64.isBlank() && imageKey.isBlank()) {
            "Foto produk \"${item.title}\" dihapus."
          } else {
            "Foto produk \"${item.title}\" berhasil diperbarui & notifikasi dikirim."
          }
          _events.tryEmit(UiEvent.ShowMessage(msg))
        }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengubah foto produk.")) }
    }
  }

  fun updateCatalogItemDetails(
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
  ) {
    launchBusy {
      repository.updateCatalogItemDetails(item, title, artistGroup, category, price, dpAmount, stock, components, imageKey, imageBase64, notes)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Katalog \"$title\" diperbarui.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memperbarui katalog.")) }
    }
  }

  fun deleteCatalogItem(catalogId: String) {
    launchBusy {
      repository.deleteCatalogItem(catalogId)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Produk dihapus dari katalog.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menghapus produk.")) }
    }
  }

  fun placeCatalogOrderIntoGroupOrder(
    catalogItem: CatalogItem,
    targetOrder: GroupOrder?,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
  ) {
    launchBusy {
      repository.placeCatalogOrderIntoGroupOrder(
        catalogItem = catalogItem,
        targetOrder = targetOrder,
        memberName = memberName,
        memberHandle = memberHandle,
        isCurrentUser = isCurrentUser,
        initialPaidAmount = initialPaidAmount,
        paymentMethod = paymentMethod,
        notes = notes,
      ).onSuccess {
        _events.tryEmit(UiEvent.ShowMessage("Pesanan ${catalogItem.title} untuk $memberName berhasil dicatat!"))
        _events.tryEmit(
          UiEvent.TriggerLocalNotification(
            title = "Pesanan Katalog Baru",
            body = "$memberName memesan ${catalogItem.title} (Bayar: ${GroupOrderRepository.formatIdr(initialPaidAmount)}).",
          )
        )
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mencatat pesanan katalog."))
      }
    }
  }

  fun saveExchangeRates(rates: ExchangeRates) {
    launchBusy {
      repository.saveExchangeRates(rates)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Rate kurs JIEGO berhasil disimpan.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menyimpan rate kurs.")) }
    }
  }

  fun resetExchangeRatesToDefault() {
    saveExchangeRates(ExchangeRates())
  }

  fun addPhotoUpdate(
    category: PhotoCategory,
    uploaderRole: String,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ) {
    launchBusy {
      repository.addPhotoUpdate(
        category = category,
        uploaderRole = uploaderRole,
        uploaderName = uploaderName,
        uploaderPhone = uploaderPhone,
        orderId = orderId,
        orderName = orderName,
        caption = caption,
        imageBase64 = imageBase64,
      ).onSuccess { (_, notifPair) ->
        _events.tryEmit(UiEvent.ShowMessage("Foto ${category.shortLabel} berhasil diunggah & notifikasi terkirim."))
        _events.tryEmit(
          UiEvent.TriggerLocalNotification(
            title = notifPair.first,
            body = notifPair.second,
          )
        )
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengunggah foto."))
      }
    }
  }

  fun updatePhotoUpdate(
    existing: PhotoUpdate,
    category: PhotoCategory,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ) {
    launchBusy {
      repository.updatePhotoUpdate(
        existing = existing,
        category = category,
        uploaderName = uploaderName,
        uploaderPhone = uploaderPhone,
        orderId = orderId,
        orderName = orderName,
        caption = caption,
        imageBase64 = imageBase64,
      ).onSuccess { notifPair ->
        _events.tryEmit(UiEvent.ShowMessage("Foto ${category.shortLabel} diperbarui & notifikasi terkirim."))
        _events.tryEmit(
          UiEvent.TriggerLocalNotification(
            title = notifPair.first,
            body = notifPair.second,
          )
        )
      }.onFailure {
        _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal memperbarui foto."))
      }
    }
  }

  fun deletePhotoUpdate(photoId: String) {
    launchBusy {
      repository.deletePhotoUpdate(photoId)
        .onSuccess { _events.tryEmit(UiEvent.ShowMessage("Foto dihapus dari galeri.")) }
        .onFailure { _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal menghapus foto.")) }
    }
  }

  fun registerNotificationSubscriber(email: String, displayName: String) {
    if (email.isBlank()) return
    viewModelScope.launch {
      repository.registerNotificationSubscriber(email, displayName)
        .onFailure { error ->
          Log.w(TAG, "Failed to register subscriber email: ${error.message}")
        }
    }
  }

  fun sendOwnerAnnouncement(title: String, message: String) {
    launchBusy {
      repository.broadcastOwnerAnnouncement(title, message)
        .onSuccess {
          _events.tryEmit(UiEvent.ShowMessage("Pengumuman terkirim ke seluruh pembeli."))
          _events.tryEmit(
            UiEvent.TriggerLocalNotification(
              title = title.trim(),
              body = message.trim(),
            )
          )
        }
        .onFailure {
          _events.tryEmit(UiEvent.ShowMessage(it.localizedMessage ?: "Gagal mengirim pengumuman."))
        }
    }
  }

  private fun launchBusy(block: suspend () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        block()
      } finally {
        _isBusy.value = false
      }
    }
  }

  private data class SecondGroupState(
    val notifications: List<GoNotification>,
    val catalogItems: List<CatalogItem>,
    val exchangeRates: ExchangeRates,
    val photoUpdates: List<PhotoUpdate>,
    val subscribers: List<NotificationSubscriber>,
  )

  private companion object {
    const val TAG = "KPopGoVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
