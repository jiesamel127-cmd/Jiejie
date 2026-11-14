package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GoStatus(val code: String, val labelId: String) {
  OPEN("OPEN", "PO Dibuka"),
  CONFIRMED("CONFIRMED", "Pesanan Dikonfirmasi"),
  WAITING_PAYMENT("WAITING_PAYMENT", "Menunggu Pembayaran"),
  ORDERED("ORDERED", "Dipesan ke Supplier"),
  SHIPPED("SHIPPED", "Barang Dikirim"),
  COMPLETED("COMPLETED", "Selesai");

  companion object {
    fun fromCode(code: String): GoStatus = entries.find { it.code == code } ?: OPEN
  }
}

enum class MemberOrderStatus(val code: String, val labelId: String) {
  CONFIRMED("CONFIRMED", "Pesanan dikonfirmasi"),
  WAITING_PAYMENT("WAITING_PAYMENT", "Menunggu pembayaran"),
  ORDERED("ORDERED", "Diproses di Korea"),
  SHIPPED("SHIPPED", "Barang dikirim"),
  COMPLETED("COMPLETED", "Selesai diterima");

  companion object {
    fun fromCode(code: String): MemberOrderStatus =
      entries.find { it.code == code } ?: CONFIRMED
  }
}

enum class PaymentStatus(val labelId: String) {
  BELUM_BAYAR("Belum Bayar"),
  DP("DP / Sebagian"),
  LUNAS("Lunas")
}

enum class NotificationType(val code: String, val labelId: String) {
  ORDER_STATUS("ORDER_STATUS", "Status Pesanan"),
  PAYMENT_REMINDER("PAYMENT_REMINDER", "Pengingat Bayar"),
  GO_UPDATE("GO_UPDATE", "Info Grup Order"),
  PHOTO_UPDATE("PHOTO_UPDATE", "Update Foto");

  companion object {
    fun fromCode(code: String): NotificationType =
      entries.find { it.code == code } ?: GO_UPDATE
  }
}

@IgnoreExtraProperties
data class GroupOrder(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val artistGroup: String = "",
  val batchCode: String = "",
  val status: String = GoStatus.OPEN.code,
  val closingDateMillis: Long = 0L,
  val paymentDeadlineMillis: Long = 0L,
  val notes: String = "",
  val memberCount: Long = 0L,
  val totalOrderValue: Long = 0L,
  val totalPaidAmount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val statusEnum: GoStatus
    get() = GoStatus.fromCode(status)

  val remainingOrderBalance: Long
    get() = (totalOrderValue - totalPaidAmount).coerceAtLeast(0L)

  val paidProgress: Float
    get() = if (totalOrderValue <= 0L) 0f else (totalPaidAmount.toFloat() / totalOrderValue.toFloat()).coerceIn(0f, 1f)
}

@IgnoreExtraProperties
data class MemberOrder(
  @DocumentId val id: String = "",
  val userId: String = "",
  val orderId: String = "",
  val memberName: String = "",
  val memberHandle: String = "",
  val isCurrentUser: Boolean = false,
  val shippingFee: Long = 0L,
  val totalItemsCost: Long = 0L,
  val paidAmount: Long = 0L,
  val orderStatus: String = MemberOrderStatus.CONFIRMED.code,
  val lastPaymentMethod: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val totalCost: Long
    get() = totalItemsCost + shippingFee

  val remainingBalance: Long
    get() = (totalCost - paidAmount).coerceAtLeast(0L)

  val paymentStatus: PaymentStatus
    get() = when {
      totalCost > 0L && paidAmount >= totalCost -> PaymentStatus.LUNAS
      paidAmount > 0L -> PaymentStatus.DP
      else -> PaymentStatus.BELUM_BAYAR
    }

  val orderStatusEnum: MemberOrderStatus
    get() = MemberOrderStatus.fromCode(orderStatus)
}

@IgnoreExtraProperties
data class OrderItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val orderId: String = "",
  val memberId: String = "",
  val itemName: String = "",
  val variant: String = "",
  val quantity: Long = 1L,
  val unitPrice: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val lineTotal: Long
    get() = quantity * unitPrice
}

@IgnoreExtraProperties
data class GoNotification(
  @DocumentId val id: String = "",
  val userId: String = "",
  val orderId: String = "",
  val orderName: String = "",
  val memberId: String = "",
  val type: String = NotificationType.GO_UPDATE.code,
  val title: String = "",
  val message: String = "",
  val isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val typeEnum: NotificationType
    get() = NotificationType.fromCode(type)
}

@IgnoreExtraProperties
data class CatalogItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val artistGroup: String = "",
  val category: String = "Lightstick",
  val price: Long = 0L,
  val dpAmount: Long = 0L,
  val stock: Long = 0L,
  val components: String = "",
  val imageKey: String = "",
  val imageBase64: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val remainingAfterDp: Long
    get() = (price - dpAmount).coerceAtLeast(0L)

  val isOutOfStock: Boolean
    get() = stock <= 0L

  val hasPhoto: Boolean
    get() = imageBase64.isNotBlank() || imageKey == "lngshot_lightstick"
}

