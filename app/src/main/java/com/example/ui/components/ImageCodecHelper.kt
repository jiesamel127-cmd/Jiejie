package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCodecHelper {

  private val bitmapCache = LruCache<String, ImageBitmap>(24)

  private fun cacheKey(base64: String): String {
    return "${base64.length}_${base64.hashCode()}"
  }

  fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
    if (base64.isBlank()) return null
    val key = cacheKey(base64)
    bitmapCache.get(key)?.let { return it }
    return runCatching {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { decoded ->
        bitmapCache.put(key, decoded)
      }
    }.getOrNull()
  }

  @Composable
  fun rememberDecodedBase64Image(base64: String): ImageBitmap? {
    val cached = if (base64.isBlank()) null else bitmapCache.get(cacheKey(base64))
    val state by produceState<ImageBitmap?>(initialValue = cached, key1 = base64) {
      value = if (base64.isBlank()) {
        null
      } else {
        bitmapCache.get(cacheKey(base64)) ?: withContext(Dispatchers.Default) {
          decodeBase64ToImageBitmap(base64)
        }
      }
    }
    return state
  }

  suspend fun encodeUriToBase64Jpeg(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

      val maxDim = 800
      var inSampleSize = 1
      while (bounds.outWidth / inSampleSize > maxDim * 2 || bounds.outHeight / inSampleSize > maxDim * 2) {
        inSampleSize *= 2
      }

      val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
      val rawBitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOpts)
      } ?: return@withContext null

      val scaled = scaleBitmapToMax(rawBitmap, maxDim)
      val out = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
      var b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

      if (b64.length > 480_000) {
        val smaller = scaleBitmapToMax(scaled, 560)
        val retryOut = ByteArrayOutputStream()
        smaller.compress(Bitmap.CompressFormat.JPEG, 50, retryOut)
        b64 = Base64.encodeToString(retryOut.toByteArray(), Base64.NO_WRAP)
        if (smaller !== scaled && !smaller.isRecycled) smaller.recycle()
      }

      if (scaled !== rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
      if (!scaled.isRecycled) scaled.recycle()
      b64
    }.getOrNull()
  }

  private fun scaleBitmapToMax(source: Bitmap, maxDim: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxDim && h <= maxDim) return source
    val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
    val targetW = (w * ratio).toInt().coerceAtLeast(1)
    val targetH = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
  }
}
