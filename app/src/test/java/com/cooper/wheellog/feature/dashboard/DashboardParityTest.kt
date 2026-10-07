package com.cooper.wheellog.feature.dashboard

import com.cooper.wheellog.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class DashboardParityTest {
    @Test fun `central speed threshold tenths rounding mph and locale mirror WheelView`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertThat(DashboardFormatting.speed(99, false)).isEqualTo("9.9")
            assertThat(DashboardFormatting.speed(100, false)).isEqualTo("10")
            assertThat(DashboardFormatting.speed(105, false)).isEqualTo("11")
            assertThat(DashboardFormatting.speed(200, true)).isEqualTo("12")
            assertThat(DashboardFormatting.speed(-105, false)).isEqualTo("-10.5")
            assertThat(DashboardFormatting.temperature(7, false)).isEqualTo("07℃")
            assertThat(DashboardFormatting.temperature(7, true)).isEqualTo("44℉")
        } finally { Locale.setDefault(previous) }
    }

    @Test fun `catalogue has exactly all settings metrics and repeated selections retain slots`() {
        assertThat(DashboardCatalogue.labels).hasSize(27)
        assertThat(DashboardCatalogue.labels.toSet()).hasSize(27)
        assertThat(DashboardCatalogue.labels).containsAtLeast(
            R.string.maxcurrent, R.string.maxphasecurrent, R.string.maxtemperature,
            R.string.temperature2, R.string.ride_time, R.string.consumption)
        val catalogue = listOf(DashboardBlock("PWM", "2.00%"), DashboardBlock("Voltage", "67.20 V"))
        val selected = DashboardCatalogue.select(listOf("Voltage", "PWM", "Voltage"), catalogue)
        assertThat(selected.map { it.label }).containsExactly("Voltage", "PWM", "Voltage").inOrder()
        assertThat(selected.map { it.slot }).containsExactly(0, 1, 2).inOrder()
        assertThat(DashboardCatalogue.replace(selected.map { it.label }, 2, "Battery"))
            .containsExactly("Voltage", "PWM", "Battery").inOrder()
        assertThat(DashboardCatalogue.select(listOf("", "PWM"), catalogue).single().slot).isEqualTo(1)
    }

    @Test fun `ranges are finite capped signed and independent of display units`() {
        assertThat(DashboardFormatting.fraction(Float.NaN, 0)).isEqualTo(0f)
        assertThat(DashboardFormatting.fraction(100f, 0)).isEqualTo(1f)
        assertThat(DashboardFormatting.fraction(-25f, 50)).isEqualTo(-.5f)
        assertThat(dashboardPwmColor(59, 60, 90)).isEqualTo(0xAAFFFFFF.toInt())
        assertThat(dashboardPwmColor(60, 60, 90)).isEqualTo(0xAAFFFFAA.toInt())
        assertThat(dashboardPwmColor(90, 60, 90)).isEqualTo(0xFFFF0000.toInt())
        assertThat(dashboardPwmColor(80, 90, 60)).isEqualTo(0xAAFFFFFF.toInt())
    }

    @Test fun `legacy localized aliases retain metric identity order repeated slots and original selection keys`() {
        val catalogue = listOf(DashboardBlock("Battery", "80 %", metric = R.string.battery))
        val selected = DashboardCatalogue.select(
            listOf("Batterie", "Батарея", "Battery"), catalogue,
            mapOf("Batterie" to R.string.battery, "Батарея" to R.string.battery))
        assertThat(selected.map { it.label }).containsExactly("Battery", "Battery", "Battery").inOrder()
        assertThat(selected.map { it.metric }).containsExactly(R.string.battery, R.string.battery, R.string.battery)
        assertThat(selected.map { it.selectionKey }).containsExactly("Batterie", "Батарея", "Battery").inOrder()
        assertThat(selected.map { it.slot }).containsExactly(0, 1, 2).inOrder()
    }

    @Test fun `portrait landscape theme density and square layouts use original geometry`() {
        val portrait = DashboardGeometry.calculate(400f, 800f, 1f, false, 8)
        assertThat(portrait.outer.cx).isEqualTo(200f)
        assertThat(portrait.outer.cy).isEqualTo(200f)
        assertThat(portrait.outerStroke).isEqualTo(47.5f)
        assertThat(portrait.innerStroke).isEqualTo(29f)
        assertThat(portrait.blocks).hasSize(8)
        assertThat(portrait.speed.contains(portrait.inner.left, portrait.inner.cy)).isFalse()
        assertThat(portrait.speed.contains(portrait.outer.cx, portrait.outer.cy)).isTrue()
        portrait.blocks.forEachIndexed { index, box ->
            assertThat(portrait.blockAt(box.cx, box.cy)).isEqualTo(index)
        }
        val landscape = DashboardGeometry.calculate(800f, 400f, 1f, true, 8)
        assertThat(landscape.outer.cx).isEqualTo(400f)
        assertThat(landscape.outer.cy).isEqualTo(200f)
        assertThat(landscape.inner.width).isLessThan(portrait.inner.width)
        assertThat(landscape.blocks.first().cx).isLessThan(landscape.outer.left)
        assertThat(landscape.blocks[1].cx).isGreaterThan(landscape.outer.right)
        assertThat(DashboardGeometry.calculate(400f, 400f, 1f, false, 8).blocks).isEmpty()
        assertThat(DashboardGeometry.calculate(100f, 110f, 4f, true, 27).blocks).isEmpty()
        assertThat(DashboardGeometry.calculate(400f, 800f, 1f, false, 1).blocks).hasSize(1)
    }
}
