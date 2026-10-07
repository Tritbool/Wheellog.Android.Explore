package com.cooper.wheellog.bms

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.LoggingService
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.models.BMSData
import io.github.tritbool.euc.ble.models.EUCData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.cooper.wheellog.utils.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.Continuation

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BmsSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var model: BleSessionViewModel
    private val client = mockk<EucBleClient>(relaxed = true)
    private var packs: List<BMSData>? = null
    private fun pack(index: Int, cells: List<Double>?, voltage: Double? = null, temps: List<Double>? = null, current: Double? = null) =
        BMSData(index, voltage, current, null, null, null, temps, cells)
    private fun data(timestamp: Long = 1) = EUCData(
        speed = 0.0, voltage = 100.0, current = -2.0, temperature = 25.0, batteryLevel = 80,
        distance = 0.0, power = 0.0, timestamp = timestamp, rawData = byteArrayOf(),
        manufacturer = "Begode", model = "Rocket", isCharging = false, rideTime = 0,
        cellVoltages = List(50) { 9.0 }, motorTemperature = 35.0
    )
    private fun publish(
        timestamp: Long = 1,
        manufacturer: String = "Begode",
        wheelModel: String = "Rocket",
        sample: EUCData = data(timestamp)
    ) {
        model.javaClass.getDeclaredMethod("updateTelemetryData", EUCData::class.java).apply { isAccessible = true }
            .invoke(model, sample.copy(manufacturer = manufacturer, model = wheelModel))
    }

    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt("versionSettings", 1).commit()
        startKoin { modules(module { single { AppConfig(context) } }) }
        model = BleSessionViewModel(context)
        model.javaClass.getDeclaredField("_eucBleClient\$delegate").apply { isAccessible = true }.set(model, lazyOf(client))
        every { client.getBMSData() } answers { packs }
    }

    @After fun cleanup() {
        model.viewModelScope.cancel()
        stopKoin()
    }

    @Test fun `partial packets retain per pack identities and never use combined EUCData cells`() {
        packs = listOf(pack(0, listOf(4.1, 4.2), 100.1, listOf(21.0, 22.0)),
            pack(1, listOf(3.7, 3.8), 99.2, listOf(31.0, 32.0)))
        publish()
        val first = model.bmsDisplay.value
        assertThat(first.first.cells.take(2)).containsExactly(4.1, 4.2).inOrder()
        assertThat(first.second.cells.take(2)).containsExactly(3.7, 3.8).inOrder()
        assertThat(first.first.fields[R.string.bmsMinCell]).isEqualTo("4.100 V [1]")
        assertThat(first.first.fields[R.string.bmsMaxCell]).isEqualTo("4.200 V [2]")
        assertThat(first.first.fields[R.string.bmsCellDiff]).isEqualTo("0.100 V")
        packs = listOf(pack(1, null, temps = listOf(33.0)))
        publish(2, manufacturer = "", wheelModel = "")
        val partial = model.bmsDisplay.value
        assertThat(partial.first).isEqualTo(first.first)
        assertThat(partial.second.cellNum).isEqualTo(2)
        assertThat(partial.second.cells.take(2)).containsExactly(3.7, 3.8).inOrder()
        assertThat(partial.second.fields[R.string.bmsTemp1]).isEqualTo("33.0°C")
        assertThat(partial.second.fields[R.string.bmsTemp2]).isEqualTo("32.0°C")
        assertThat(partial.wheelType).isEqualTo(first.wheelType)
        assertThat(partial.model).isEqualTo(first.model)
        packs = null
        publish(3)
        assertThat(model.bmsDisplay.value.first).isEqualTo(partial.first)
        assertThat(model.bmsDisplay.value.second).isEqualTo(partial.second)
        model.bms1.cells[0] = 1.0
        assertThat(first.first.cells[0]).isEqualTo(4.1)
    }

    @Test fun `disconnect and reset drop carried packs and next wheel cannot resurrect combined cells`() {
        packs = listOf(pack(0, listOf(4.1, 4.2)), pack(1, listOf(3.7, 3.8)))
        publish()
        model.javaClass.getDeclaredMethod("updateDisconnectedState", Continuation::class.java).apply { isAccessible = true }
            .invoke(model, mockk<Continuation<Unit>>(relaxed = true))
        assertThat(model.bmsDisplay.value.hasDetails).isFalse()
        assertThat(model.bmsDisplay.value.voltage).isEqualTo(0.0)
        packs = null
        publish(2)
        assertThat(model.bmsDisplay.value.hasDetails).isFalse()
        assertThat(model.bmsDisplay.value.voltage).isEqualTo(100.0)
        packs = listOf(pack(0, listOf(4.0, 4.1)))
        publish(3)
        model.resetBmsData()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(model.bmsDisplay.value.hasDetails).isFalse()
    }

    @Test fun `one based decoders retain battery two when only its partial packet arrives`() {
        packs = listOf(pack(1, listOf(4.1, 4.2)), pack(2, listOf(3.7, 3.8)))
        publish(manufacturer = "Kingsong")
        val captured = model.bmsDisplay.value
        packs = listOf(pack(2, listOf(3.9)))
        publish(2, "Kingsong")
        val next = model.bmsDisplay.value
        assertThat(next.first).isEqualTo(captured.first)
        assertThat(next.second.cells.take(2)).containsExactly(3.9, 3.8).inOrder()
        assertThat(next.second.cellNum).isEqualTo(2)
        assertThat(captured.second.cells.take(2)).containsExactly(3.7, 3.8).inOrder()
    }

    @Test fun `partial metadata retains same wheel packs but a newly identified wheel clears both`() {
        packs = listOf(pack(0, listOf(4.1, 4.2)), pack(1, listOf(3.7, 3.8)))
        publish()
        packs = null
        publish(2, wheelModel = "")
        assertThat(model.bmsDisplay.value.first.cellNum).isEqualTo(2)
        assertThat(model.bmsDisplay.value.second.cellNum).isEqualTo(2)
        publish(3, wheelModel = "Blitz")
        assertThat(model.bmsDisplay.value.hasDetails).isFalse()
        assertThat(model.bmsDisplay.value.first.cells).doesNotContain(4.1)
        assertThat(model.bmsDisplay.value.second.cells).doesNotContain(3.7)
    }

    @Test fun `missing telemetry model does not replace a detailed model specific presentation`() {
        packs = listOf(pack(1, listOf(4.1, 4.2)), pack(2, listOf(3.7, 3.8)))
        publish(manufacturer = "Kingsong", wheelModel = "KS-S22")
        val first = model.bmsDisplay.value
        packs = null
        publish(2, manufacturer = "Kingsong", wheelModel = "")
        val partial = model.bmsDisplay.value
        assertThat(partial.model).isEqualTo("KS-S22")
        assertThat(BmsMapper.fields(partial)).isEqualTo(BmsMapper.fields(first))
        assertThat(partial.first).isEqualTo(first.first)
        assertThat(partial.second).isEqualTo(first.second)
    }

    @Test fun `known single V14 logical pack remains usable when per pack API is temporarily unavailable`() {
        packs = listOf(pack(1, List(32) { 4.1 }, voltage = 131.2))
        publish(manufacturer = "Inmotion", wheelModel = "V14")
        val first = model.bmsDisplay.value
        packs = null
        publish(2, manufacturer = "Inmotion", wheelModel = "V14")
        val partial = model.bmsDisplay.value
        assertThat(partial.first).isEqualTo(first.first)
        assertThat(partial.first.cellNum).isEqualTo(32)
        assertThat(partial.second.cellNum).isEqualTo(0)
        val presentation = BmsMapper.present(partial)
        assertThat(presentation.showSecond).isFalse()
        assertThat(presentation.rows.single { it.label == R.string.bmsVoltage }.first).isEqualTo("131.20 V")
    }

    @Test fun `actual ExtremeBull manufacturer maps zero based packs independently`() {
        packs = listOf(pack(0, listOf(4.1, 4.2), voltage = 100.1), pack(1, listOf(3.7, 3.8), voltage = 99.2))
        publish(manufacturer = "ExtremeBull")
        val snapshot = model.bmsDisplay.value
        assertThat(snapshot.wheelType).isEqualTo(com.cooper.wheellog.utils.Constants.WHEEL_TYPE.GOTWAY)
        assertThat(snapshot.first.cells.take(2)).containsExactly(4.1, 4.2).inOrder()
        assertThat(snapshot.second.cells.take(2)).containsExactly(3.7, 3.8).inOrder()
        assertThat(snapshot.first.fields[R.string.bmsVoltage]).isEqualTo("100.10 V")
        assertThat(snapshot.second.fields[R.string.bmsVoltage]).isEqualTo("99.20 V")
    }

    @Test fun `never supplied fields refresh telemetry while supplied fields survive partial packets and reset independently`() {
        packs = listOf(
            pack(1, List(16) { 4.1 }, current = 4.5, temps = listOf(41.0)),
            pack(2, List(16) { 3.8 }, voltage = 99.0, temps = listOf(51.0, 52.0, 53.0, 54.0, 55.0, 56.0))
        )
        publish(manufacturer = "Leaperkim", wheelModel = "Lynx")
        val first = model.bmsDisplay.value
        assertThat(first.first.fields[R.string.bmsVoltage]).isEqualTo("100.00 V")
        assertThat(first.first.fields[R.string.bmsTemp2]).isEqualTo("35.0°C")
        assertThat(first.second.fields[R.string.bmsCurrent]).isEqualTo("-2.00 A")
        packs = listOf(pack(1, null))
        publish(2, "Leaperkim", "Lynx",
            data(2).copy(voltage = 101.0, current = -3.0, temperature = 26.0, motorTemperature = 36.0))
        val partial = model.bmsDisplay.value
        assertThat(partial.first.fields[R.string.bmsVoltage]).isEqualTo("101.00 V")
        assertThat(partial.first.fields[R.string.bmsCurrent]).isEqualTo("4.50 A")
        assertThat(partial.first.fields[R.string.bmsTemp1]).isEqualTo("41.0°C")
        assertThat(partial.first.fields[R.string.bmsTemp2]).isEqualTo("36.0°C")
        assertThat(partial.second.fields[R.string.bmsVoltage]).isEqualTo("99.00 V")
        assertThat(partial.second.fields[R.string.bmsCurrent]).isEqualTo("-3.00 A")
        assertThat(partial.second.fields[R.string.bmsTemp6]).isEqualTo("56.0°C")
        packs = null
        publish(3, "Leaperkim", "Lynx",
            data(3).copy(voltage = 102.0, current = -4.0, temperature = 27.0, motorTemperature = 37.0))
        val absent = model.bmsDisplay.value
        assertThat(absent.first.fields[R.string.bmsVoltage]).isEqualTo("102.00 V")
        assertThat(absent.first.fields[R.string.bmsCurrent]).isEqualTo("4.50 A")
        assertThat(absent.first.fields[R.string.bmsTemp1]).isEqualTo("41.0°C")
        assertThat(absent.first.fields[R.string.bmsTemp2]).isEqualTo("37.0°C")
        assertThat(absent.second.fields[R.string.bmsVoltage]).isEqualTo("99.00 V")
        assertThat(absent.second.fields[R.string.bmsCurrent]).isEqualTo("-4.00 A")
        assertThat(absent.second.fields[R.string.bmsTemp6]).isEqualTo("56.0°C")
        model.resetBmsData()
        shadowOf(Looper.getMainLooper()).idle()
        packs = listOf(pack(1, List(16) { 4.0 }), pack(2, List(16) { 3.9 }))
        publish(4, "Leaperkim", "Lynx",
            data(4).copy(voltage = 103.0, current = -5.0, temperature = 28.0, motorTemperature = 38.0))
        val reset = model.bmsDisplay.value
        listOf(reset.first, reset.second).forEach {
            assertThat(it.fields[R.string.bmsVoltage]).isEqualTo("103.00 V")
            assertThat(it.fields[R.string.bmsCurrent]).isEqualTo("-5.00 A")
            assertThat(it.fields[R.string.bmsTemp1]).isEqualTo("28.0°C")
            assertThat(it.fields[R.string.bmsTemp2]).isEqualTo("38.0°C")
            assertThat(it.fields[R.string.bmsTemp6]).isEqualTo("0.0°C")
        }
    }

    @Test fun `empty API snapshots publish immediately without clearing UI partial carryforward`() {
        val emissions = mutableListOf<List<BMSData>>()
        val collector = model.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.bmsSnapshots.collect { emissions += it }
        }
        try {
            val cells = mutableListOf(4.1, 4.2)
            packs = listOf(pack(0, cells))
            publish()
            val copied = model.bmsSnapshots.replayCache.single()
            cells[0] = 1.0
            assertThat(copied.single().cellVoltages).containsExactly(4.1, 4.2).inOrder()
            packs = emptyList()
            publish(2)
            assertThat(model.bmsSnapshots.replayCache.single()).isEmpty()
            assertThat(model.bmsDisplay.value.first.cellNum).isEqualTo(2)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(emissions.last()).isEmpty()
            assertThat(emissions).contains(copied)
        } finally {
            collector.cancel()
        }
    }

    @Test fun `disconnect reset connection and wheel change invalidate logging replay before next telemetry`() {
        packs = listOf(pack(0, listOf(4.1, 4.2)))
        publish()
        model.javaClass.getDeclaredMethod("updateDisconnectedState", Continuation::class.java).apply { isAccessible = true }
            .invoke(model, mockk<Continuation<Unit>>(relaxed = true))
        assertThat(model.bmsSnapshots.replayCache.single()).isEmpty()
        publish(2)
        model.resetBmsData()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(model.bmsSnapshots.replayCache.single()).isEmpty()
        publish(3)
        model.javaClass.getDeclaredMethod("updateConnectedDevice", io.github.tritbool.euc.ble.models.EUCDevice::class.java)
            .apply { isAccessible = true }.invoke(model, null)
        assertThat(model.bmsSnapshots.replayCache.single()).isEmpty()
        publish(4)
        packs = null
        publish(5, wheelModel = "Blitz")
        assertThat(model.bmsSnapshots.replayCache.single()).isEmpty()
    }

    @Test fun `logging reads immediate replay not stale asynchronously collected packs`() {
        org.koin.core.context.loadKoinModules(module { single { model } })
        org.koin.core.context.GlobalContext.get().get<AppConfig>().enableBmsData = true
        val service = LoggingService()
        val file = mockk<FileUtil>(relaxed = true)
        service.javaClass.getDeclaredField("bmsFileUtil").apply { isAccessible = true }.set(service, file)
        val write = service.javaClass.getDeclaredMethod("updateBmsFile").apply { isAccessible = true }
        packs = listOf(pack(0, listOf(4.1, 4.2), voltage = 100.0))
        publish()
        write.invoke(service)
        verify(exactly = 1) { file.writeLine(any()) }
        clearMocks(file, answers = false, recordedCalls = true)
        packs = emptyList()
        publish(2)
        write.invoke(service)
        verify(exactly = 0) { file.writeLine(any()) }
        packs = listOf(pack(0, listOf(3.8, 3.9), voltage = 99.0))
        publish(3)
        model.resetBmsData()
        shadowOf(Looper.getMainLooper()).idle()
        write.invoke(service)
        verify(exactly = 0) { file.writeLine(any()) }
    }
}
