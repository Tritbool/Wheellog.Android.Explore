package com.cooper.wheellog.bms

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.cooper.wheellog.utils.SmartBms
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BmsPresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun snapshot(type: WHEEL_TYPE, model: String = "", cells: Int = 30, second: Int = 0, proto: String = "") =
        BmsSnapshot(type, model, proto,
            BmsMapper.pack(SmartBms().apply { cellNum = cells }),
            BmsMapper.pack(SmartBms().apply { cellNum = second }),
            73, 100.125, -4.25, 26.5, 39.25)
    private fun names(s: BmsSnapshot) = BmsMapper.fields(s).map { context.resources.getResourceEntryName(it) }
    private val fallback = "bmsRemPerc bmsVoltage bmsCurrent bmsTemp1 bmsTemp2".split(" ")
    private val extrema = "bmsAvgCell bmsMaxCell bmsMinCell bmsCellDiff".split(" ")
    private val ksBase = "bmsSn bmsFw bmsFactoryCap bmsCycles bmsRemCap bmsRemPerc bmsCurrent bmsVoltage bmsTemp1 bmsTemp2".split(" ")
    private val veteranBase = "bmsCurrent bmsVoltage bmsTemp1 bmsTemp2 bmsTemp3 bmsTemp4 bmsTemp5 bmsTemp6".split(" ")
    private val gotwayBase = "bmsCurrent bmsVoltage bmsSemiVoltage1 bmsSemiVoltage2 bmsTemp1 bmsTemp2 bmsTemp3 bmsTemp4".split(" ")
    private fun expected(base: List<String>, count: Int) = base + extrema + (1..count).map { "bmsCell$it" }

    @Test fun `Kingsong legacy fixtures preserve all model specific fields and order for one and two packs`() {
        val env = "bmsTempMos bmsTempMosEnv bmsTemp1Env bmsHumidity1Env".split(" ")
        val fixtures = listOf(
            Triple("KS-14SP", env, 16),
            Triple("KS-S16", listOf("bmsTemp3", "bmsTemp4") + env, 20),
            Triple("KS-S16P", listOf("bmsTemp3", "bmsTemp4") + env, 20),
            Triple("KS-S19", listOf("bmsTemp3", "bmsTemp4") + env, 24),
            Triple("KS-S20", listOf("bmsTemp3", "bmsTemp4", "bmsTemp5", "bmsTemp6") + env, 30),
            Triple("KS-S22", listOf("bmsTemp3", "bmsTemp4", "bmsTemp5", "bmsTemp6") + env, 30),
            Triple("KS-F22P", listOf("bmsTemp3", "bmsTemp4", "bmsTemp5", "bmsTemp6") + env, 42),
            Triple("KS-F18P", "bmsTemp3 bmsTemp4 bmsTemp5 bmsTempMos bmsTemp1Env bmsHumidity1Env bmsTemp2Env bmsHumidity2Env".split(" "), 36)
        )
        fixtures.forEach { (model, extras, count) ->
            listOf(0, count).forEach { second ->
                val s = snapshot(WHEEL_TYPE.KINGSONG, model, count, second)
                assertThat(names(s)).containsExactlyElementsIn(expected(ksBase + extras, count)).inOrder()
                assertThat(BmsMapper.present(s).showSecond).isEqualTo(second > 0)
            }
        }
        assertThat(names(snapshot(WHEEL_TYPE.KINGSONG, "KS-18L"))).containsExactlyElementsIn(fallback).inOrder()
    }

    @Test fun `Veteran fixtures and Begode count thresholds match legacy rather than invented capabilities`() {
        val veterans = mapOf("Lynx" to 36, "Lynx S" to 36, "Sherman L" to 36,
            "Nosfet Apex" to 36, "Nosfet Aeon" to 36, "Patton S" to 30, "Nosfet Aero" to 30, "Oryx" to 42)
        veterans.forEach { (model, count) ->
            listOf(0, count).forEach { second ->
                assertThat(names(snapshot(WHEEL_TYPE.VETERAN, model, count, second)))
                    .containsExactlyElementsIn(expected(veteranBase, count)).inOrder()
            }
        }
        assertThat(names(snapshot(WHEEL_TYPE.VETERAN, "Patton"))).containsExactlyElementsIn(fallback).inOrder()
        mapOf(1 to 16, 16 to 16, 17 to 20, 20 to 20, 21 to 24, 24 to 24,
            25 to 32, 32 to 32, 33 to 36, 36 to 36, 37 to 40, 40 to 40, 41 to 50, 50 to 50).forEach { (reported, count) ->
            listOf(0, reported).forEach { second ->
                assertThat(names(snapshot(WHEEL_TYPE.GOTWAY, "Rocket", reported, second)))
                    .containsExactlyElementsIn(expected(gotwayBase, count)).inOrder()
            }
        }
    }

    @Test fun `Ninebot Z fixtures include metadata and health but S2 and other wheel types use legacy fallback`() {
        val ninebot = "bmsSn bmsFw bmsFactoryCap bmsActualCap bmsCycles bmsChrgCount bmsMfgDate bmsStatus bmsRemCap bmsRemPerc bmsCurrent bmsVoltage bmsTemp1 bmsTemp2 bmsHealth".split(" ")
        listOf(14, 15, 16).forEach { count ->
            listOf(0, count).forEach { second ->
                assertThat(names(snapshot(WHEEL_TYPE.NINEBOT_Z, "Z10", count, second)))
                    .containsExactlyElementsIn(expected(ninebot, count)).inOrder()
            }
        }
        assertThat(names(snapshot(WHEEL_TYPE.NINEBOT_Z, "S2", proto = "S2"))).containsExactlyElementsIn(fallback).inOrder()
        listOf(WHEEL_TYPE.Unknown, WHEEL_TYPE.NINEBOT, WHEEL_TYPE.INMOTION, WHEEL_TYPE.INMOTION_V2).forEach {
            assertThat(names(snapshot(it, "V14"))).containsExactlyElementsIn(fallback).inOrder()
        }
    }

    @Test fun `no cells uses wheel telemetry raw Celsius and does not invent a second column`() {
        WHEEL_TYPE.entries.forEach { type ->
            val result = BmsMapper.present(snapshot(type, cells = 0))
            assertThat(result.rows.map { it.label }).containsExactly(
                R.string.bmsRemPerc, R.string.bmsVoltage, R.string.bmsCurrent, R.string.bmsTemp1, R.string.bmsTemp2
            ).inOrder()
            assertThat(result.rows.map { it.first }).containsExactly("73 %", "100.13 V", "-4.25 A", "26.5°C", "39.3°C").inOrder()
            assertThat(result.rows.map { it.second }).containsExactly("-", "-", "-", "-", "-").inOrder()
            assertThat(result.showSecond).isFalse()
        }
        assertThat(BmsMapper.present(snapshot(WHEEL_TYPE.GOTWAY, cells = 0, second = 16)).showSecond).isTrue()
    }

    @Test fun `scalar formatting indices and balance mask use authoritative pack fields with US decimals`() {
        val b = SmartBms().apply {
            cellNum = 50
            serialNumber = "PACK-A"; versionNumber = "1.2"; factoryCap = 5000; actualCap = 4900
            fullCycles = 12; chargeCount = 17; mfgDateStr = "2025-06"; status = 3
            remCap = 3210; remPerc = 65; current = -2.25; voltage = 123.45
            semiVoltage1 = 61.12; semiVoltage2 = 62.33
            temp1 = 25.5; temp2 = 26.5; temp3 = 27.5; temp4 = 28.5
            health = 98; avgCell = 3.9; maxCell = 4.2; maxCellNum = 7
            minCell = 3.6; minCellNum = 19; cellDiff = 0.6
            cells[0] = 4.012; cells[31] = 4.1; cells[32] = 3.9
            balanceMap = 1 or Int.MIN_VALUE
        }
        val p = BmsMapper.pack(b)
        val expected = mapOf(
            R.string.bmsSn to "PACK-A", R.string.bmsFw to "1.2", R.string.bmsFactoryCap to "5000 mAh",
            R.string.bmsActualCap to "4900 mAh", R.string.bmsCycles to "12", R.string.bmsChrgCount to "17",
            R.string.bmsMfgDate to "2025-06", R.string.bmsStatus to "3", R.string.bmsRemCap to "3210 mAh",
            R.string.bmsRemPerc to "65 %", R.string.bmsCurrent to "-2.25 A", R.string.bmsVoltage to "123.45 V",
            R.string.bmsSemiVoltage1 to "61.12 V", R.string.bmsSemiVoltage2 to "62.33 V",
            R.string.bmsTemp1 to "25.5°C", R.string.bmsTemp2 to "26.5°C", R.string.bmsTemp3 to "27.5°C",
            R.string.bmsTemp4 to "28.5°C", R.string.bmsHealth to "98 %", R.string.bmsAvgCell to "3.900 V",
            R.string.bmsMaxCell to "4.200 V [7]", R.string.bmsMinCell to "3.600 V [19]", R.string.bmsCellDiff to "0.600 V"
        )
        expected.forEach { (field, text) -> assertThat(p.fields[field]).isEqualTo(text) }
        val result = BmsMapper.present(snapshot(WHEEL_TYPE.GOTWAY, cells = 50).copy(first = p))
        fun cell(label: Int) = result.rows.single { it.label == label }.first
        assertThat(cell(R.string.bmsCell1)).isEqualTo("4.012 V [B]")
        assertThat(cell(R.string.bmsCell32)).isEqualTo("4.100 V [B]")
        assertThat(cell(R.string.bmsCell33)).isEqualTo("3.900 V ")
        assertThat(cell(R.string.bmsCell50)).isEqualTo("0.000 V ")
        result.rows.filter { it.label in BmsMapper.cellLabels.drop(32) }.forEach {
            assertThat(it.first).doesNotContain("[B]")
            assertThat(it.second).doesNotContain("[B]")
        }
    }

    @Test fun `snapshot deeply isolates array replacement and concurrent subsequent mutations`() {
        val source = SmartBms().apply { cellNum = 50; cells[0] = 4.125; voltage = 123.0 }
        val captured = BmsMapper.pack(source)
        val writer = thread {
            repeat(10000) { source.cells[0] = it.toDouble(); source.voltage = it.toDouble() }
            source.reset()
        }
        repeat(10000) {
            assertThat(captured.cells[0]).isEqualTo(4.125)
            assertThat(captured.fields[R.string.bmsVoltage]).isEqualTo("123.00 V")
        }
        writer.join()
        assertThat(captured.cellNum).isEqualTo(50)
    }

    @Test fun `packs keep independent values and absent legacy fields or short arrays are safe`() {
        val first = SmartBms().apply { cellNum = 50; voltage = 123.4; cells = arrayOf(4.125) }
        val second = SmartBms().apply { cellNum = 30; voltage = 120.5; temp1 = 41.0; cells[0] = 3.75 }
        val result = BmsMapper.present(snapshot(WHEEL_TYPE.GOTWAY, cells = 50).copy(
            first = BmsMapper.pack(first), second = BmsMapper.pack(second)
        ))
        fun row(id: Int) = result.rows.single { it.label == id }
        assertThat(row(R.string.bmsVoltage).first).isEqualTo("123.40 V")
        assertThat(row(R.string.bmsVoltage).second).isEqualTo("120.50 V")
        assertThat(row(R.string.bmsTemp1).first).isEqualTo("0.0°C")
        assertThat(row(R.string.bmsTemp1).second).isEqualTo("41.0°C")
        assertThat(row(R.string.bmsCell1).first).isEqualTo("4.125 V ")
        assertThat(row(R.string.bmsCell1).second).isEqualTo("3.750 V ")
        assertThat(row(R.string.bmsCell50).first).isEqualTo("0.000 V ")
        val ninebot = BmsMapper.present(snapshot(WHEEL_TYPE.NINEBOT_Z).copy(first = BmsMapper.pack(first)))
        assertThat(ninebot.rows.single { it.label == R.string.bmsSn }.first).isEmpty()
        assertThat(ninebot.rows.single { it.label == R.string.bmsActualCap }.first).isEqualTo("0 mAh")
    }
}
