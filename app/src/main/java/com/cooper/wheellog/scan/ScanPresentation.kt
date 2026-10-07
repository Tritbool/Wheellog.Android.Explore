package com.cooper.wheellog.scan

import io.github.tritbool.euc.ble.models.EUCDevice
import java.util.Collections

data class ScanDeviceItem(val address: String, val name: String, val device: EUCDevice)

data class ScanUiState(
    val scanning: Boolean = false,
    val devices: List<ScanDeviceItem> = emptyList(),
    val manualAddress: String = "",
    val invalidAddress: Boolean = false
)

object ScanPresentation {
    @JvmStatic
    fun displayName(name: String?, unknown: String): String =
        name?.takeIf { it.isNotEmpty() } ?: unknown

    fun devices(devices: List<EUCDevice>, unknown: String): List<ScanDeviceItem> =
        Collections.unmodifiableList(devices.distinctBy { it.address }.map {
            ScanDeviceItem(it.address, displayName(it.name, unknown), it)
        })
}
