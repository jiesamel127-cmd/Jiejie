package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CatalogItem
import com.example.data.model.ExchangeRates
import com.example.data.model.GoStatus
import com.example.data.model.GroupOrder
import com.example.ui.components.ImageCodecHelper
import com.example.ui.components.InlineRateConverterHelper
import com.example.ui.components.JiegoContacts
import com.example.ui.components.JiegoWhatsAppFooterCard
import com.example.ui.components.formatIdr
import com.example.ui.viewmodel.KPopGoData
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(
  data: KPopGoData,
  onAddCatalogItem: (title: String, artist: String, category: String, price: Long, dp: Long, stock: Long, components: String, imageBase64: String, notes: String) -> Unit,
  onUpdateCatalogItem: (item: CatalogItem, title: String, artist: String, category: String, price: Long, dp: Long, stock: Long, components: String, imageKey: String, imageBase64: String, notes: String) -> Unit,
  onUpdateCatalogPhoto: (item: CatalogItem, imageKey: String, imageBase64: String) -> Unit,
  onUpdateStock: (catalogId: String, newStock: Long) -> Unit,
  onDeleteCatalogItem: (catalogId: String) -> Unit,
  onSeedLngshot: () -> Unit,
  onOrderCatalogItem: (
    catalogItem: CatalogItem,
    targetOrder: GroupOrder?,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
  ) -> Unit,
  onOpenQrisDialog: (amountToPay: Long?, subtitle: String?) -> Unit,
  onOpenRatesCalculator: () -> Unit = {},
  isBuyerMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showFormDialog by remember { mutableStateOf(false) }
  var editingItem by remember { mutableStateOf<CatalogItem?>(null) }
  var orderingItem by remember { mutableStateOf<CatalogItem?>(null) }
  var previewPhotoItem by remember { mutableStateOf<CatalogItem?>(null) }
  var quickPhotoTargetItem by remember { mutableStateOf<CatalogItem?>(null) }

  val quickPhotoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    val target = quickPhotoTargetItem
    quickPhotoTargetItem = null
    if (uri != null && target != null) {
      scope.launch {
        val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(context, uri)
        if (encoded != null) {
          onUpdateCatalogPhoto(target, "", encoded)
        }
      }
    }
  }

  if (showFormDialog || editingItem != null) {
    CatalogItemFormDialog(
      existing = editingItem,
      exchangeRates = data.exchangeRates,
      onDismiss = {
        showFormDialog = false
        editingItem = null
      },
      onConfirm = { title, artist, category, price, dp, stock, components, imageKey, imageBase64, notes ->
        val existing = editingItem
        if (existing == null) {
          onAddCatalogItem(title, artist, category, price, dp, stock, components, imageBase64, notes)
        } else {
          onUpdateCatalogItem(existing, title, artist, category, price, dp, stock, components, imageKey, imageBase64, notes)
        }
        showFormDialog = false
        editingItem = null
      },
    )
  }

  previewPhotoItem?.let { target ->
    CatalogPhotoPreviewDialog(
      item = target,
      onDismiss = { previewPhotoItem = null },
    )
  }

  if (orderingItem != null) {
    val target = orderingItem!!
    OrderFromCatalogDialog(
      item = target,
      availableGroupOrders = data.groupOrders.filter { it.statusEnum != GoStatus.COMPLETED },
      isBuyerMode = isBuyerMode,
      onDismiss = { orderingItem = null },
      onOpenQrisPreview = { amount ->
        onOpenQrisDialog(amount, "Pembayaran ${target.title}")
      },
      onConfirm = { selectedGo, name, handle, isMe, paid, method, notes ->
        onOrderCatalogItem(target, selectedGo, name, handle, isMe, paid, method, notes)
        orderingItem = null
      },
    )
  }

  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    // Catalog Header Banner
    item(key = "catalog_header") {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(16.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Katalog JIEGO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (isBuyerMode) {
                  "${data.catalogItems.size} produk tersedia • Cek harga, DP, stok & pesan sekarang"
                } else {
                  "${data.catalogItems.size} produk tersedia • Kelola stok, harga & pesanan"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { onOpenQrisDialog(null, "QRIS Resmi Pembelian JIEGO") },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .height(48.dp)
                .testTag("catalog_qris_button"),
            ) {
              Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("QRIS")
            }
          }

          if (!isBuyerMode) {
            Button(
              onClick = { showFormDialog = true },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_catalog_item_button"),
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Tambah Produk Katalog", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = onOpenRatesCalculator,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("catalog_rates_button"),
            ) {
              Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Kalkulator Rate", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
              onClick = { JiegoContacts.openShopee(context) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("catalog_shopee_button"),
            ) {
              Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Toko Shopee", maxLines = 1, overflow = TextOverflow.Ellipsis)
              Spacer(Modifier.width(4.dp))
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }

    if (data.catalogItems.isEmpty()) {
      item(key = "empty_catalog") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Storefront,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp),
            )
            Text("Belum Ada Item Katalog", style = MaterialTheme.typography.titleLarge)
            Text(
              text = if (isBuyerMode) {
                "Katalog produk sedang disiapkan oleh Owner JIEJIE. Silakan cek lagi nanti atau hubungi Owner via WhatsApp."
              } else {
                "Tambahkan LIGHTSTICK LNGSHOT (Harga 760k, DP 600k, Stok 1) atau buat item katalog k-pop lainnya."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isBuyerMode) {
              Button(
                onClick = onSeedLngshot,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("seed_lngshot_button"),
              ) {
                Text("Muat LIGHTSTICK LNGSHOT (760k • Stok 1)")
              }
            }
          }
        }
      }
    } else {
      items(data.catalogItems, key = { it.id }) { item ->
        CatalogProductCard(
          item = item,
          isBuyerMode = isBuyerMode,
          onDecreaseStock = { onUpdateStock(item.id, (item.stock - 1L).coerceAtLeast(0L)) },
          onIncreaseStock = { onUpdateStock(item.id, item.stock + 1L) },
          onOrderNow = { orderingItem = item },
          onOpenQris = { onOpenQrisDialog(item.dpAmount.takeIf { it > 0L } ?: item.price, "QRIS DP/Pelunasan ${item.title}") },
          onPreviewPhoto = { previewPhotoItem = item },
          onPickPhoto = {
            quickPhotoTargetItem = item
            quickPhotoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onDeletePhoto = { onUpdateCatalogPhoto(item, "", "") },
          onEdit = { editingItem = item },
          onDelete = { onDeleteCatalogItem(item.id) },
        )
      }
    }

    item(key = "catalog_wa_footer") {
      JiegoWhatsAppFooterCard(
        customMessageSuffix = "Saya ingin menanyakan katalog produk JIEGO.",
      )
    }
  }
}

