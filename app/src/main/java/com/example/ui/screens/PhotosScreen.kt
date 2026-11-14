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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GroupOrder
import com.example.data.model.PhotoCategory
import com.example.data.model.PhotoUpdate
import com.example.ui.components.ImageCodecHelper
import com.example.ui.components.JiegoContacts
import com.example.ui.components.JiegoWhatsAppFooterCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.viewmodel.KPopGoData
import kotlinx.coroutines.launch

@Composable
fun PhotosScreen(
  data: KPopGoData,
  isBuyerMode: Boolean,
  onAddPhoto: (
    category: PhotoCategory,
    uploaderRole: String,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ) -> Unit,
  onUpdatePhoto: (
    existing: PhotoUpdate,
    category: PhotoCategory,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ) -> Unit,
  onDeletePhoto: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategoryFilter by remember { mutableStateOf<PhotoCategory?>(null) }
  var showUploadDialog by remember { mutableStateOf(false) }
  var editingPhoto by remember { mutableStateOf<PhotoUpdate?>(null) }
  var previewPhoto by remember { mutableStateOf<PhotoUpdate?>(null) }

  val filteredPhotos = remember(data.photoUpdates, selectedCategoryFilter) {
    val filter = selectedCategoryFilter
    if (filter == null) data.photoUpdates else data.photoUpdates.filter { it.categoryEnum == filter }
  }

  if (showUploadDialog || editingPhoto != null) {
    val targetEdit = editingPhoto
    PhotoUploadDialog(
      existing = targetEdit,
      isBuyerMode = isBuyerMode,
      availableOrders = data.groupOrders,
      onDismiss = {
        showUploadDialog = false
        editingPhoto = null
      },
      onSubmit = { category, name, phone, orderId, orderName, caption, base64 ->
        if (targetEdit == null) {
          val role = if (isBuyerMode || category == PhotoCategory.TESTI) "BUYER" else "OWNER"
          onAddPhoto(category, role, name, phone, orderId, orderName, caption, base64)
        } else {
          onUpdatePhoto(targetEdit, category, name, phone, orderId, orderName, caption, base64)
        }
        showUploadDialog = false
        editingPhoto = null
      },
    )
  }

  previewPhoto?.let { photo ->
    PhotoLightboxDialog(
      photo = photo,
      onDismiss = { previewPhoto = null },
    )
  }

  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    // Header Banner
    item(key = "photos_header") {
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
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Collections,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Halaman Foto JIEGO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (isBuyerMode) {
                  "Cek bukti checkout, arrived warehouse, packing & unggah foto testi Anda."
                } else {
                  "Taruh foto proof checkout, arrived warehouse KR/China/INA, arrived admin packing & foto packing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Button(
            onClick = { showUploadDialog = true },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag(if (isBuyerMode) "buyer_upload_testi_button" else "owner_upload_photo_button"),
          ) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = if (isBuyerMode) {
                "Kirim Foto Testi (Nama + No. HP)"
              } else {
                "Unggah Foto Bukti / Warehouse / Packing"
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // Category Filter Chips
    item(key = "photos_filter_chips") {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          FilterChip(
            selected = selectedCategoryFilter == null,
            onClick = { selectedCategoryFilter = null },
            label = { Text("Semua (${data.photoUpdates.size})") },
            modifier = Modifier.testTag("photo_filter_all"),
          )
        }
        items(PhotoCategory.entries) { category ->
          val count = data.photoUpdates.count { it.categoryEnum == category }
          FilterChip(
            selected = selectedCategoryFilter == category,
            onClick = { selectedCategoryFilter = category },
            label = {
              Text(if (count > 0) "${category.shortLabel} ($count)" else category.shortLabel)
            },
            modifier = Modifier.testTag("photo_filter_${category.code}"),
          )
        }
      }
    }

    if (filteredPhotos.isEmpty()) {
      item(key = "empty_photos") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PhotoLibrary,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = if (selectedCategoryFilter == null) {
                "Belum Ada Foto Diunggah"
              } else {
                "Belum Ada ${selectedCategoryFilter!!.labelId}"
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (isBuyerMode) {
                "Pemilik akan mengunggah bukti checkout, warehouse KR/China, warehouse INA, & packing di sini. Anda juga bisa mengirim foto testi."
              } else {
                "Unggah bukti checkout, foto arrived warehouse KR/China, arrived warehouse INA, arrived admin packing, atau foto packing agar pembeli langsung mendapat notifikasi."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredPhotos, key = { it.id }) { photo ->
        PhotoUpdateCard(
          photo = photo,
          isBuyerMode = isBuyerMode,
          onPreviewClick = { previewPhoto = photo },
          onEditClick = { editingPhoto = photo },
          onDeleteClick = { onDeletePhoto(photo.id) },
        )
      }
    }

    item(key = "photos_wa_footer") {
      JiegoWhatsAppFooterCard(
        customMessageSuffix = "Saya ingin menanyakan update foto pesanan / packing di JIEGO.",
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoUpdateCard(
  photo: PhotoUpdate,
  isBuyerMode: Boolean,
  onPreviewClick: () -> Unit,
  onEditClick: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  val context = LocalContext.current
  val bitmap = ImageCodecHelper.rememberDecodedBase64Image(photo.imageBase64)
  val cat = photo.categoryEnum

  val (badgeBg, badgeFg) = when (cat) {
    PhotoCategory.PROOF_CHECKOUT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PhotoCategory.ARRIVED_WH_KR_CN,
    PhotoCategory.ARRIVED_WH_INA -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    PhotoCategory.ARRIVED_ADMIN_PACKING,
    PhotoCategory.PACKING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    PhotoCategory.TESTI -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
      .testTag("photo_card_${photo.id}"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = cat.labelId,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clickable(onClick = onPreviewClick),
        )
      } else {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
          )
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
          ) {
            StatusPill(text = cat.labelId, containerColor = badgeBg, contentColor = badgeFg)
            StatusPill(
              text = if (photo.isBuyerTesti) "Pembeli" else "Pemilik",
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = formatDateTime(photo.updatedAt?.toDate() ?: photo.createdAt?.toDate()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (photo.uploaderName.isNotBlank() || photo.uploaderPhone.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              if (photo.uploaderName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = photo.uploaderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              if (photo.uploaderPhone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = photo.uploaderPhone,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        if (photo.orderName.isNotBlank()) {
          Text(
            text = "Grup Order: ${photo.orderName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }

        if (photo.caption.isNotBlank()) {
          Text(
            text = photo.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        if (!isBuyerMode) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = onEditClick,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("edit_photo_${photo.id}"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(6.dp))
              Text("Ganti / Edit Foto")
            }
            IconButton(
              onClick = onDeleteClick,
              modifier = Modifier
                .size(48.dp)
                .testTag("delete_photo_${photo.id}"),
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Hapus foto",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        } else if (cat == PhotoCategory.ARRIVED_ADMIN_PACKING || cat == PhotoCategory.PACKING) {
          OutlinedButton(
            onClick = {
              val msg = buildString {
                append("Halo Admin Packing ER, saya mau konfirmasi foto ${cat.labelId}")
                if (photo.orderName.isNotBlank()) append(" untuk ${photo.orderName}")
                append(".")
              }
              JiegoContacts.openWhatsApp(context, JiegoContacts.ADMIN_PACKING_ER, msg)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
          ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Chat Admin Packing ER (08816623574)")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PhotoUploadDialog(
  existing: PhotoUpdate?,
  isBuyerMode: Boolean,
  availableOrders: List<GroupOrder>,
  onDismiss: () -> Unit,
  onSubmit: (
    category: PhotoCategory,
    uploaderName: String,
    uploaderPhone: String,
    orderId: String,
    orderName: String,
    caption: String,
    imageBase64: String,
  ) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val isBuyerUpload = isBuyerMode || (existing?.categoryEnum == PhotoCategory.TESTI)
  val ownerCategories = remember { PhotoCategory.entries.filter { it.isOwnerCategory } }

  var selectedCategory by remember {
    mutableStateOf(
      existing?.categoryEnum ?: if (isBuyerMode) PhotoCategory.TESTI else PhotoCategory.PROOF_CHECKOUT
    )
  }
  var selectedOrder by remember {
    mutableStateOf(availableOrders.find { it.id == existing?.orderId })
  }
  var goDropdownExpanded by remember { mutableStateOf(false) }

  var uploaderName by remember {
    mutableStateOf(
      existing?.uploaderName ?: if (isBuyerMode) "" else "Owner JIEJIE"
    )
  }
  var uploaderPhone by remember {
    mutableStateOf(existing?.uploaderPhone ?: "")
  }
  var caption by remember { mutableStateOf(existing?.caption ?: "") }
  var imageBase64 by remember { mutableStateOf(existing?.imageBase64 ?: "") }
  var isProcessingImage by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val previewBitmap = ImageCodecHelper.rememberDecodedBase64Image(imageBase64)

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
        } else {
          errorText = "Gagal membaca atau mengompres gambar yang dipilih."
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = when {
          existing != null -> "Perbarui Foto"
          isBuyerUpload -> "Kirim Foto Testi Pembeli"
          else -> "Unggah Foto Bukti / Packing"
        },
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
        if (!isBuyerMode && existing?.categoryEnum != PhotoCategory.TESTI) {
          Text("Pilih Jenis Foto (Pemilik):", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            ownerCategories.forEach { cat ->
              FilterChip(
                selected = selectedCategory == cat,
                onClick = { selectedCategory = cat },
                label = { Text(cat.shortLabel) },
                modifier = Modifier.testTag("upload_cat_${cat.code}"),
              )
            }
          }
        }

        // Photo Picker Box
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
            .testTag("pick_photo_box"),
        ) {
          if (previewBitmap != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
              Image(
                bitmap = previewBitmap,
                contentDescription = "Preview foto dipilih",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(170.dp),
              )
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(8.dp),
              ) {
                Text(
                  text = "Ketuk untuk ganti foto",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
              )
              Text(
                text = if (isProcessingImage) "Memproses foto..." else "Ketuk untuk Pilih Foto dari Galeri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        if (selectedCategory == PhotoCategory.TESTI || isBuyerMode) {
          OutlinedTextField(
            value = uploaderName,
            onValueChange = { uploaderName = it.take(80) },
            label = { Text("Nama Pembeli *") },
            placeholder = { Text("Masukkan nama lengkap / panggilan") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("testi_name_input"),
          )
          OutlinedTextField(
            value = uploaderPhone,
            onValueChange = { v -> uploaderPhone = v.filter { it.isDigit() || it == '+' }.take(20) },
            label = { Text("Nomor HP / WhatsApp *") },
            placeholder = { Text("08xxxxxxxxxx") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("testi_phone_input"),
          )
        } else {
          OutlinedTextField(
            value = uploaderName,
            onValueChange = { uploaderName = it.take(80) },
            label = { Text("Diunggah Oleh") },
            placeholder = { Text("Owner JIEJIE / Admin Packing ER") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        if (availableOrders.isNotEmpty()) {
          ExposedDropdownMenuBox(
            expanded = goDropdownExpanded,
            onExpandedChange = { goDropdownExpanded = !goDropdownExpanded },
          ) {
            OutlinedTextField(
              value = selectedOrder?.name ?: "Umum (Semua Grup Order)",
              onValueChange = {},
              readOnly = true,
              label = { Text("Tautkan ke Grup Order (Opsional)") },
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
                text = { Text("Umum (Semua Grup Order)") },
                onClick = {
                  selectedOrder = null
                  goDropdownExpanded = false
                },
              )
              availableOrders.forEach { go ->
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
        }

        OutlinedTextField(
          value = caption,
          onValueChange = { caption = it.take(300) },
          label = {
            Text(
              if (selectedCategory == PhotoCategory.TESTI) "Pesan / Testimoni Pembeli"
              else "Keterangan / Info Resi / Batch"
            )
          },
          placeholder = {
            Text(
              if (selectedCategory == PhotoCategory.TESTI) "Paket aman, packing rapi, makasih JIEGO!"
              else "Contoh: Batch 1 sudah sampai warehouse Korea"
            )
          },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("photo_caption_input"),
        )

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Notifikasi otomatis masuk ke riwayat & bar perangkat saat foto dikirim.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        if (errorText != null) {
          Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        enabled = !isProcessingImage,
        onClick = {
          if (imageBase64.isBlank()) {
            errorText = "Silakan pilih foto terlebih dahulu."
            return@Button
          }
          if (selectedCategory == PhotoCategory.TESTI) {
            if (uploaderName.isBlank()) {
              errorText = "Nama pembeli wajib diisi."
              return@Button
            }
            if (uploaderPhone.trim().length < 5) {
              errorText = "Nomor HP pembeli wajib diisi (minimal 5 angka)."
              return@Button
            }
          }
          onSubmit(
            selectedCategory,
            uploaderName.ifBlank { if (selectedCategory == PhotoCategory.TESTI) "Pembeli" else "Owner JIEJIE" },
            uploaderPhone,
            selectedOrder?.id ?: "",
            selectedOrder?.name ?: "",
            caption,
            imageBase64,
          )
        },
        modifier = Modifier.testTag("submit_photo_button"),
      ) {
        Text(if (existing == null) "Kirim Foto" else "Simpan Perubahan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@Composable
private fun PhotoLightboxDialog(
  photo: PhotoUpdate,
  onDismiss: () -> Unit,
) {
  val bitmap = ImageCodecHelper.rememberDecodedBase64Image(photo.imageBase64)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(photo.categoryEnum.labelId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        if (bitmap != null) {
          Image(
            bitmap = bitmap,
            contentDescription = photo.categoryEnum.labelId,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 220.dp, max = 380.dp)
              .clip(RoundedCornerShape(14.dp)),
          )
        }
        if (photo.uploaderName.isNotBlank()) {
          val phoneLabel = if (photo.uploaderPhone.isNotBlank()) " (${photo.uploaderPhone})" else ""
          Text(
            text = "Pengunggah: ${photo.uploaderName}$phoneLabel",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        if (photo.orderName.isNotBlank()) {
          Text("Grup Order: ${photo.orderName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (photo.caption.isNotBlank()) {
          Text(photo.caption, style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}
