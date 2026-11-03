package com.kampplus.hava.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Kartta görünen alt yazının ([City.subtitle]) kurallarını doğrular. */
class CityTest {

    private fun city(countryCode: String?, country: String?, admin1: String?) = City(
        id = 1L,
        name = "Ankara",
        latitude = 0.0,
        longitude = 0.0,
        countryCode = countryCode,
        country = country,
        admin1 = admin1
    )

    @Test
    fun `turkiye resmi adi kisa adla gosterilir`() {
        val ankara = city(countryCode = "TR", country = "Türkiye Cumhuriyeti", admin1 = "Ankara")

        assertEquals("Ankara, Türkiye", ankara.subtitle)
    }

    @Test
    fun `diger ulkelerin adi degistirilmez`() {
        val berlin = city(countryCode = "DE", country = "Almanya", admin1 = "Berlin")

        assertEquals("Berlin, Almanya", berlin.subtitle)
    }

    @Test
    fun `bolge yoksa yalnizca ulke yazilir`() {
        val city = city(countryCode = "TR", country = "Türkiye Cumhuriyeti", admin1 = null)

        assertEquals("Türkiye", city.subtitle)
    }
}
