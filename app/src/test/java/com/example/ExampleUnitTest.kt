package com.example

import com.example.auth.JiegoAuthConfig
import com.example.ui.components.JiegoContacts
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun jiegoContacts_includeShopeeAndAllAdmins() {
    assertEquals("https://id.shp.ee/dABi8Yvg", JiegoContacts.SHOPEE_URL)
    assertEquals("08816623574", JiegoContacts.ADMIN_PACKING_ER.displayNumber)
    assertEquals(3, JiegoContacts.ALL.size)
  }

  @Test
  fun jiegoAuthConfig_onlyAllowsOfficialOwnerEmail() {
    assertTrue(JiegoAuthConfig.isOwnerEmail("jiego4sho@gmail.com"))
    assertTrue(JiegoAuthConfig.isOwnerEmail("  JIEGO4SHO@GMAIL.COM  "))
    assertFalse(JiegoAuthConfig.isOwnerEmail("pembeli@gmail.com"))
    assertFalse(JiegoAuthConfig.isOwnerEmail("jiesamel127@gmail.com"))
    assertFalse(JiegoAuthConfig.isOwnerEmail(null))
    assertFalse(JiegoAuthConfig.isOwnerEmail(""))
  }
}
