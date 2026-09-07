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
    assertEquals("SuperNova SNES", appName)
  }

  @Test
  fun `verify snes button bitmasks`() {
    assertEquals(1 shl 0, com.example.model.SnesButton.B.mask)
    assertEquals(1 shl 8, com.example.model.SnesButton.A.mask)
    assertEquals(1 shl 1, com.example.model.SnesButton.Y.mask)
    assertEquals(1 shl 9, com.example.model.SnesButton.X.mask)
    assertEquals(1 shl 3, com.example.model.SnesButton.START.mask)
  }
}
