package com.cooper.wheellog.data

import android.content.Context
import com.google.common.collect.Range
import com.google.common.truth.Truth.*
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.io.InputStream
import kotlin.system.measureTimeMillis

class TripParserTest {
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        dao = mockkClass(TripDao::class, relaxed = false)
        startKoin {
            modules(
                module {
                    single { dao }
                }
            )
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun parseFile() {
        // Arrange.
        val context = mockkClass(Context::class, relaxed = true)
        val trip = TripDataDbEntry(fileName = "test")
        every { dao.getTripByFileName(any()) } returns trip
        justRun { dao.update(any()) }
        val inputStream: InputStream = File("src/test/resources/log_test1.csv").inputStream()

        // Act.
        measureTimeMillis {
            TripParser.parseFile(context, "test", inputStream)
        }.also {
            println("parseFile took $it ms")
        }

        // Assert.
        assertThat(trip.duration).isEqualTo(880)
        assertThat(trip.distance).isEqualTo(19_493)
        assertThat(trip.maxCurrent).isIn(Range.closed(86.8f, 86.9f))
        assertThat(trip.maxPwm).isIn(Range.closed(86.7f, 86.8f))
        assertThat(trip.maxPower).isIn(Range.closed(7825f, 7826f))
        assertThat(trip.maxSpeed).isIn(Range.closed(71f, 71.05f))
        assertThat(trip.avgSpeed).isIn(Range.closed(29.7f, 29.8f))
        assertThat(trip.consumptionTotal).isIn(Range.closed(1059f, 1060f))
        assertThat(trip.consumptionByKm).isIn(Range.closed(54.3f, 54.4f))
    }

    @Test
    fun emptyFileDoesNotInsertMetadataAndClosesStream() {
        val context = mockkClass(Context::class, relaxed = true)
        var closed = false
        val stream = object : java.io.ByteArrayInputStream(byteArrayOf()) {
            override fun close() { closed = true; super.close() }
        }
        assertThat(TripParser.parseFile(context, "empty.csv", stream).second).isNull()
        assertThat(closed).isTrue()
        verify { dao wasNot Called }
    }

    @Test
    fun cancellationClosesStreamAndDoesNotInsertMetadata() {
        val context = mockkClass(Context::class, relaxed = true)
        var closed = false
        val stream = object : java.io.ByteArrayInputStream("date,time\n".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        var cancelled = false
        try {
            TripParser.parseFile(context, "cancelled.csv", stream) {
                throw kotlinx.coroutines.CancellationException("cancel")
            }
        } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertThat(cancelled).isTrue()
        assertThat(closed).isTrue()
        verify { dao wasNot Called }
    }

    private fun replacementCsv() = (
        "date,time,battery_level,voltage,current,power,speed,system_temp,pwm,distance,totaldistance\n" +
        "2024-01-01,10:00:00.000,80,100,10,1000,20,30,15,0,1000\n" +
        "2024-01-01,10:01:00.000,79,100,20,2000,30,31,25,1000,2000\n"
    ).byteInputStream()

    @Test
    fun replacedFileRecomputesDecreasedMaximaAndPreservesEcMetadata() {
        val context = mockkClass(Context::class, relaxed = true)
        val previous = TripDataDbEntry(fileName = "replacement.csv", maxCurrent = 999f, maxPwm = 99f, ecId = 42, ecUrl = "saved")
        every { dao.getTripByFileName(previous.fileName) } returns previous
        justRun { dao.update(any()) }
        val parsed = TripParser.parseFile(context, previous.fileName, replacementCsv()).second!!
        assertThat(parsed.maxCurrent).isEqualTo(20f)
        assertThat(parsed.maxPwm).isEqualTo(25f)
        assertThat(parsed.ecId).isEqualTo(42)
        assertThat(parsed.ecUrl).isEqualTo("saved")
        verify { dao.update(parsed) }
    }

    @Test
    fun transientCollisionParsingDoesNotReadOrWriteSharedRoomRow() {
        val context = mockkClass(Context::class, relaxed = true)
        val memory = TripDataDbEntry(fileName = "same.csv", maxCurrent = 999f, maxPwm = 99f, ecId = 42)
        val parsed = TripParser.parseFile(
            context, memory.fileName, replacementCsv(), persist = false, existingTrip = memory
        ).second!!
        assertThat(parsed.maxCurrent).isEqualTo(20f)
        assertThat(parsed.maxPwm).isEqualTo(25f)
        assertThat(parsed.ecId).isEqualTo(42)
        assertThat(memory.maxCurrent).isEqualTo(999f)
        assertThat(memory.maxPwm).isEqualTo(99f)
        verify { dao wasNot Called }
    }
}