package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastLogicTest {

    private val baseBroadcast = Broadcast(
        id = "test-bc-1",
        title = "Test Announcement",
        body = "Hello World",
        emoji = "🎉",
        accent = "info",
        audience = "all",
        rule = "count",
        maxShows = 1,
        expiresAt = 0L,
        status = "active",
        createdAt = 1000L
    )

    @Test
    fun testCount1NotYetShown() {
        val b = baseBroadcast.copy(rule = "count", maxShows = 1)
        val receipt = BroadcastReceipt(shownCount = 0)
        val show = shouldShow(
            b = b,
            receipt = receipt,
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertTrue("count=1 with 0 impressions should show", show)
    }

    @Test
    fun testCount1AlreadyShown() {
        val b = baseBroadcast.copy(rule = "count", maxShows = 1)
        val receipt = BroadcastReceipt(shownCount = 1)
        val show = shouldShow(
            b = b,
            receipt = receipt,
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertFalse("count=1 with 1 impression should NOT show", show)
    }

    @Test
    fun testCount3ShownTwice() {
        val b = baseBroadcast.copy(rule = "count", maxShows = 3)
        val receipt = BroadcastReceipt(shownCount = 2)
        val show = shouldShow(
            b = b,
            receipt = receipt,
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertTrue("count=3 with 2 impressions should show", show)
    }

    @Test
    fun testCount3ShownThreeTimes() {
        val b = baseBroadcast.copy(rule = "count", maxShows = 3)
        val receipt = BroadcastReceipt(shownCount = 3)
        val show = shouldShow(
            b = b,
            receipt = receipt,
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertFalse("count=3 with 3 impressions should NOT show", show)
    }

    @Test
    fun testUntilBeforeDeadline() {
        val deadline = 5000L
        val b = baseBroadcast.copy(rule = "until", expiresAt = deadline)
        val serverNow = 4999L
        assertTrue("isBroadcastLive should be true before deadline", isBroadcastLive(b, serverNow))
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 5),
            serverNow = serverNow,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertTrue("until rule before deadline should show regardless of previous shownCount", show)
    }

    @Test
    fun testUntilAfterDeadline() {
        val deadline = 5000L
        val b = baseBroadcast.copy(rule = "until", expiresAt = deadline)
        val serverNow = 5001L
        assertFalse("isBroadcastLive should be false after deadline", isBroadcastLive(b, serverNow))
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 0),
            serverNow = serverNow,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertFalse("until rule after deadline should NOT show", show)
    }

    @Test
    fun testStoppedBroadcast() {
        val b = baseBroadcast.copy(status = "stopped")
        assertFalse("Stopped broadcast is not live", isBroadcastLive(b, 2000L))
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 0),
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertFalse("Stopped broadcast should never show", show)
    }

    @Test
    fun testSpecificNotTargeted() {
        val b = baseBroadcast.copy(audience = "specific")
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 0),
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = false
        )
        assertFalse("Specific audience without being targeted should NOT show", show)
    }

    @Test
    fun testSpecificTargeted() {
        val b = baseBroadcast.copy(audience = "specific")
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 0),
            serverNow = 2000L,
            alreadyShownThisSession = false,
            isTargeted = true
        )
        assertTrue("Specific audience when targeted should show", show)
    }

    @Test
    fun testAlreadyShownThisSession() {
        val b = baseBroadcast.copy(rule = "count", maxShows = 3)
        val show = shouldShow(
            b = b,
            receipt = BroadcastReceipt(shownCount = 0),
            serverNow = 2000L,
            alreadyShownThisSession = true,
            isTargeted = true
        )
        assertFalse("Broadcast already shown in current session must NOT show again", show)
    }
}
