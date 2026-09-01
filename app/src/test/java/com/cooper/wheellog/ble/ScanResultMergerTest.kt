package com.cooper.wheellog.ble

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.models.EUCDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ScanResultMergerTest {

    private fun device(address: String, name: String? = null): EUCDevice {
        val device = mockk<EUCDevice>(relaxed = true)
        every { device.address } returns address
        every { device.name } returns name
        return device
    }

    @Test
    fun `appends unknown devices in discovery order`() {
        val first = device("AA")
        val second = device("BB")

        var result = ScanResultMerger.merge(emptyList(), listOf(first))
        result = ScanResultMerger.merge(result, listOf(second))

        assertThat(result.map { it.address }).containsExactly("AA", "BB").inOrder()
    }

    @Test
    fun `keeps existing order when the library reports a reshuffled list`() {
        val a = device("AA")
        val b = device("BB")
        val c = device("CC")
        val current = listOf(a, b)

        // onScanCompleted hands back the devices in arbitrary (hash map) order
        val result = ScanResultMerger.merge(current, listOf(c, b, a))

        assertThat(result.map { it.address }).containsExactly("AA", "BB", "CC").inOrder()
    }

    @Test
    fun `returns the same instance when nothing changed`() {
        val current = listOf(device("AA", "Wheel"))

        val result = ScanResultMerger.merge(current, listOf(device("AA", "Wheel")))

        assertThat(result).isSameInstanceAs(current)
    }

    @Test
    fun `refreshes an entry in place once its name becomes known`() {
        val current = listOf(device("AA"), device("BB", "Other"))

        val result = ScanResultMerger.merge(current, listOf(device("AA", "Begode")))

        assertThat(result.map { it.address }).containsExactly("AA", "BB").inOrder()
        assertThat(result[0].name).isEqualTo("Begode")
    }

    @Test
    fun `ignores an empty payload`() {
        val current = listOf(device("AA"))

        assertThat(ScanResultMerger.merge(current, emptyList())).isSameInstanceAs(current)
    }
}