@Composable
private fun CatalogProductCard(
  item: CatalogItem,
  isBuyerMode: Boolean,
  onDecreaseStock: () -> Unit,
  onIncreaseStock: () -> Unit,
  onOrderNow: () -> Unit,
  onOpenQris: () -> Unit,
  onPreviewPhoto: () -> Unit,
  onPickPhoto: () -> Unit,
  onDeletePhoto: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val customBitmap = ImageCodecHelper.rememberDecodedBase64Image(item.imageBase64)
  val hasDisplayPhoto = customBitmap != null || item.imageBase64.isNotBlank() || item.imageKey == "lngshot_lightstick"

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
      .testTag("catalog_card_${item.id}"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (hasDisplayPhoto) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .clickable(onClick = onPreviewPhoto),
        ) {
          if (customBitmap != null) {
            Image(
              bitmap = customBitmap,
              contentDescription = "Foto produk ${item.title}",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Image(
              painter = painterResource(id = R.drawable.img_catalog_lngshot_lightstick),
              contentDescription = "Foto komponen ${item.title}",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
          Surface(
            color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(12.dp),
          ) {
            Text(
              text = if (item.isOutOfStock) "STOK HABIS" else "STOK: ${item.stock}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onTertiary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(
          start = 16.dp,
          end = 16.dp,
          top = if (hasDisplayPhoto) 0.dp else 16.dp,
          bottom = 16.dp,
        ),
      ) {
        if (!isBuyerMode) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = onPickPhoto,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag(if (hasDisplayPhoto) "change_photo_catalog_${item.id}" else "add_photo_catalog_${item.id}"),
            ) {
              Icon(
                imageVector = if (hasDisplayPhoto) Icons.Default.PhotoCamera else Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (hasDisplayPhoto) "Ubah Foto" else "Tambah Foto Produk",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (hasDisplayPhoto) {
              OutlinedButton(
                onClick = onDeletePhoto,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                  .height(48.dp)
                  .testTag("delete_photo_catalog_${item.id}"),
              ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Hapus Foto", maxLines = 1)
              }
            }
          }
        }
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = item.artistGroup.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = item.category.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            Spacer(Modifier.height(6.dp))
            Text(
              text = item.title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }

          if (!isBuyerMode) {
            Row {
              IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_catalog_${item.id}")) {
                Icon(Icons.Default.Edit, contentDescription = "Ubah produk katalog")
              }
              IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_catalog_${item.id}")) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus produk katalog")
              }
            }
          }
        }

        if (item.components.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.Top,
              modifier = Modifier.padding(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Kelengkapan: ${item.components}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Price / DP / Remaining Breakdown
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
          ) {
            CatalogMetricColumn(
              label = "Harga Full",
              value = formatIdr(item.price),
              highlight = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            CatalogMetricColumn(
              label = "DP",
              value = formatIdr(item.dpAmount),
              highlight = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            CatalogMetricColumn(
              label = "Pelunasan",
              value = formatIdr(item.remainingAfterDp),
              highlight = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.weight(1f),
            )
          }
        }

        // Stock Row (Stepper for Owner, Read-Only Pill for Buyer)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = if (isBuyerMode) 12.dp else 6.dp),
        ) {
          Text(
            text = "Stok Tersedia: ${item.stock}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          )
          if (!isBuyerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = onDecreaseStock,
                enabled = item.stock > 0L,
                modifier = Modifier.testTag("dec_stock_${item.id}"),
              ) {
                Icon(Icons.Default.Remove, contentDescription = "Kurangi stok")
              }
              Text(
                text = item.stock.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp),
              )
              IconButton(
                onClick = onIncreaseStock,
                modifier = Modifier.testTag("inc_stock_${item.id}"),
              ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah stok")
              }
            }
          } else {
            Text(
              text = if (item.isOutOfStock) "Habis" else "Siap dipesan",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
          }
        }

        if (item.notes.isNotBlank()) {
          Text(
            text = item.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Actions
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = onOrderNow,
            enabled = !item.isOutOfStock,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("order_catalog_${item.id}"),
          ) {
            Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              when {
                item.isOutOfStock -> "Stok Habis"
                isBuyerMode -> "Pesan Sekarang"
                else -> "Pesan / Masuk GO"
              }
            )
          }
          OutlinedButton(
            onClick = onOpenQris,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .height(48.dp)
              .testTag("qris_catalog_${item.id}"),
          ) {
            Icon(Icons.Default.QrCode2, contentDescription = "QRIS Pembelian")
          }
        }
      }
    }
  }
}

