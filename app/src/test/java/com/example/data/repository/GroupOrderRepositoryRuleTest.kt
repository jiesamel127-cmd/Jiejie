package com.example.data.repository

import com.example.auth.JiegoAuthConfig
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.importer.RekapanCsvParser
import com.example.data.importer.RekapanSheetInput
import com.example.data.importer.XlsxWorkbookParser
import com.example.data.model.ExchangeRates
import com.example.data.model.GoStatus
import com.example.data.model.NotificationType
import com.example.data.model.PaymentStatus
import com.example.data.model.PhotoCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class GroupOrderRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createGroupOrder_and_addMemberWithItems_updatesAggregatesAndNotifications() = runBlocking {
    val uid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)

    val now = System.currentTimeMillis()
    val orderId = withTimeout(TIMEOUT_MS) {
      repo.createGroupOrder(
        name = "PO SEVENTEEN Test",
        artistGroup = "SEVENTEEN",
        batchCode = "B1",
        closingDateMillis = now + 86_400_000L,
        paymentDeadlineMillis = now + 172_800_000L,
        notes = "Test note",
      ).getOrThrow()
    }

    val createdOrder = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(uid).first { list -> list.any { it.id == orderId } }
        .first { it.id == orderId }
    }

    val memberId = withTimeout(TIMEOUT_MS) {
      repo.addMemberWithItems(
        order = createdOrder,
        memberName = "Sinta",
        memberHandle = "@sinta",
        isCurrentUser = true,
        shippingFee = 25_000L,
        initialPaidAmount = 100_000L,
        paymentMethod = "QRIS",
        notes = "",
        items = listOf(DraftOrderItem("Album", "Ver A", 2L, 150_000L)),
      ).getOrThrow()
    }

    val updatedOrder = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(uid).first { list ->
        val found = list.find { it.id == orderId }
        found != null && found.memberCount == 1L && found.totalOrderValue == 325_000L
      }.first { it.id == orderId }
    }
    assertEquals(100_000L, updatedOrder.totalPaidAmount)
    assertEquals(225_000L, updatedOrder.remainingOrderBalance)

    val member = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(uid).first { list -> list.any { it.id == memberId } }
        .first { it.id == memberId }
    }
    assertEquals(PaymentStatus.DP, member.paymentStatus)
    assertEquals(225_000L, member.remainingBalance)

    // Record full payment as owner jiego4sho@gmail.com
    withTimeout(TIMEOUT_MS) {
      repo.recordMemberPayment(updatedOrder.name, member, 325_000L, "Transfer BCA").getOrThrow()
    }

    val paidMember = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(uid).first { list ->
        list.any { it.id == memberId && it.paidAmount == 325_000L }
      }.first { it.id == memberId }
    }
    assertEquals(PaymentStatus.LUNAS, paidMember.paymentStatus)
    assertEquals(0L, paidMember.remainingBalance)

    // Send reminder and update GO status
    withTimeout(TIMEOUT_MS) {
      repo.sendPaymentReminder(updatedOrder, paidMember).getOrThrow()
      repo.updateGroupOrderStatus(updatedOrder, GoStatus.SHIPPED).getOrThrow()
    }

    val notifications = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotifications(uid).first { it.size >= 4 }
    }
    assertTrue(notifications.isNotEmpty())
  }

  @Test
  fun observeGroupOrders_unauthenticated_throwsPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = GroupOrderRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupOrders("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED when unauthenticated")
    } catch (e: Exception) {
      var cur: Throwable? = e
      var firestoreEx: FirebaseFirestoreException? = null
      while (cur != null) {
        if (cur is FirebaseFirestoreException) {
          firestoreEx = cur
          break
        }
        cur = cur.cause
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun nonOwnerEmail_canReadStoreData_butCannotModifyOwnerOrdersOrRates() = runBlocking {
    val ownerUid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)
    val now = System.currentTimeMillis()
    val orderId = withTimeout(TIMEOUT_MS) {
      repo.createGroupOrder("Owner GO", "AESPA", "B1", now + 10000L, now + 20000L, "").getOrThrow()
    }
    val createdOrder = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(ownerUid).first { it.any { o -> o.id == orderId } }.first { it.id == orderId }
    }

    val buyerUid = signInTestUser("pembeli-bukan-owner@test.com")
    // Buyer can read owner's GOs
    val visibleOrders = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(buyerUid).first { it.any { o -> o.id == orderId } }
    }
    assertTrue(visibleOrders.any { it.id == orderId })

    // Buyer CANNOT delete GO, update GO status, or overwrite exchange rates
    val deleteResult = repo.deleteGroupOrder(orderId)
    assertTrue(deleteResult.isFailure)

    val statusResult = repo.updateGroupOrderStatus(createdOrder, GoStatus.SHIPPED)
    assertTrue(statusResult.isFailure)

    val ratesResult = repo.saveExchangeRates(ExchangeRates(china = 9999.0))
    assertTrue(ratesResult.isFailure)
  }

  @Test
  fun importRekapanResult_importsMultiBatchCsvThroughSecurityRules() = runBlocking {
    val uid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    clearImportedStoreDocs()
    val repo = GroupOrderRepository(firestore, auth)

    val csvSample = """
      DAFTAR LIST WAR BATCH 1,,,,
      NO,FOTO PC,NAMA,NOMOR HANDPHONE,HARGA,STATUS
      1,,yunia,85175452490,"Rp152,000",Done Packing
      2,,yunia,85175452490,"Rp80,000",Done Packing
      3,,"ritha, RBUY!",6285792440587,"Rp120,000",Done Packing
      DAFTAR LIST WAR BATCH 2,,,,
      NO,FOTO PC,NAMA,NOMOR HANDPHONE,HARGA,STATUS
      1,,elan,6281234567890,"Rp155,000",not payment yet
    """.trimIndent()

    val parsed = RekapanCsvParser.parse(csvSample)
    val summary = withTimeout(TIMEOUT_MS) {
      repo.importRekapanResult(parsed).getOrThrow()
    }

    assertEquals(2, summary.batchesImported)
    assertEquals(3, summary.membersImported)
    assertEquals(4, summary.itemsImported)
    assertEquals(1, summary.unpaidItemsCount)

    val orders = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(uid).first { it.size >= 2 }
    }
    val members = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(uid).first { it.size >= 3 }
    }
    val items = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeItems(uid).first { it.size >= 4 }
    }
    val notifications = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotifications(uid).first { it.isNotEmpty() }
    }

    assertTrue(orders.size >= 2)
    assertTrue(members.size >= 3)
    assertTrue(items.size >= 4)
    assertTrue(notifications.isNotEmpty())

    val yunia = members.first { it.memberName == "yunia" }
    assertEquals(232_000L, yunia.totalCost)
    assertEquals(0L, yunia.remainingBalance)
    assertEquals(PaymentStatus.LUNAS, yunia.paymentStatus)

    val elan = members.first { it.memberName == "elan" }
    assertEquals(155_000L, elan.totalCost)
    assertEquals(155_000L, elan.remainingBalance)
    assertEquals(PaymentStatus.BELUM_BAYAR, elan.paymentStatus)
  }

  @Test
  fun importRekapanResult_reimportWithUpdates_upsertsWithoutDuplicatesThroughSecurityRules() = runBlocking {
    val uid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    clearImportedStoreDocs()
    val repo = GroupOrderRepository(firestore, auth)

    val initialCsv = """
      DAFTAR LIST WAR BATCH 99,,,,
      NO,FOTO PC,NAMA,NOMOR HANDPHONE,HARGA,STATUS
      1,PC Wonwoo,yunia,85175452490,"Rp150,000",not payment yet
    """.trimIndent()

    val firstSummary = withTimeout(TIMEOUT_MS) {
      repo.importRekapanResult(RekapanCsvParser.parse(initialCsv)).getOrThrow()
    }
    assertEquals(1, firstSummary.batchesImported)
    assertEquals(1, firstSummary.membersImported)
    assertEquals(1, firstSummary.itemsImported)

    // Owner updates yunia to done payment and adds a new buyer nadia in Google Sheets
    val updatedCsv = """
      DAFTAR LIST WAR BATCH 99,,,,
      NO,FOTO PC,NAMA,NOMOR HANDPHONE,HARGA,STATUS
      1,PC Wonwoo,yunia,85175452490,"Rp150,000",done payment
      2,PC Mingyu,nadia,81234567890,"Rp85,000",not payment yet
    """.trimIndent()

    val syncSummary = withTimeout(TIMEOUT_MS) {
      repo.importRekapanResult(RekapanCsvParser.parse(updatedCsv), isAutoSync = true).getOrThrow()
    }
    assertEquals(0, syncSummary.batchesImported)
    assertEquals(1, syncSummary.membersImported)
    assertEquals(1, syncSummary.itemsImported)
    assertEquals(1, syncSummary.updatedMembersCount)

    val batchOrders = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(uid).first { list -> list.any { it.batchCode == "BATCH-99" } }
    }.filter { it.batchCode == "BATCH-99" }
    assertEquals(1, batchOrders.size)
    val order = batchOrders.first()
    assertEquals(2L, order.memberCount)
    assertEquals(235_000L, order.totalOrderValue)
    assertEquals(150_000L, order.totalPaidAmount)

    val members = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(uid).first { list -> list.count { it.orderId == order.id } == 2 }
    }.filter { it.orderId == order.id }
    val yunia = members.first { it.memberName == "yunia" }
    assertEquals(PaymentStatus.LUNAS, yunia.paymentStatus)
    assertEquals(150_000L, yunia.paidAmount)

    // Third sync with identical sheet -> 0 changes, 0 duplicate items
    val noopSummary = withTimeout(TIMEOUT_MS) {
      repo.importRekapanResult(RekapanCsvParser.parse(updatedCsv), isAutoSync = true).getOrThrow()
    }
    assertEquals(false, noopSummary.hasChanges)
  }

  @Test
  fun importRekapanResult_fullMultiSheetWorkbook_createsAllBatchesMembersAndItemsThroughSecurityRules() = runBlocking {
    val uid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    clearImportedStoreDocs()
    val repo = GroupOrderRepository(firestore, auth)

    val file = listOf(
      File("app/src/main/res/raw/rekapan_jie_full.xlsx"),
      File("src/main/res/raw/rekapan_jie_full.xlsx"),
    ).first { it.exists() }

    val sheets = file.inputStream().use { XlsxWorkbookParser.parse(it) }
    val parsed = RekapanCsvParser.parseSheets(
      sheets = sheets.map { RekapanSheetInput(sheetName = it.sheetName, rows = it.rows) }
    )
    assertTrue("Expected > 20 members to exercise multi-chunk Firestore rules", parsed.totalMembers > 20)
    assertTrue("Expected > 20 items to exercise multi-chunk Firestore rules", parsed.totalItems > 20)
    assertTrue(parsed.batches.none { it.sheetName.contains("NOVEL", ignoreCase = true) })

    val summary = withTimeout(60_000L) {
      repo.importRekapanResult(parsed).getOrThrow()
    }
    assertEquals(parsed.totalBatches, summary.batchesImported)
    assertEquals(parsed.totalMembers, summary.membersImported)
    assertEquals(parsed.totalItems, summary.itemsImported)

    val savedOrders = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupOrders(uid).first { list -> list.count { it.id.startsWith("imp_go_") } >= parsed.totalBatches }
    }
    val savedMembers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(uid).first { list -> list.count { it.id.startsWith("imp_mem_") } >= parsed.totalMembers }
    }
    val savedItems = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeItems(uid).first { list -> list.count { it.id.startsWith("imp_itm_") } >= parsed.totalItems }
    }

    val importedGos = savedOrders.filter { it.id.startsWith("imp_go_") }
    assertEquals(parsed.totalBatches, importedGos.size)
    assertTrue(savedMembers.count { it.id.startsWith("imp_mem_") } >= parsed.totalMembers)
    assertTrue(savedItems.count { it.id.startsWith("imp_itm_") } >= parsed.totalItems)
    // Verify every imported GO has at least one member linked to its orderId and no NOVEL GO exists
    importedGos.forEach { go ->
      assertTrue("Excluded NOVEL batch should not be in Firestore", !go.artistGroup.contains("NOVEL", ignoreCase = true))
      assertTrue("GO ${go.name} (${go.id}) had 0 members!", savedMembers.any { it.orderId == go.id })
    }
  }

  @Test
  fun catalogItems_seedAndBuyerOrderPlacement_respectsSecurityRules() = runBlocking {
    val ownerUid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)

    val catalogId = withTimeout(TIMEOUT_MS) {
      repo.seedLngshotLightstick().getOrThrow()
    }

    val created = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(ownerUid).first { it.any { c -> c.id == catalogId } }
        .first { it.id == catalogId }
    }
    assertEquals("LIGHTSTICK LNGSHOT", created.title)
    assertEquals(760_000L, created.price)
    assertEquals(600_000L, created.dpAmount)
    assertEquals(1L, created.stock)

    // Buyer signs in and places an order from catalog
    val buyerUid = signInTestUser("buyer-order@test.com")
    withTimeout(TIMEOUT_MS) {
      repo.placeCatalogOrderIntoGroupOrder(
        catalogItem = created,
        targetOrder = null,
        memberName = "Nadia",
        memberHandle = "081234567890",
        isCurrentUser = true,
        initialPaidAmount = 600_000L,
        paymentMethod = "QRIS",
        notes = "DP Lightstick",
      ).getOrThrow()
    }

    val afterOrder = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(buyerUid).first { it.any { c -> c.id == catalogId && c.stock == 0L } }
        .first { it.id == catalogId }
    }
    assertEquals(0L, afterOrder.stock)
  }

  @Test
  fun catalogItems_addUpdateAndDeleteProductPhoto_respectsSecurityRules() = runBlocking {
    val ownerUid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)
    val initialPhoto = "/9j/4AAQSkZJRgABAQAAAQABAAD/initialPhotoData=="
    val updatedPhoto = "/9j/4AAQSkZJRgABAQAAAQABAAD/updatedPhotoData=="

    // 1. Owner adds new catalog item with product photo
    val catalogId = withTimeout(TIMEOUT_MS) {
      repo.addCatalogItem(
        title = "ALBUM SEVENTEEN SPILL THE FEELS",
        artistGroup = "SEVENTEEN",
        category = "Album",
        price = 285_000L,
        dpAmount = 150_000L,
        stock = 5L,
        components = "Photobook | CD | Photocard",
        imageKey = "",
        imageBase64 = initialPhoto,
        notes = "PO Reguler dengan foto produk",
      ).getOrThrow()
    }

    val created = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(ownerUid).first { it.any { c -> c.id == catalogId } }
        .first { it.id == catalogId }
    }
    assertEquals(initialPhoto, created.imageBase64)
    assertTrue(created.hasPhoto)

    // 2. Owner changes product photo directly via updateCatalogItemPhoto
    withTimeout(TIMEOUT_MS) {
      repo.updateCatalogItemPhoto(catalogId, imageKey = "", imageBase64 = updatedPhoto).getOrThrow()
    }
    val afterChange = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(ownerUid).first { list ->
        list.any { it.id == catalogId && it.imageBase64 == updatedPhoto }
      }.first { it.id == catalogId }
    }
    assertEquals(updatedPhoto, afterChange.imageBase64)

    // 3. Owner deletes product photo
    withTimeout(TIMEOUT_MS) {
      repo.updateCatalogItemPhoto(catalogId, imageKey = "", imageBase64 = "").getOrThrow()
    }
    val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(ownerUid).first { list ->
        list.any { it.id == catalogId && it.imageBase64.isEmpty() && it.imageKey.isEmpty() }
      }.first { it.id == catalogId }
    }
    assertTrue(!afterDelete.hasPhoto)

    // 4. Owner re-adds photo & edits details via updateCatalogItemDetails
    withTimeout(TIMEOUT_MS) {
      repo.updateCatalogItemDetails(
        item = afterDelete,
        title = "ALBUM SEVENTEEN SPILL THE FEELS VER 2",
        artistGroup = "SEVENTEEN",
        category = "Album",
        price = 290_000L,
        dpAmount = 150_000L,
        stock = 4L,
        components = "Photobook | CD | 2 Photocard",
        imageKey = "",
        imageBase64 = initialPhoto,
        notes = "Foto ditambahkan ulang via Edit Produk",
      ).getOrThrow()
    }
    val afterEdit = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(ownerUid).first { list ->
        list.any { it.id == catalogId && it.imageBase64 == initialPhoto && it.price == 290_000L }
      }.first { it.id == catalogId }
    }
    assertEquals(initialPhoto, afterEdit.imageBase64)

    // 5. Buyer CANNOT modify or delete product photos, but CAN place order (decrementing stock)
    val buyerUid = signInTestUser("buyer-photo-check@test.com")
    val buyerPhotoTamper = repo.updateCatalogItemPhoto(catalogId, "", updatedPhoto)
    assertTrue(buyerPhotoTamper.isFailure)

    val buyerEditTamper = repo.updateCatalogItemDetails(
      item = afterEdit,
      title = afterEdit.title,
      artistGroup = afterEdit.artistGroup,
      category = afterEdit.category,
      price = afterEdit.price,
      dpAmount = afterEdit.dpAmount,
      stock = afterEdit.stock,
      components = afterEdit.components,
      imageKey = "",
      imageBase64 = "",
      notes = afterEdit.notes,
    )
    assertTrue(buyerEditTamper.isFailure)

    withTimeout(TIMEOUT_MS) {
      repo.placeCatalogOrderIntoGroupOrder(
        catalogItem = afterEdit,
        targetOrder = null,
        memberName = "Rina",
        memberHandle = "08122223333",
        isCurrentUser = true,
        initialPaidAmount = 150_000L,
        paymentMethod = "QRIS",
        notes = "Beli 1",
      ).getOrThrow()
    }
    val afterBuyerOrder = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCatalogItems(buyerUid).first { list ->
        list.any { it.id == catalogId && it.stock == 3L }
      }.first { it.id == catalogId }
    }
    assertEquals(3L, afterBuyerOrder.stock)
  }

  @Test
  fun exchangeRates_saveAndObserve_respectsSecurityRules() = runBlocking {
    val uid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)

    val customRates = ExchangeRates(
      china = 2800.0,
      korea = 14.5,
      thailand = 550.0,
      jepang = 120.0,
      vietnam = 0.82,
      taiwan = 595.0,
    )

    withTimeout(TIMEOUT_MS) {
      repo.saveExchangeRates(customRates).getOrThrow()
    }

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeExchangeRates(uid).first { it.china == 2800.0 && it.vietnam == 0.82 }
    }
    assertEquals(14.5, observed.korea, 0.0001)
    assertEquals(595.0, observed.taiwan, 0.0001)
  }

  @Test
  fun photoUpdates_ownerAndBuyerTesti_createAndNotifyThroughSecurityRules() = runBlocking {
    val ownerUid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val repo = GroupOrderRepository(firestore, auth)
    val dummyBase64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA=="

    // Owner uploads Arrived WH KR/China photo
    val (ownerPhotoId, _) = withTimeout(TIMEOUT_MS) {
      repo.addPhotoUpdate(
        category = PhotoCategory.ARRIVED_WH_KR_CN,
        uploaderRole = "OWNER",
        uploaderName = "Owner JIEJIE",
        uploaderPhone = "",
        orderId = "",
        orderName = "",
        caption = "Sudah sampai warehouse Korea batch 1",
        imageBase64 = dummyBase64,
      ).getOrThrow()
    }

    // Buyer uploads TESTI with name + phone using a non-owner email
    val buyerUid = signInTestUser("pembeli-testi@test.com")
    val (buyerPhotoId, _) = withTimeout(TIMEOUT_MS) {
      repo.addPhotoUpdate(
        category = PhotoCategory.TESTI,
        uploaderRole = "BUYER",
        uploaderName = "Dina",
        uploaderPhone = "081298765432",
        orderId = "",
        orderName = "",
        caption = "Barang sampai aman dan mulus!",
        imageBase64 = dummyBase64,
      ).getOrThrow()
    }

    // Buyer CANNOT upload an OWNER proof photo
    val forgedOwnerPhoto = repo.addPhotoUpdate(
      category = PhotoCategory.PROOF_CHECKOUT,
      uploaderRole = "OWNER",
      uploaderName = "Fake Owner",
      uploaderPhone = "",
      orderId = "",
      orderName = "",
      caption = "Forged checkout",
      imageBase64 = dummyBase64,
    )
    assertTrue(forgedOwnerPhoto.isFailure)

    // Buyer TESTI missing phone must fail validation
    val missingPhoneResult = repo.addPhotoUpdate(
      category = PhotoCategory.TESTI,
      uploaderRole = "BUYER",
      uploaderName = "Dina",
      uploaderPhone = "",
      orderId = "",
      orderName = "",
      caption = "Tanpa nomor HP",
      imageBase64 = dummyBase64,
    )
    assertTrue(missingPhoneResult.isFailure)

    val photos = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observePhotoUpdates(buyerUid).first { list ->
        list.any { it.id == ownerPhotoId } && list.any { it.id == buyerPhotoId }
      }
    }
    assertTrue(photos.any { it.id == ownerPhotoId } && photos.any { it.id == buyerPhotoId })

    // Sign back in as owner jiego4sho@gmail.com to update owner's photo
    signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val targetOwnerPhoto = photos.first { it.id == ownerPhotoId }
    withTimeout(TIMEOUT_MS) {
      repo.updatePhotoUpdate(
        existing = targetOwnerPhoto,
        category = PhotoCategory.ARRIVED_ADMIN_PACKING,
        uploaderName = "Admin Packing ER",
        uploaderPhone = "08816623574",
        orderId = "",
        orderName = "",
        caption = "Sudah tiba di Admin Packing ER",
        imageBase64 = dummyBase64,
      ).getOrThrow()
    }

    val notifications = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotifications(ownerUid).first { list ->
        list.count { it.typeEnum == NotificationType.PHOTO_UPDATE } >= 3
      }
    }
    assertTrue(notifications.count { it.typeEnum == NotificationType.PHOTO_UPDATE } >= 3)
  }

  @Test
  fun notificationSubscribers_and_ownerAnnouncement_respectsSecurityRules() = runBlocking {
    // 1. Buyer registers own verified email
    val buyerEmail = "pembeli-notif@test.com"
    val buyerUid = signInTestUser(buyerEmail)
    val repo = GroupOrderRepository(firestore, auth)

    withTimeout(TIMEOUT_MS) {
      repo.registerNotificationSubscriber(buyerEmail, "Sinta Buyer").getOrThrow()
    }

    // Buyer CANNOT list all subscribers
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeNotificationSubscribers(buyerUid).first()
      }
      fail("Expected PERMISSION_DENIED when buyer lists subscribers")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    // 2. Owner lists registered buyer emails and broadcasts announcement
    val ownerUid = signInTestUser(JiegoAuthConfig.OWNER_EMAIL)
    val subscribers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotificationSubscribers(ownerUid).first { list ->
        list.any { it.email == buyerEmail }
      }
    }
    assertTrue(subscribers.any { it.email == buyerEmail && it.displayName == "Sinta Buyer" })

    val notifId = withTimeout(TIMEOUT_MS) {
      repo.broadcastOwnerAnnouncement(
        title = "Update Resi Batch 5",
        message = "Semua paket Batch 5 sudah dikirim, cek tab Foto ya!",
      ).getOrThrow()
    }

    // 3. Buyer receives the broadcast notification immediately in notifications stream
    val signedBuyerUid = signInTestUser(buyerEmail)
    val buyerNotifications = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotifications(signedBuyerUid).first { list -> list.any { it.id == notifId } }
    }
    assertTrue(buyerNotifications.any { it.id == notifId && it.title == "Update Resi Batch 5" })
  }

  private suspend fun clearImportedStoreDocs() {
    val storeRef = firestore.collection("stores").document(JiegoAuthConfig.STORE_ID)
    for (col in listOf("items", "members", "groupOrders")) {
      val docs = withTimeout(TIMEOUT_MS) { storeRef.collection(col).get().await().documents }
      docs.filter { it.id.startsWith("imp_") }.chunked(200).forEach { chunk ->
        val batch = firestore.batch()
        chunk.forEach { batch.delete(it.reference) }
        withTimeout(TIMEOUT_MS) { batch.commit().await() }
      }
    }
  }

  private companion object {
    const val TIMEOUT_MS = 20_000L
    const val FLOW_TIMEOUT_MS = 15_000L
  }
}
