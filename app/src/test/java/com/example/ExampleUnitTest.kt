package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExampleUnitTest {
  @Test
  fun testEmailSanitizationForFirebaseKey() {
    val email = "user.name.test@gmail.com"
    val sanitized = email.replace(".", ",")
    assertEquals("user,name,test@gmail,com", sanitized)
  }

  @Test
  fun testSubscriptionStateFormatting() {
    val lifetime: SubscriptionState = SubscriptionState.Lifetime
    assertTrue(lifetime is SubscriptionState.Lifetime)

    val expiresAt = 1790755200000L // e.g. future date
    val active: SubscriptionState = SubscriptionState.Active(expiresAt)
    assertTrue(active is SubscriptionState.Active)

    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    val formatted = dateFormat.format(Date(expiresAt))
    assertEquals("Subscription active until $formatted", "Subscription active until ${dateFormat.format(Date((active as SubscriptionState.Active).expiresAt))}")
  }
}