enum class RateCountry(
  val key: String,
  val countryName: String,
  val currencyCode: String,
  val defaultRate: Double,
) {
  CHINA("china", "China", "CNY", 2750.0),
  KOREA("korea", "Korea", "KRW", 14.0),
  THAILAND("thailand", "Thailand", "THB", 547.0),
  JEPANG("jepang", "Jepang", "JPY", 118.0),
  VIETNAM("vietnam", "Vietnam", "VND", 0.80),
  TAIWAN("taiwan", "Taiwan", "TWD", 590.0);
}

@IgnoreExtraProperties
data class ExchangeRates(
  @DocumentId val id: String = "exchangeRates",
  val userId: String = "",
  val china: Double = RateCountry.CHINA.defaultRate,
  val korea: Double = RateCountry.KOREA.defaultRate,
  val thailand: Double = RateCountry.THAILAND.defaultRate,
  val jepang: Double = RateCountry.JEPANG.defaultRate,
  val vietnam: Double = RateCountry.VIETNAM.defaultRate,
  val taiwan: Double = RateCountry.TAIWAN.defaultRate,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun rateFor(country: RateCountry): Double = when (country) {
    RateCountry.CHINA -> china
    RateCountry.KOREA -> korea
    RateCountry.THAILAND -> thailand
    RateCountry.JEPANG -> jepang
    RateCountry.VIETNAM -> vietnam
    RateCountry.TAIWAN -> taiwan
  }.takeIf { it > 0.0 } ?: country.defaultRate

  fun withRate(country: RateCountry, newRate: Double): ExchangeRates {
    val safe = newRate.coerceIn(0.01, 1_000_000.0)
    return when (country) {
      RateCountry.CHINA -> copy(china = safe)
      RateCountry.KOREA -> copy(korea = safe)
      RateCountry.THAILAND -> copy(thailand = safe)
      RateCountry.JEPANG -> copy(jepang = safe)
      RateCountry.VIETNAM -> copy(vietnam = safe)
      RateCountry.TAIWAN -> copy(taiwan = safe)
    }
  }

  fun calculateGrossIdr(
    country: RateCountry,
    foreignUnitPrice: Double,
    quantity: Long = 1L,
    foreignDomesticShipping: Double = 0.0,
    extraFeeIdr: Long = 0L,
  ): Long {
    val safePrice = foreignUnitPrice.coerceAtLeast(0.0)
    val safeQty = quantity.coerceAtLeast(1L)
    val safeShip = foreignDomesticShipping.coerceAtLeast(0.0)
    val safeFee = extraFeeIdr.coerceAtLeast(0L)
    val totalForeign = (safePrice * safeQty) + safeShip
    return kotlin.math.round(totalForeign * rateFor(country)).toLong().coerceAtLeast(0L) + safeFee
  }
}

enum class PhotoCategory(
  val code: String,
  val labelId: String,
  val shortLabel: String,
  val isOwnerCategory: Boolean,
) {
  PROOF_CHECKOUT("PROOF_CHECKOUT", "Foto Proof Checkout", "Proof Checkout", true),
  ARRIVED_WH_KR_CN("ARRIVED_WH_KR_CN", "Foto Arrived Warehouse KR/China", "WH KR/China", true),
  ARRIVED_WH_INA("ARRIVED_WH_INA", "Foto Arrived Warehouse INA", "WH INA", true),
  ARRIVED_ADMIN_PACKING("ARRIVED_ADMIN_PACKING", "Foto Arrived Admin Packing", "Admin Packing", true),
  PACKING("PACKING", "Foto Packing", "Packing", true),
  TESTI("TESTI", "Foto Testi Pembeli", "Testi", false);

  companion object {
    fun fromCode(code: String): PhotoCategory =
      entries.find { it.code == code } ?: PROOF_CHECKOUT
  }
}

@IgnoreExtraProperties
data class PhotoUpdate(
  @DocumentId val id: String = "",
  val userId: String = "",
  val category: String = PhotoCategory.PROOF_CHECKOUT.code,
  val uploaderRole: String = "OWNER",
  val uploaderName: String = "",
  val uploaderPhone: String = "",
  val orderId: String = "",
  val orderName: String = "",
  val caption: String = "",
  val imageBase64: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val categoryEnum: PhotoCategory
    get() = PhotoCategory.fromCode(category)

  val isBuyerTesti: Boolean
    get() = categoryEnum == PhotoCategory.TESTI || uploaderRole == "BUYER"
}

@IgnoreExtraProperties
data class NotificationSubscriber(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val platform: String = "ANDROID",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

