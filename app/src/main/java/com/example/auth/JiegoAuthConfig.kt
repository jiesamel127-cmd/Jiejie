package com.example.auth

object JiegoAuthConfig {
  const val OWNER_EMAIL = "jiego4sho@gmail.com"
  const val STORE_ID = "jiego"

  fun isOwnerEmail(email: String?): Boolean {
    return email?.trim()?.equals(OWNER_EMAIL, ignoreCase = true) == true
  }
}
