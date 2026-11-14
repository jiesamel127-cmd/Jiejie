package com.example.ui.components

import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.OrderItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberSearchMatcherTest {

  private val yuniaMember = MemberOrder(
    id = "m1",
    orderId = "go1",
    memberName = "yunia",
    memberHandle = "85175452490",
    totalItemsCost = 152_000L,
  )

  private val rithaMember = MemberOrder(
    id = "m2",
    orderId = "go1",
    memberName = "ritha, RBUY!",
    memberHandle = "6285792440587",
    totalItemsCost = 120_000L,
  )

  private val elanMember = MemberOrder(
    id = "m3",
    orderId = "go2",
    memberName = "elan",
    memberHandle = "0812-3456-7890",
    totalItemsCost = 155_000L,
  )

  private val parentOrder1 = GroupOrder(
    id = "go1",
    name = "WAR BATCH 1",
    artistGroup = "WAR",
    batchCode = "BATCH-1",
  )

  @Test
  fun matchesBuyerName_caseAndPunctuationInsensitive() {
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "Yunia"))
    assertTrue(MemberSearchMatcher.matchesMember(rithaMember, "ritha rbuy"))
    assertTrue(MemberSearchMatcher.matchesMember(rithaMember, "RBUY"))
    assertFalse(MemberSearchMatcher.matchesMember(yuniaMember, "nadia"))
  }

  @Test
  fun matchesIndonesianPhoneNumbers_acrossAllCommonFormats() {
    // Member stores 85175452490 -> user searches 0851..., 62851..., +62 851-..., or middle digits
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "085175452490"))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "6285175452490"))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "+62 851-7545-2490"))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "8517545"))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "2490"))

    // Member stores 6285792440587 -> user searches 0857...
    assertTrue(MemberSearchMatcher.matchesMember(rithaMember, "085792440587"))
    assertTrue(MemberSearchMatcher.matchesMember(rithaMember, "0857-9244-0587"))
    assertTrue(MemberSearchMatcher.matchesMember(rithaMember, "+62857"))

    // Member stores 0812-3456-7890 -> user searches 62812...
    assertTrue(MemberSearchMatcher.matchesMember(elanMember, "6281234567890"))
    assertTrue(MemberSearchMatcher.matchesMember(elanMember, "0812"))
    assertFalse(MemberSearchMatcher.matchesMember(elanMember, "0899999999"))
  }

  @Test
  fun matchesMultiTokenAndItemNamesAndOrderContext() {
    val items = listOf(
      OrderItem(id = "i1", orderId = "go1", memberId = "m1", itemName = "PC Wonwoo", variant = "HEAVEN")
    )
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "yunia wonwoo", items, parentOrder1))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "yunia 0851", items, parentOrder1))
    assertTrue(MemberSearchMatcher.matchesMember(yuniaMember, "batch 0851", items, parentOrder1))
    assertFalse(MemberSearchMatcher.matchesMember(yuniaMember, "yunia 0899", items, parentOrder1))
  }
}