@Composable
private fun CatalogMetricColumn(
  label: String,
  value: String,
  highlight: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = highlight,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CatalogItemFormDialog(
  existing: CatalogItem?,
  exchangeRates: ExchangeRates = ExchangeRates(),
  onDismiss: () -> Unit,
  onConfirm: (title: String, artist: String, category: String, price: Long, dp: Long, stock: Long, components: String, imageKey: String, imageBase64: String, notes: String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var title by remember { mutableStateOf(existing?.title ?: "") }
  var artist by remember { mutableStateOf(existing?.artistGroup ?: "") }
  var category by remember { mutableStateOf(existing?.category ?: "Lightstick") }
  var priceStr by remember { mutableStateOf(existing?.price?.toString() ?: "") }
  var dpStr by remember { mutableStateOf(existing?.dpAmount?.toString() ?: "") }
  var stockStr by remember { mutableStateOf(existing?.stock?.toString() ?: "1") }
  var components by remember { mutableStateOf(existing?.components ?: "") }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  var imageKey by remember { mutableStateOf(existing?.imageKey ?: "") }
  var imageBase64 by remember { mutableStateOf(existing?.imageBase64 ?: "") }
  var isProcessingImage by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val previewBitmap = ImageCodecHelper.rememberDecodedBase64Image(imageBase64)
  val hasFormPhoto = previewBitmap != null || imageBase64.isNotBlank() || imageKey == "lngshot_lightstick"

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessingImage = true
      errorText = null
      scope.launch {
        val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(context, uri)
        isProcessingImage = false
        if (encoded != null) {
          imageBase64 = encoded
          imageKey = ""
        } else {
          errorText = "Gagal membaca atau mengompres foto yang dipilih."
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (existing == null) "Tambah Produk Katalog" else "Edit Produk Katalog",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 500.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text("Foto Produk (Opsional):", style = MaterialTheme.typography.labelLarge)
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
            .testTag("catalog_form_pick_photo"),
        ) {
          if (previewBitmap != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
              Image(
                bitmap = previewBitmap,
                contentDescription = "Preview foto produk",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp),
              )
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(8.dp),
              ) {
                Text(
                  text = "Ketuk untuk ubah foto",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          } else if (imageKey == "lngshot_lightstick") {
            Box(modifier = Modifier.fillMaxWidth()) {
              Image(
                painter = painterResource(id = R.drawable.img_catalog_lngshot_lightstick),
                contentDescription = "Foto preset produk",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp),
              )
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(8.dp),
              ) {
                Text(
                  text = "Ketuk untuk ubah foto",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(20.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
              )
              Text(
                text = if (isProcessingImage) "Memproses foto..." else "Ketuk untuk Tambah Foto Produk",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        if (hasFormPhoto) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("catalog_form_change_photo_button"),
            ) {
              Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Ubah Foto")
            }
            OutlinedButton(
              onClick = {
                imageBase64 = ""
                imageKey = ""
              },
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier
                .height(48.dp)
                .testTag("catalog_form_delete_photo_button"),
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Hapus Foto")
            }
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(120) },
          label = { Text("Nama Produk *") },
          placeholder = { Text("Misal: LIGHTSTICK LNGSHOT") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = artist,
            onValueChange = { artist = it.take(60) },
            label = { Text("Artis / Grup *") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = category,
            onValueChange = { category = it.take(40) },
            label = { Text("Kategori") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = priceStr,
            onValueChange = { priceStr = it.filter { ch -> ch.isDigit() } },
            label = { Text("Harga Full (Rp) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = dpStr,
            onValueChange = { dpStr = it.filter { ch -> ch.isDigit() } },
            label = { Text("DP (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        InlineRateConverterHelper(
          rates = exchangeRates,
          onApplyIdrPrice = { priceStr = it.toString() },
        )
        OutlinedTextField(
          value = stockStr,
          onValueChange = { stockStr = it.filter { ch -> ch.isDigit() } },
          label = { Text("Stok Tersedia *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = components,
          onValueChange = { components = it.take(400) },
          label = { Text("Isi / Kelengkapan Produk") },
          placeholder = { Text("Package Box | Light Stick | Strap | User Manual | Photocard") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(400) },
          label = { Text("Catatan") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
          Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        enabled = !isProcessingImage,
        onClick = {
          val price = priceStr.toLongOrNull() ?: 0L
          val dp = (dpStr.toLongOrNull() ?: 0L).coerceAtMost(price)
          val stock = stockStr.toLongOrNull() ?: 0L
          if (title.isBlank() || artist.isBlank() || price <= 0L) {
            errorText = "Nama produk, artis, dan harga wajib diisi."
            return@Button
          }
          onConfirm(
            title,
            artist,
            category.ifBlank { "Merchandise" },
            price,
            dp,
            stock,
            components,
            imageKey,
            imageBase64,
            notes,
          )
        },
      ) {
        Text("Simpan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@Composable
private fun CatalogPhotoPreviewDialog(
  item: CatalogItem,
  onDismiss: () -> Unit,
) {
  val customBitmap = ImageCodecHelper.rememberDecodedBase64Image(item.imageBase64)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (customBitmap != null) {
          Image(
            bitmap = customBitmap,
            contentDescription = "Foto penuh ${item.title}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 220.dp, max = 380.dp)
              .clip(RoundedCornerShape(14.dp)),
          )
        } else if (item.imageKey == "lngshot_lightstick") {
          Image(
            painter = painterResource(id = R.drawable.img_catalog_lngshot_lightstick),
            contentDescription = "Foto penuh ${item.title}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 220.dp, max = 380.dp)
              .clip(RoundedCornerShape(14.dp)),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderFromCatalogDialog(
  item: CatalogItem,
  availableGroupOrders: List<GroupOrder>,
  isBuyerMode: Boolean = false,
  onDismiss: () -> Unit,
  onOpenQrisPreview: (amount: Long) -> Unit,
  onConfirm: (
    selectedOrder: GroupOrder?,
    memberName: String,
    memberHandle: String,
    isCurrentUser: Boolean,
    initialPaidAmount: Long,
    paymentMethod: String,
    notes: String,
  ) -> Unit,
) {
  var selectedOrder by remember { mutableStateOf<GroupOrder?>(availableGroupOrders.firstOrNull()) }
  var goDropdownExpanded by remember { mutableStateOf(false) }
  var buyerName by remember { mutableStateOf("") }
  var buyerPhone by remember { mutableStateOf("") }
  var isCurrentUser by remember { mutableStateOf(true) }
  var payDpOnly by remember { mutableStateOf(item.dpAmount in 1 until item.price) }
  var paidNowStr by remember {
    mutableStateOf(
      if (item.dpAmount in 1 until item.price) item.dpAmount.toString() else item.price.toString()
    )
  }
  var paymentMethod by remember { mutableStateOf("QRIS") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pesan ${item.title}", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "Harga: ${formatIdr(item.price)} • DP: ${formatIdr(item.dpAmount)} • Stok: ${item.stock}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
            if (item.components.isNotBlank()) {
              Text(
                text = item.components,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        ExposedDropdownMenuBox(
          expanded = goDropdownExpanded,
          onExpandedChange = { goDropdownExpanded = !goDropdownExpanded },
        ) {
          OutlinedTextField(
            value = selectedOrder?.name ?: "Buat Grup Order Baru (PO ${item.title})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Masukkan ke Grup Order") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goDropdownExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = goDropdownExpanded,
            onDismissRequest = { goDropdownExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("Buat Grup Order Baru (PO ${item.title})") },
              onClick = {
                selectedOrder = null
                goDropdownExpanded = false
              },
            )
            availableGroupOrders.forEach { go ->
              DropdownMenuItem(
                text = { Text(go.name) },
                onClick = {
                  selectedOrder = go
                  goDropdownExpanded = false
                },
              )
            }
          }
        }

        OutlinedTextField(
          value = buyerName,
          onValueChange = { buyerName = it.take(80) },
          label = { Text("Nama Pembeli *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = buyerPhone,
          onValueChange = { buyerPhone = it.take(60) },
          label = { Text("No. WhatsApp / Username") },
          placeholder = { Text("08xxxxxxxxxx atau @username") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        if (!isBuyerMode) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isCurrentUser, onCheckedChange = { isCurrentUser = it })
            Text("Tampilkan di tab \"Belanjaan Saya\"", style = MaterialTheme.typography.bodySmall)
          }
        }

        HorizontalDivider()

        Text("Pilihan Pembayaran Awal", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (item.dpAmount in 1 until item.price) {
            FilterChip(
              selected = payDpOnly,
              onClick = {
                payDpOnly = true
                paidNowStr = item.dpAmount.toString()
              },
              label = { Text("DP ${formatIdr(item.dpAmount)}") },
            )
          }
          FilterChip(
            selected = !payDpOnly && paidNowStr.toLongOrNull() == item.price,
            onClick = {
              payDpOnly = false
              paidNowStr = item.price.toString()
            },
            label = { Text("Lunas ${formatIdr(item.price)}") },
          )
          FilterChip(
            selected = paidNowStr == "0",
            onClick = {
              payDpOnly = false
              paidNowStr = "0"
            },
            label = { Text("Belum Bayar") },
          )
        }

        OutlinedTextField(
          value = paidNowStr,
          onValueChange = { paidNowStr = it.filter { ch -> ch.isDigit() } },
          label = { Text("Nominal Dibayar Sekarang (Rp)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
          onClick = { onOpenQrisPreview(paidNowStr.toLongOrNull() ?: item.dpAmount) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) {
          Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Lihat Foto QRIS Pembelian")
        }

        if (errorText != null) {
          Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (buyerName.isBlank()) {
            errorText = "Nama pembeli wajib diisi."
            return@Button
          }
          val paid = (paidNowStr.toLongOrNull() ?: 0L).coerceAtLeast(0L)
          onConfirm(
            selectedOrder,
            buyerName,
            buyerPhone,
            isCurrentUser,
            paid,
            if (paid > 0L) paymentMethod else "",
            "Pesanan Katalog ${item.title}",
          )
        },
      ) {
        Text(if (isBuyerMode) "Kirim Pesanan" else "Catat Pesanan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}
