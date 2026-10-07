package com.cooper.wheellog.scan

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.models.EUCDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ScanPresentationTest {
    private fun device(address: String, name: String?) = mockk<EUCDevice>().also {
        every { it.address } returns address
        every { it.name } returns name
    }

    @Test fun `name fallback preserves the actual Views empty versus whitespace contract`() {
        assertThat(ScanPresentation.displayName(null, "Unknown")).isEqualTo("Unknown")
        assertThat(ScanPresentation.displayName("", "Unknown")).isEqualTo("Unknown")
        assertThat(ScanPresentation.displayName(" ", "Unknown")).isEqualTo(" ")
        assertThat(ScanPresentation.displayName("Wheel", "Unknown")).isEqualTo("Wheel")
    }

    @Test fun `rows keep discovery order and use address identities not positions or RSSI`() {
        val a = device("AA", null)
        val b = device("BB", "Wheel")
        val incoming = mutableListOf(a, b, device("AA", "Duplicate"))
        val rows = ScanPresentation.devices(incoming, "Unknown")
        incoming.clear()
        assertThat(rows.map { it.address }).containsExactly("AA", "BB").inOrder()
        assertThat(rows.map { it.name }).containsExactly("Unknown", "Wheel").inOrder()
        assertThat(rows.first().device).isSameInstanceAs(a)
    }
}
