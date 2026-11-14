const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-kpopgo-bd82b07e-9223-4857-9abe-0b035c81dee1";
const OWNER_UID = "owner_jiego_uid";
const OWNER_EMAIL = "jiego4sho@gmail.com";
const BUYER_UID = "buyer_uid_456";
const BUYER_EMAIL = "pembeli.test@gmail.com";
const STORE_ID = "jiego";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore(DATABASE_ID);
}

function buyerDb() {
  return testEnv.authenticatedContext(BUYER_UID, { email: BUYER_EMAIL, email_verified: true }).firestore(DATABASE_ID);
}

function storeDoc(db) {
  return db.collection("stores").doc(STORE_ID);
}

function validGroupOrder(uid, overrides = {}) {
  const ts = nowTs();
  return {
    userId: uid,
    name: "PO SEVENTEEN Spill The Feels",
    artistGroup: "SEVENTEEN",
    batchCode: "BATCH-1",
    status: "OPEN",
    closingDateMillis: Date.now() + 86400000 * 5,
    paymentDeadlineMillis: Date.now() + 86400000 * 3,
    notes: "Harga sudah termasuk EMS & Tax estimasi",
    memberCount: 1,
    totalOrderValue: 350000,
    totalPaidAmount: 150000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validMember(uid, orderId, overrides = {}) {
  const ts = nowTs();
  return {
    userId: uid,
    orderId,
    memberName: "Sinta Putri",
    memberHandle: "@sinta_carat",
    isCurrentUser: true,
    shippingFee: 25000,
    totalItemsCost: 325000,
    paidAmount: 150000,
    orderStatus: "WAITING_PAYMENT",
    lastPaymentMethod: "QRIS",
    notes: "DP 50%",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validItem(uid, orderId, memberId, overrides = {}) {
  const ts = nowTs();
  return {
    userId: uid,
    orderId,
    memberId,
    itemName: "Album Carat Ver.",
    variant: "Mingyu",
    quantity: 1,
    unitPrice: 325000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validNotification(uid, orderId, overrides = {}) {
  const ts = nowTs();
  return {
    userId: uid,
    orderId,
    orderName: "PO SEVENTEEN Spill The Feels",
    memberId: "",
    type: "ORDER_STATUS",
    title: "Pesanan dikonfirmasi",
    message: "Pesanan grup order Anda telah dikonfirmasi oleh GOM.",
    isRead: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validCatalogItem(uid, overrides = {}) {
  const ts = nowTs();
  return {
    userId: uid,
    title: "LIGHTSTICK LNGSHOT",
    artistGroup: "LNGSHOT",
    category: "Lightstick",
    price: 760000,
    dpAmount: 600000,
    stock: 1,
    components: "Package Box | Light Stick | Strap | User Manual | Photocard (4EA, 1SET)",
    imageKey: "lngshot_lightstick",
    notes: "Official Light Stick LNGSHOT",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write JIEGO store", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(storeDoc(unauthDb).collection("groupOrders").get());
  await assertFails(
    storeDoc(unauthDb).collection("groupOrders").doc("go_1").set(validGroupOrder(OWNER_UID))
  );
});

test("Owner jiego4sho@gmail.com can manage groupOrders, members, items, and notifications; buyer can read only", async () => {
  const oStore = storeDoc(ownerDb());
  const bStore = storeDoc(buyerDb());

  await assertSucceeds(oStore.collection("groupOrders").doc("go_1").set(validGroupOrder(OWNER_UID)));
  await assertSucceeds(oStore.collection("members").doc("mem_1").set(validMember(OWNER_UID, "go_1")));
  await assertSucceeds(oStore.collection("items").doc("item_1").set(validItem(OWNER_UID, "go_1", "mem_1")));
  await assertSucceeds(oStore.collection("notifications").doc("notif_1").set(validNotification(OWNER_UID, "go_1")));

  // Buyer can read store data
  await assertSucceeds(bStore.collection("groupOrders").get());
  await assertSucceeds(bStore.collection("members").get());

  // Buyer CANNOT mark payments, delete orders, or edit GO metadata
  await assertFails(
    bStore.collection("members").doc("mem_1").update({
      paidAmount: 350000,
      lastPaymentMethod: "QRIS",
      updatedAt: nowTs(),
    })
  );
  await assertFails(bStore.collection("groupOrders").doc("go_1").delete());
  await assertFails(
    bStore.collection("groupOrders").doc("go_1").update({ status: "SHIPPED", updatedAt: nowTs() })
  );

  // Owner CAN update member payment & GO status
  await assertSucceeds(
    oStore.collection("members").doc("mem_1").update({
      paidAmount: 350000,
      lastPaymentMethod: "Transfer BCA",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    oStore.collection("notifications").doc("notif_1").update({
      isRead: true,
      updatedAt: nowTs(),
    })
  );
});

test("Orphaned member or item without parent document is rejected", async () => {
  const oStore = storeDoc(ownerDb());

  await assertFails(oStore.collection("members").doc("mem_orphan").set(validMember(OWNER_UID, "nonexistent_go")));
  await assertSucceeds(oStore.collection("groupOrders").doc("go_1").set(validGroupOrder(OWNER_UID)));
  await assertFails(oStore.collection("items").doc("item_orphan").set(validItem(OWNER_UID, "go_1", "nonexistent_mem")));
});

test("Shadow update with ghost field and terminal COMPLETED locking are rejected", async () => {
  const oStore = storeDoc(ownerDb());
  const goRef = oStore.collection("groupOrders").doc("go_1");

  await assertSucceeds(goRef.set(validGroupOrder(OWNER_UID)));
  await assertFails(goRef.update({ name: "Updated", isVerified: true, updatedAt: nowTs() }));
  await assertSucceeds(goRef.update({ status: "COMPLETED", updatedAt: nowTs() }));
  await assertFails(goRef.update({ status: "OPEN", updatedAt: nowTs() }));
});

test("Only owner can create/edit catalogItems; buyer can read and decrement stock by 1 on order", async () => {
  const oStore = storeDoc(ownerDb());
  const bStore = storeDoc(buyerDb());
  const catRefOwner = oStore.collection("catalogItems").doc("cat_1");

  await assertSucceeds(catRefOwner.set(validCatalogItem(OWNER_UID, { stock: 2 })));
  await assertFails(
    oStore.collection("catalogItems").doc("cat_bad").set(validCatalogItem(OWNER_UID, { dpAmount: 900000 }))
  );

  // Buyer cannot create or edit price of catalog items
  await assertFails(bStore.collection("catalogItems").doc("cat_2").set(validCatalogItem(BUYER_UID)));
  await assertFails(bStore.collection("catalogItems").doc("cat_1").update({ price: 1000, updatedAt: nowTs() }));

  // Buyer CAN read catalog and decrement stock by 1 when ordering
  await assertSucceeds(bStore.collection("catalogItems").get());
  await assertSucceeds(bStore.collection("catalogItems").doc("cat_1").update({ stock: 1, updatedAt: nowTs() }));
});

test("Only owner jiego4sho@gmail.com can save exchangeRates; buyer can read only", async () => {
  const oStore = storeDoc(ownerDb());
  const bStore = storeDoc(buyerDb());
  const ts = nowTs();

  await assertSucceeds(
    oStore.collection("settings").doc("exchangeRates").set({
      userId: OWNER_UID,
      china: 2750,
      korea: 14,
      thailand: 547,
      jepang: 118,
      vietnam: 0.8,
      taiwan: 590,
      updatedAt: ts,
    })
  );

  // Buyer can read rates but cannot overwrite them
  await assertSucceeds(bStore.collection("settings").doc("exchangeRates").get());
  await assertFails(
    bStore.collection("settings").doc("exchangeRates").set({
      userId: BUYER_UID,
      china: 1,
      korea: 1,
      thailand: 1,
      jepang: 1,
      vietnam: 1,
      taiwan: 1,
      updatedAt: ts,
    })
  );
});

test("PhotoUpdates enforce owner proof vs buyer TESTI rules", async () => {
  const oStore = storeDoc(ownerDb());
  const bStore = storeDoc(buyerDb());
  const ts = nowTs();
  const sampleB64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/";

  // Owner proof checkout photo
  await assertSucceeds(
    oStore.collection("photoUpdates").doc("photo_1").set({
      userId: OWNER_UID,
      category: "PROOF_CHECKOUT",
      uploaderRole: "OWNER",
      uploaderName: "Owner JIEJIE",
      uploaderPhone: "",
      orderId: "",
      orderName: "",
      caption: "CO Weverse batch 1 done",
      imageBase64: sampleB64,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Buyer CANNOT upload OWNER proof checkout photo
  await assertFails(
    bStore.collection("photoUpdates").doc("photo_forged").set({
      userId: BUYER_UID,
      category: "PROOF_CHECKOUT",
      uploaderRole: "OWNER",
      uploaderName: "Fake Owner",
      uploaderPhone: "",
      orderId: "",
      orderName: "",
      caption: "Forged",
      imageBase64: sampleB64,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Buyer TESTI with name + phone succeeds
  await assertSucceeds(
    bStore.collection("photoUpdates").doc("photo_2").set({
      userId: BUYER_UID,
      category: "TESTI",
      uploaderRole: "BUYER",
      uploaderName: "Sinta",
      uploaderPhone: "081234567890",
      orderId: "",
      orderName: "",
      caption: "Paket sampai aman kak!",
      imageBase64: sampleB64,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Buyer cannot delete owner's photo
  await assertFails(bStore.collection("photoUpdates").doc("photo_1").delete());

  // Buyer can read all photos & emit PHOTO_UPDATE notification
  await assertSucceeds(bStore.collection("photoUpdates").get());
  await assertSucceeds(
    bStore.collection("notifications").doc("notif_photo").set(
      validNotification(BUYER_UID, "photo_gallery", {
        type: "PHOTO_UPDATE",
        orderName: "Halaman Foto JIEGO",
        title: "Foto Testi Baru (Sinta)",
        message: "Sinta (081234567890) mengunggah Foto Testi Pembeli.",
      })
    )
  );
});

test("Notification subscribers enforce strict email privacy (owner lists all, buyer manages own only)", async () => {
  const oStore = storeDoc(ownerDb());
  const bStore = storeDoc(buyerDb());
  const ts = nowTs();

  // Buyer can check and register own email matching auth token
  await assertSucceeds(bStore.collection("subscribers").doc(BUYER_UID).get());
  await assertSucceeds(
    bStore.collection("subscribers").doc(BUYER_UID).set({
      userId: BUYER_UID,
      email: BUYER_EMAIL,
      displayName: "Pembeli Test",
      platform: "ANDROID",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(bStore.collection("subscribers").doc(BUYER_UID).get());

  // Buyer CANNOT spoof another email or register under another UID
  await assertFails(
    bStore.collection("subscribers").doc(BUYER_UID).set({
      userId: BUYER_UID,
      email: "fake@other.com",
      displayName: "Spoof",
      platform: "ANDROID",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bStore.collection("subscribers").doc("other_uid").set({
      userId: "other_uid",
      email: BUYER_EMAIL,
      displayName: "Other",
      platform: "ANDROID",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Buyer CANNOT list all subscriber emails or read owner's subscriber doc
  await assertFails(bStore.collection("subscribers").get());
  await assertFails(bStore.collection("subscribers").doc(OWNER_UID).get());

  // Owner jiego4sho@gmail.com CAN list all subscriber emails for BCC announcements
  await assertSucceeds(oStore.collection("subscribers").get());
});
