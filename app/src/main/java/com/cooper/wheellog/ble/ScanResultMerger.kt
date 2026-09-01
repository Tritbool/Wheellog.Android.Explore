package com.cooper.wheellog.ble

import io.github.tritbool.euc.ble.models.EUCDevice

/**
 * Keeps the discovered-device list in discovery (FIFO) order, the way the legacy WheelLog
 * scan dialog behaved.
 *
 * This is needed because the BLE library stores its scan results in a hash map, so the list
 * handed over by `onScanCompleted` is in arbitrary order; replacing the app-side list with it
 * makes the device list visibly reshuffle when the scan stops.
 */
internal object ScanResultMerger {

    /**
     * Merges [incoming] into [current], preserving the order of [current]: already known
     * addresses are refreshed in place (only when a previously unknown name became available,
     * so the constant RSSI updates of repeated advertisements do not churn the list), unknown
     * addresses are appended at the end.
     *
     * Returns [current] itself when nothing changed, so callers can skip a state update.
     */
    fun merge(current: List<EUCDevice>, incoming: List<EUCDevice>): List<EUCDevice> {
        if (incoming.isEmpty()) return current
        val merged = current.toMutableList()
        var changed = false
        for (device in incoming) {
            val index = merged.indexOfFirst { it.address == device.address }
            if (index >= 0) {
                if (merged[index].name.isNullOrBlank() && !device.name.isNullOrBlank()) {
                    merged[index] = device
                    changed = true
                }
            } else {
                merged.add(device)
                changed = true
            }
        }
        return if (changed) merged else current
    }
}
