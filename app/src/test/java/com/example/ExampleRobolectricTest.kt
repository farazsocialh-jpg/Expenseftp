package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Expense Manager", appName)
  }

  @Test
  fun `verify Qatar Commercial Bank SMS template parsing`() {
    val smsBody = """
      Your card ending **2511
      used for QAR 30.25
      at NEW TAIF HYPERMARKET
      at 21:20
      28-May-26
      Available Limit: QAR 714.18
    """.trimIndent()

    val parsed = com.example.util.SmsParser.parseMessage("Cb SMS", smsBody)
    org.junit.Assert.assertNotNull("Parsed transaction must not be null", parsed)
    assertEquals(30.25, parsed!!.amount, 0.001)
    assertEquals("DEBIT", parsed.type)
    assertEquals("Shopping", parsed.category)
    assertEquals("groceries", parsed.tag)
    assertEquals("Taif Hypermarket", parsed.merchant)
    assertEquals("Bank", parsed.account)
  }

  @Test
  fun `verify robust sender matching with various carrier formats`() {
    val robustHelper = com.example.util.SmsParser
    org.junit.Assert.assertTrue(robustHelper.isSameSender("CB-SMS", "Cb SMS"))
    org.junit.Assert.assertTrue(robustHelper.isSameSender("Cb_SMS", "Cb SMS"))
    org.junit.Assert.assertTrue(robustHelper.isSameSender("CB-SMS-QD", "Cb SMS"))
    org.junit.Assert.assertTrue(robustHelper.isSameSender("CBSMS", "Cb SMS"))
  }
}
