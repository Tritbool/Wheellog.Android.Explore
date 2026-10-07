package com.cooper.wheellog.trips

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.data.LogTick
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.data.TripDataDbEntry
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.data.TripRepository
import com.cooper.wheellog.data.TripParser
import com.cooper.wheellog.utils.FileUtil
import com.cooper.wheellog.views.TripModel
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class TripRepositoryTest {
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val dao = mockk<TripDao>(relaxed = true)
    private val item = TripItemState(Uri.parse("content://media/external/downloads/42"), "ride_01.csv", null, "ride", "", "")

    @Before fun setup() {
        every { context.contentResolver } returns resolver
        mockkObject(FileUtil.Companion)
        every { FileUtil.fillTrips(context) } returns arrayListOf()
    }

    @After fun cleanup() { unmockkAll() }

    @Test fun `successful MediaStore delete removes exactly filename row only after file succeeds`() = runTest {
        val row = TripDataDbEntry(id = 42, fileName = item.fileName)
        every { resolver.delete(item.uri, null, null) } returns 1
        every { dao.getTripByFileName(item.fileName) } returns row
        assertThat(TripRepository(dao).deleteFile(context, item)).isTrue()
        verifyOrder {
            resolver.delete(item.uri, null, null)
            dao.getTripByFileName(item.fileName)
            dao.delete(row)
        }
        verify(exactly = 0) { dao.deleteDataById(any()) }
    }

    @Test fun `zero deleted rows retains database entry`() = runTest {
        every { resolver.delete(item.uri, null, null) } returns 0
        assertThat(TripRepository(dao).deleteFile(context, item)).isFalse()
        verify { dao wasNot Called }
    }

    @Test fun `permission failure never deletes metadata`() = runTest {
        every { resolver.delete(item.uri, null, null) } throws SecurityException("permission denied")
        var failure: Exception? = null
        try { TripRepository(dao).deleteFile(context, item) } catch (exception: SecurityException) { failure = exception }
        assertThat(failure).isInstanceOf(SecurityException::class.java)
        verify { dao wasNot Called }
    }

    @Test fun `same filename in another folder retains shared metadata`() = runTest {
        every { resolver.delete(item.uri, null, null) } returns 1
        every { FileUtil.fillTrips(context) } returns arrayListOf(
            TripModel(item.fileName, "", Uri.parse("content://media/external/downloads/99"))
        )
        assertThat(TripRepository(dao).deleteFile(context, item)).isTrue()
        verify { dao wasNot Called }
    }

    @Test
    @Config(sdk = [28])
    fun `legacy deletion uses captured path and missing file is a failure`() = runTest {
        val file = File("build/trip-delete-fixture.csv")
        file.parentFile!!.mkdirs()
        file.writeText("fixture")
        val legacy = item.copy(legacyPath = file.absolutePath)
        try {
            assertThat(TripRepository(dao).deleteFile(context, legacy)).isTrue()
            assertThat(file.exists()).isFalse()
            clearMocks(dao)
            assertThat(TripRepository(dao).deleteFile(context, legacy)).isFalse()
            verify { dao wasNot Called }
            verify { resolver wasNot Called }
        } finally { file.delete() }
    }

    @Test fun `history caches by size and modification while active candidate reparses unchanged metadata`() = runTest {
        val older = TripModel("2020_01_01_10_00_00.csv", "1 Kb", item.uri, fileSize = 100, lastModified = 1)
        val latest = TripModel(
            "2020_01_02_10_00_00.csv", "2 Kb", Uri.parse("content://media/external/downloads/99"),
            fileSize = 200, lastModified = 2
        )
        val rows = listOf(
            TripDataDbEntry(fileName = older.fileName, duration = 10),
            TripDataDbEntry(fileName = latest.fileName, duration = 20)
        )
        every { context.getString(any()) } answers {
            ApplicationProvider.getApplicationContext<Application>().getString(firstArg<Int>())
        }
        every { dao.getAll() } returns rows
        every { FileUtil.fillTrips(context) } returns arrayListOf(older, latest)
        mockkObject(TripParser)
        every { TripParser.parseFile(context, any(), any(), any(), any(), any(), any()) } answers {
            lastArg<() -> Unit>().invoke()
            emptyList<LogTick>() to rows.first { it.fileName == secondArg<String>() }
        }
        val repository = TripRepository(dao)
        repository.loadItems(context, false)
        verify(exactly = 0) { TripParser.parseFile(context, older.fileName, any(), any(), any(), any(), any()) }
        verify(exactly = 1) { TripParser.parseFile(context, latest.fileName, any(), any(), any(), any(), any()) }
        repository.loadItems(context, true)
        verify(exactly = 2) { TripParser.parseFile(context, latest.fileName, any(), any(), any(), any(), any()) }
        verify(exactly = 0) { TripParser.parseFile(context, older.fileName, any(), any(), any(), any(), any()) }
        every { FileUtil.fillTrips(context) } returns arrayListOf(
            TripModel(older.fileName, "1 Kb", older.uri, fileSize = 101, lastModified = 1), latest
        )
        repository.loadItems(context, false)
        verify(exactly = 1) { TripParser.parseFile(context, older.fileName, any(), any(), any(), any(), any()) }
        every { FileUtil.fillTrips(context) } returns arrayListOf(
            TripModel(older.fileName, "1 Kb", older.uri, fileSize = 101, lastModified = 3), latest
        )
        repository.loadItems(context, false)
        verify(exactly = 2) { TripParser.parseFile(context, older.fileName, any(), any(), any(), any(), any()) }
    }

    @Test fun `filename rows are published before uncached parsing and statistics arrive incrementally`() = runTest {
        val first = TripModel("first.csv", "first size", item.uri, fileSize = 100, lastModified = 1)
        val second = TripModel("second.csv", "second size", Uri.parse("content://media/external/downloads/99"), fileSize = 200, lastModified = 2)
        every { dao.getAll() } returns emptyList()
        every { FileUtil.fillTrips(context) } returns arrayListOf(first, second)
        every { context.getString(any()) } answers {
            ApplicationProvider.getApplicationContext<Application>().getString(firstArg<Int>())
        }
        val snapshots = mutableListOf<List<TripItemState>>()
        mockkObject(TripParser)
        every { TripParser.parseFile(context, any(), any(), any(), any(), any(), any()) } answers {
            assertThat(snapshots).isNotEmpty()
            assertThat(snapshots.first().map { it.description }).containsExactly("first size", "second size").inOrder()
            lastArg<() -> Unit>().invoke()
            emptyList<LogTick>() to TripDataDbEntry(fileName = secondArg(), duration = 1, maxSpeed = 20f)
        }
        TripRepository(dao).loadItems(context, false) { snapshots.add(it) }
        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1][0].description).contains("20.00")
        assertThat(snapshots[1][1].description).isEqualTo("second size")
        assertThat(snapshots[2][1].description).contains("20.00")
        assertThat(snapshots.first()[0].description).isEqualTo("first size")
    }

    @Test fun `same basename never reuses shared Room statistics or poisons another URI`() = runTest {
        val first = TripModel("same.csv", "first size", item.uri, fileSize = 100, lastModified = 1)
        val second = TripModel("same.csv", "second size", Uri.parse("content://media/external/downloads/99"), fileSize = 200, lastModified = 2)
        every { dao.getAll() } returns listOf(TripDataDbEntry(fileName = "same.csv", duration = 1, maxSpeed = 99f))
        every { FileUtil.fillTrips(context) } returns arrayListOf(first, second)
        every { context.getString(any()) } answers {
            ApplicationProvider.getApplicationContext<Application>().getString(firstArg<Int>())
        }
        mockkObject(TripParser)
        every { TripParser.parseFile(context, "same.csv", any(), any(), false, any(), any()) } answers {
            lastArg<() -> Unit>().invoke()
            val speed = if (arg<Uri>(3) == first.uri) 10f else 20f
            emptyList<LogTick>() to TripDataDbEntry(fileName = "same.csv", duration = 1, maxSpeed = speed)
        }
        val repository = TripRepository(dao)
        val initial = mutableListOf<List<TripItemState>>()
        val loaded = repository.loadItems(context, false) { initial.add(it) }
        assertThat(initial.first().map { it.description }).containsExactly("first size", "second size").inOrder()
        assertThat(loaded[0].description).contains("10.00")
        assertThat(loaded[1].description).contains("20.00")
        val refreshed = repository.loadItems(context, false)
        assertThat(refreshed[0].description).contains("10.00")
        assertThat(refreshed[1].description).contains("20.00")
        verify(exactly = 1) { TripParser.parseFile(context, "same.csv", any(), first.uri, false, any(), any()) }
        verify(exactly = 2) { TripParser.parseFile(context, "same.csv", any(), second.uri, false, any(), any()) }
    }

    @Test fun `actual overnight active CSV and first stopped refresh reparse despite newer import and unchanged metadata`() = runTest {
        val active = TripModel(
            "2020_01_01_10_00_00.csv", "active size", Uri.parse("content://media/external_primary/downloads/42"),
            fileSize = 100, lastModified = 1
        )
        val imported = TripModel(
            "2020_01_02_10_00_00.csv", "imported size", Uri.parse("content://media/external_primary/downloads/99"),
            fileSize = 200, lastModified = 2
        )
        val rows = listOf(
            TripDataDbEntry(fileName = active.fileName, duration = 10),
            TripDataDbEntry(fileName = imported.fileName, duration = 20)
        )
        every { dao.getAll() } returns rows
        every { FileUtil.fillTrips(context) } returns arrayListOf(active, imported)
        every { context.getString(any()) } answers {
            ApplicationProvider.getApplicationContext<Application>().getString(firstArg<Int>())
        }
        mockkObject(TripParser)
        every { TripParser.parseFile(context, any(), any(), any(), any(), any(), any()) } answers {
            lastArg<() -> Unit>().invoke()
            emptyList<LogTick>() to rows.first { it.fileName == secondArg<String>() }
        }
        var activeLocation: String? = "content://media/external/downloads/42"
        val repository = TripRepository(dao) { activeLocation }
        repository.loadItems(context, false)
        repository.loadItems(context, false)
        verify(exactly = 2) { TripParser.parseFile(context, active.fileName, any(), active.uri, any(), any(), any()) }
        verify(exactly = 2) { TripParser.parseFile(context, imported.fileName, any(), imported.uri, any(), any(), any()) }
        activeLocation = null
        repository.loadItems(context, false)
        verify(exactly = 3) { TripParser.parseFile(context, active.fileName, any(), active.uri, any(), any(), any()) }
        repository.loadItems(context, false)
        verify(exactly = 3) { TripParser.parseFile(context, active.fileName, any(), active.uri, any(), any(), any()) }
        verify(exactly = 4) { TripParser.parseFile(context, imported.fileName, any(), imported.uri, any(), any(), any()) }
    }
}
