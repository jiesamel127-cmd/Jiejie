package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExchangeRatesTest {

  @Test
  fun defaultRates_matchUserRequestedJiegoRates() {
    val rates = ExchangeRates()

    assertEquals(2750.0, rates.rateFor(RateCountry.CHINA), 0.0001)
    assertEquals(14.0, rates.rateFor(RateCountry.KOREA), 0.0001)
    assertEquals(547.0, rates.rateFor(RateCountry.THAILAND), 0.0001)
    assertEquals(118.0, rates.rateFor(RateCountry.JEPANG), 0.0001)
    assertEquals(0.80, rates.rateFor(RateCountry.VIETNAM), 0.0001)
    assertEquals(590.0, rates.rateFor(RateCountry.TAIWAN), 0.0001)
  }

  @Test
  fun calculateGrossIdr_computesEachCountryWithShippingAndExtraFee() {
    val rates = ExchangeRates()

    // China: (100 CNY * 2 + 12 CNY shipping) * 2750 + 5000 IDR = 583,000 + 5,000 = 588,000
    assertEquals(
      588_000L,
      rates.calculateGrossIdr(
        country = RateCountry.CHINA,
        foreignUnitPrice = 100.0,
        quantity = 2L,
        foreignDomesticShipping = 12.0,
        extraFeeIdr = 5_000L,
      ),
    )

    // Korea: (25000 KRW * 1 + 3000 KRW) * 14 = 392,000
    assertEquals(
      392_000L,
      rates.calculateGrossIdr(
        country = RateCountry.KOREA,
        foreignUnitPrice = 25_000.0,
        quantity = 1L,
        foreignDomesticShipping = 3_000.0,
      ),
    )

    // Thailand: 450 THB * 547 = 246,150
    assertEquals(
      246_150L,
      rates.calculateGrossIdr(
        country = RateCountry.THAILAND,
        foreignUnitPrice = 450.0,
      ),
    )

    // Jepang: 3500 JPY * 118 = 413,000
    assertEquals(
      413_000L,
      rates.calculateGrossIdr(
        country = RateCountry.JEPANG,
        foreignUnitPrice = 3_500.0,
      ),
    )

    // Vietnam: 250,000 VND * 0.80 = 200,000
    assertEquals(
      200_000L,
      rates.calculateGrossIdr(
        country = RateCountry.VIETNAM,
        foreignUnitPrice = 250_000.0,
      ),
    )

    // Taiwan: 800 TWD * 590 = 472,000
    assertEquals(
      472_000L,
      rates.calculateGrossIdr(
        country = RateCountry.TAIWAN,
        foreignUnitPrice = 800.0,
      ),
    )
  }

  @Test
  fun withRate_updatesSingleCountryAndClampsInvalidValues() {
    val updated = ExchangeRates().withRate(RateCountry.VIETNAM, 0.85)
    assertEquals(0.85, updated.vietnam, 0.0001)
    assertEquals(14.0, updated.korea, 0.0001)

    val fallbackZero = ExchangeRates(korea = 0.0)
    assertEquals(14.0, fallbackZero.rateFor(RateCountry.KOREA), 0.0001)
  }
}
