package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ExchangeRates
import com.example.data.model.RateCountry
import com.example.ui.screens.BillRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun formatRateValue(rate: Double): String {
  val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID"))
  return if (abs(rate - rate.roundToLong().toDouble()) < 0.0001) {
    val df = DecimalFormat("#,##0", symbols)
    "Rp ${df.format(rate.roundToLong())}"
  } else {
    val df = DecimalFormat("#,##0.00", symbols)
    "Rp ${df.format(rate)}"
  }
}

fun formatRatePlainInput(rate: Double): String {
  return if (abs(rate - rate.roundToLong().toDouble()) < 0.0001) {
    rate.roundToLong().toString()
  } else {
    String.format(Locale.US, "%.2f", rate)
  }
}

private fun parseDecimalInput(raw: String): Double {
  val normalized = raw.trim().replace(',', '.')
  return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
}

private fun filterDecimalChars(raw: String): String {
  var hasSep = false
  return buildString {
    raw.forEach { ch ->
      when {
        ch.isDigit() -> append(ch)
        (ch == '.' || ch == ',') && !hasSep -> {
          hasSep = true
          append('.')
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JiegoRatesSummaryCard(
  rates: ExchangeRates,
  onOpenCalculator: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onOpenCalculator)
      .testTag("dashboard_rates_card"),
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.CurrencyExchange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Column {
            Text(
              text = "Rate Kurs & Kalkulator Harga Kotor",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Ketuk untuk hitung harga kotor barang luar negeri",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Calculate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Hitung",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        RateCountry.entries.forEach { country ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text(
                text = country.countryName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = formatRateValue(rates.rateFor(country)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JiegoRatesCalculatorDialog(
  rates: ExchangeRates,
  isBuyerMode: Boolean,
  onSaveRates: (ExchangeRates) -> Unit,
  onResetRates: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var selectedCountry by remember { mutableStateOf(RateCountry.KOREA) }
  var foreignPriceStr by remember { mutableStateOf("") }
  var quantityStr by remember { mutableStateOf("1") }
  var domesticShipStr by remember { mutableStateOf("") }
  var extraFeeIdrStr by remember { mutableStateOf("") }
  var copiedFeedback by remember { mutableStateOf(false) }

  var isEditingRates by remember { mutableStateOf(false) }
  var editChina by remember(rates) { mutableStateOf(formatRatePlainInput(rates.china)) }
  var editKorea by remember(rates) { mutableStateOf(formatRatePlainInput(rates.korea)) }
  var editThailand by remember(rates) { mutableStateOf(formatRatePlainInput(rates.thailand)) }
  var editJepang by remember(rates) { mutableStateOf(formatRatePlainInput(rates.jepang)) }
  var editVietnam by remember(rates) { mutableStateOf(formatRatePlainInput(rates.vietnam)) }
  var editTaiwan by remember(rates) { mutableStateOf(formatRatePlainInput(rates.taiwan)) }
  var rateError by remember { mutableStateOf<String?>(null) }

  val foreignPrice = parseDecimalInput(foreignPriceStr)
  val qty = (quantityStr.toLongOrNull() ?: 1L).coerceAtLeast(1L)
  val domesticShip = parseDecimalInput(domesticShipStr)
  val extraFeeIdr = (extraFeeIdrStr.toLongOrNull() ?: 0L).coerceAtLeast(0L)
  val activeRate = rates.rateFor(selectedCountry)
  val itemsIdr = kotlin.math.round((foreignPrice * qty) * activeRate).toLong().coerceAtLeast(0L)
  val shipIdr = kotlin.math.round(domesticShip * activeRate).toLong().coerceAtLeast(0L)
  val grossIdr = rates.calculateGrossIdr(
    country = selectedCountry,
    foreignUnitPrice = foreignPrice,
    quantity = qty,
    foreignDomesticShipping = domesticShip,
    extraFeeIdr = extraFeeIdr,
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Kalkulator Harga Kotor & Rate", style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Pilih negara asal barang untuk menghitung harga kotor ke Rupiah (IDR):",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          RateCountry.entries.forEach { country ->
            FilterChip(
              selected = selectedCountry == country,
              onClick = {
                selectedCountry = country
                copiedFeedback = false
              },
              label = {
                Text("${country.countryName} (${formatRatePlainInput(rates.rateFor(country))})")
              },
              modifier = Modifier.testTag("rate_chip_${country.key}"),
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Text(
              text = "Rate ${selectedCountry.countryName} (${selectedCountry.currencyCode})",
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = "1 ${selectedCountry.currencyCode} = ${formatRateValue(activeRate)}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = foreignPriceStr,
            onValueChange = {
              foreignPriceStr = filterDecimalChars(it).take(12)
              copiedFeedback = false
            },
            label = { Text("Harga Web (${selectedCountry.currencyCode})") },
            placeholder = { Text("Misal: 150") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
              .weight(1.4f)
              .testTag("calc_foreign_price_input"),
          )
          OutlinedTextField(
            value = quantityStr,
            onValueChange = {
              quantityStr = it.filter { ch -> ch.isDigit() }.take(4)
              copiedFeedback = false
            },
            label = { Text("Qty") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
              .weight(0.6f)
              .testTag("calc_qty_input"),
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = domesticShipStr,
            onValueChange = {
              domesticShipStr = filterDecimalChars(it).take(10)
              copiedFeedback = false
            },
            label = { Text("Ongkir Web (${selectedCountry.currencyCode})") },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("calc_domestic_ship_input"),
          )
          OutlinedTextField(
            value = extraFeeIdrStr,
            onValueChange = {
              extraFeeIdrStr = it.filter { ch -> ch.isDigit() }.take(8)
              copiedFeedback = false
            },
            label = { Text("Fee Tambahan (Rp)") },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("calc_extra_fee_input"),
          )
        }

        // Result Card
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("calc_result_card"),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp),
          ) {
            Text(
              text = "ESTIMASI HARGA KOTOR (IDR)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = formatIdr(grossIdr),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.testTag("calc_gross_idr_result"),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            BillRow(
              label = "Harga Barang (${qty}x × ${formatRateValue(activeRate)})",
              value = formatIdr(itemsIdr),
              valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (shipIdr > 0L) {
              BillRow(
                label = "Ongkir Web (${domesticShipStr} ${selectedCountry.currencyCode})",
                value = formatIdr(shipIdr),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            if (extraFeeIdr > 0L) {
              BillRow(
                label = "Fee / Admin Tambahan",
                value = formatIdr(extraFeeIdr),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Text(
              text = "*Harga kotor belum termasuk EMS/Cargo internasional & tax (jika ada).",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
          }
        }

        if (grossIdr > 0L) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = {
                val summary = buildString {
                  append("Hitung Harga Kotor JIEGO (${selectedCountry.countryName} • Rate ${formatRatePlainInput(activeRate)})\n")
                  append("Harga Web: $foreignPriceStr ${selectedCountry.currencyCode} × $qty\n")
                  if (domesticShip > 0.0) append("Ongkir Web: $domesticShipStr ${selectedCountry.currencyCode}\n")
                  if (extraFeeIdr > 0L) append("Fee Tambahan: ${formatIdr(extraFeeIdr)}\n")
                  append("Total Harga Kotor: ${formatIdr(grossIdr)}")
                }
                clipboard.setText(AnnotatedString(summary))
                copiedFeedback = true
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("calc_copy_button"),
            ) {
              Text(if (copiedFeedback) "Tersalin!" else "Salin Hitungan")
            }

            if (isBuyerMode) {
              Button(
                onClick = {
                  val msg = buildString {
                    append("Halo Owner JIEJIE, saya mau tanya/titip order barang dari ${selectedCountry.countryName}:\n")
                    append("Harga Web: $foreignPriceStr ${selectedCountry.currencyCode} (Qty $qty)\n")
                    append("Estimasi Harga Kotor (Rate ${formatRatePlainInput(activeRate)}): ${formatIdr(grossIdr)}")
                  }
                  JiegoContacts.openWhatsApp(context, JiegoContacts.OWNER_JIEJIE, msg)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("calc_wa_owner_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Order via WA", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }

        HorizontalDivider()

        // Rate Table & Owner Edit Section
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Daftar Rate Resmi JIEGO",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          if (!isBuyerMode) {
            TextButton(
              onClick = { isEditingRates = !isEditingRates },
              modifier = Modifier.testTag("toggle_edit_rates_button"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (isEditingRates) "Tutup Edit" else "Ubah Rate")
            }
          }
        }

        if (!isEditingRates) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RateCountry.entries.forEach { c ->
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              ) {
                Text("${c.countryName} (${c.currencyCode})", style = MaterialTheme.typography.bodySmall)
                Text(
                  text = formatRateValue(rates.rateFor(c)),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = editChina,
                onValueChange = { editChina = filterDecimalChars(it).take(10) },
                label = { Text("China (CNY)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = editKorea,
                onValueChange = { editKorea = filterDecimalChars(it).take(10) },
                label = { Text("Korea (KRW)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = editThailand,
                onValueChange = { editThailand = filterDecimalChars(it).take(10) },
                label = { Text("Thailand (THB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = editJepang,
                onValueChange = { editJepang = filterDecimalChars(it).take(10) },
                label = { Text("Jepang (JPY)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = editVietnam,
                onValueChange = { editVietnam = filterDecimalChars(it).take(10) },
                label = { Text("Vietnam (VND)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = editTaiwan,
                onValueChange = { editTaiwan = filterDecimalChars(it).take(10) },
                label = { Text("Taiwan (TWD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            if (rateError != null) {
              Text(rateError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              OutlinedButton(
                onClick = {
                  onResetRates()
                  isEditingRates = false
                  rateError = null
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("reset_rates_button"),
              ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Default")
              }
              Button(
                onClick = {
                  val c = parseDecimalInput(editChina)
                  val k = parseDecimalInput(editKorea)
                  val t = parseDecimalInput(editThailand)
                  val j = parseDecimalInput(editJepang)
                  val v = parseDecimalInput(editVietnam)
                  val tw = parseDecimalInput(editTaiwan)
                  if (c <= 0 || k <= 0 || t <= 0 || j <= 0 || v <= 0 || tw <= 0) {
                    rateError = "Semua rate harus lebih dari 0."
                    return@Button
                  }
                  rateError = null
                  onSaveRates(
                    rates.copy(
                      china = c,
                      korea = k,
                      thailand = t,
                      jepang = j,
                      vietnam = v,
                      taiwan = tw,
                    )
                  )
                  isEditingRates = false
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("save_rates_button"),
              ) {
                Text("Simpan Rate")
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineRateConverterHelper(
  rates: ExchangeRates,
  onApplyIdrPrice: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  var selectedCountry by remember { mutableStateOf(RateCountry.KOREA) }
  var foreignAmountStr by remember { mutableStateOf("") }

  val foreignAmount = parseDecimalInput(foreignAmountStr)
  val convertedIdr = rates.calculateGrossIdr(selectedCountry, foreignAmount)

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    TextButton(
      onClick = { expanded = !expanded },
      modifier = Modifier.testTag("inline_rate_helper_toggle"),
    ) {
      Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text(if (expanded) "Tutup Hitung Rate Asing" else "Hitung Harga Kotor dari Rate Luar Negeri")
    }

    AnimatedVisibility(visible = expanded) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(10.dp),
        ) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            RateCountry.entries.forEach { country ->
              FilterChip(
                selected = selectedCountry == country,
                onClick = { selectedCountry = country },
                label = { Text("${country.countryName} (${formatRatePlainInput(rates.rateFor(country))})") },
              )
            }
          }
          OutlinedTextField(
            value = foreignAmountStr,
            onValueChange = { foreignAmountStr = filterDecimalChars(it).take(12) },
            label = { Text("Harga (${selectedCountry.currencyCode})") },
            placeholder = { Text("Contoh: 150") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (convertedIdr > 0L) {
            Button(
              onClick = {
                onApplyIdrPrice(convertedIdr)
                expanded = false
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("apply_inline_rate_button"),
            ) {
              Text("Pakai Harga Kotor: ${formatIdr(convertedIdr)}")
            }
          }
        }
      }
    }
  }
}
