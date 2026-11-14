package com.example.ui.components

import com.example.data.model.GroupOrder
import com.example.data.model.MemberOrder
import com.example.data.model.OrderItem

/**
 * Pencocokan pencarian pembeli berbasis nama, handle/akun, dan nomor HP Indonesia
 * (otomatis menyamakan format 08..., 628..., +62 8..., tanda hubung, dan spasi).
 */
object MemberSearchMatcher {

  private val WHITESPACE_REGEX = Regex("\\s+")
  private val NON_ALNUM_REGEX = Regex("[^a-z0-9\\s@]")

  fun matchesMember(
    member: MemberOrder,
    query: String,
    memberItems: List<OrderItem> = emptyList(),
    parentOrder: GroupOrder? = null,
  ): Boolean {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isEmpty()) return true

    // If user typed a phone-like string (including spaces/dashes/+62), match as full normalized phone
    val queryDigits = cleanQuery.filter { it.isDigit() }
    val isPhoneLikeQuery = queryDigits.length >= 3 &&
      cleanQuery.all { it.isDigit() || it in "+-(). " }
    if (isPhoneLikeQuery) {
      if (matchesPhoneDigits(member.memberHandle, queryDigits)) return true
      if (matchesPhoneDigits(member.memberName, queryDigits)) return true
      if (matchesPhoneDigits(member.notes, queryDigits)) return true
    }

    val tokens = cleanQuery.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true

    val cleanMemberName = normalizeLooseText(member.memberName)
    val rawMemberName = member.memberName.lowercase()
    val cleanHandle = normalizeLooseText(member.memberHandle)
    val rawHandle = member.memberHandle.lowercase()
    val rawNotes = member.notes.lowercase()
    val orderText = buildString {
      if (parentOrder != null) {
        append(parentOrder.name.lowercase())
        append(' ')
        append(parentOrder.artistGroup.lowercase())
        append(' ')
        append(parentOrder.batchCode.lowercase())
      }
    }
    val itemsText = if (memberItems.isEmpty()) {
      ""
    } else {
      memberItems.joinToString(" ") { "${it.itemName.lowercase()} ${it.variant.lowercase()}" }
    }

    return tokens.all { token ->
      val cleanToken = normalizeLooseText(token)
      val tokenDigits = token.filter { it.isDigit() }

      rawMemberName.contains(token) ||
        (cleanToken.isNotEmpty() && cleanMemberName.contains(cleanToken)) ||
        rawHandle.contains(token) ||
        (cleanToken.isNotEmpty() && cleanHandle.contains(cleanToken)) ||
        (tokenDigits.length >= 3 && matchesPhoneDigits(member.memberHandle, tokenDigits)) ||
        (tokenDigits.length >= 3 && matchesPhoneDigits(member.memberName, tokenDigits)) ||
        rawNotes.contains(token) ||
        (itemsText.isNotEmpty() && itemsText.contains(token)) ||
        (orderText.isNotEmpty() && orderText.contains(token))
    }
  }

  fun matchesPhoneDigits(rawTarget: String, queryDigits: String): Boolean {
    if (queryDigits.length < 3 || rawTarget.isBlank()) return false
    val targetVariants = phoneDigitVariants(rawTarget)
    if (targetVariants.isEmpty()) return false
    val queryVariants = phoneDigitVariants(queryDigits)
    return targetVariants.any { target ->
      queryVariants.any { q -> target.contains(q) }
    }
  }

  fun phoneDigitVariants(raw: String): Set<String> {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return emptySet()

    val variants = linkedSetOf(digits)
    when {
      digits.startsWith("62") && digits.length >= 4 -> {
        val core = digits.drop(2)
        variants.add(core)
        variants.add("0$core")
      }
      digits.startsWith("0") && digits.length >= 4 -> {
        val core = digits.drop(1)
        variants.add(core)
        variants.add("62$core")
      }
      digits.startsWith("8") && digits.length >= 8 -> {
        variants.add("0$digits")
        variants.add("62$digits")
      }
    }
    return variants
  }

  private fun normalizeLooseText(raw: String): String {
    return raw.lowercase()
      .replace(NON_ALNUM_REGEX, " ")
      .replace(WHITESPACE_REGEX, " ")
      .trim()
  }
}
