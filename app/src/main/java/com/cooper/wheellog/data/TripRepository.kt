package com.cooper.wheellog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.os.Build
import com.cooper.wheellog.LoggingService
import com.cooper.wheellog.utils.FileUtil
import java.io.File

class TripRepository(
    private val tripDao: TripDao,
    private val activeLogLocation: () -> String? = { LoggingService.activeLogLocation() }
) {
    private val filesMutex = Mutex()
    private data class Fingerprint(val size: Long?, val modified: Long?)
    private val fingerprints = mutableMapOf<String, Fingerprint>()
    private val statisticsByUri = mutableMapOf<String, TripDataDbEntry>()
    private var previousActiveKey: String? = null
    private val stoppedKeys = mutableSetOf<String>()

    suspend fun loadItems(
        context: Context,
        useMph: Boolean,
        publish: (List<TripItemState>) -> Unit = {}
    ): List<TripItemState> = withContext(Dispatchers.IO) {
        filesMutex.withLock {
            val coroutine = currentCoroutineContext()
            val cached = tripDao.getAll().associateBy { it.fileName }
            val models = FileUtil.fillTrips(context)
            val newest = models.maxByOrNull { it.lastModified ?: Long.MIN_VALUE }?.uri
            val keys = models.map { it.uri.toString() }.toSet()
            val rawLocation = activeLogLocation()
            // Continued logs use MediaStore's "external" alias; enumeration uses "external_primary".
            val location = if (rawLocation?.startsWith("content://media/external/downloads/") == true) {
                rawLocation.replaceFirst("content://media/external/downloads/", "content://media/external_primary/downloads/")
            } else rawLocation
            val activeKey = models.firstOrNull {
                rawLocation != null && (
                    it.uri.toString() == location || it.uri.toString() == rawLocation ||
                        it.pathLegacyAndroid == rawLocation
                    )
            }?.uri?.toString()
            previousActiveKey?.takeIf { it != activeKey }?.let { stoppedKeys.add(it) }
            previousActiveKey = activeKey
            stoppedKeys.retainAll(keys)
            fingerprints.keys.retainAll(keys)
            statisticsByUri.keys.retainAll(keys)
            val collisions = models.groupingBy { it.fileName }.eachCount().filterValues { it > 1 }.keys
            fun statistic(key: String, fileName: String) =
                statisticsByUri[key]?.takeIf { it.fileName == fileName } ?:
                    cached[fileName].takeUnless { fileName in collisions }
            val items = models.map { model ->
                TripPresentation.item(context, model, statistic(model.uri.toString(), model.fileName), useMph)
            }.toMutableList()
            coroutine.ensureActive()
            publish(items.toList())
            models.forEachIndexed { index, model ->
                coroutine.ensureActive()
                val key = model.uri.toString()
                val fingerprint = Fingerprint(model.fileSize, model.lastModified)
                val previous = fingerprints[key]
                val statistic = statistic(key, model.fileName)
                // MediaStore can retain stale metadata while the active stream is open,
                // and even just after it closes. Keep a stopped key until parsing succeeds.
                val needsParsing = statistic == null || statistic.duration == 0 ||
                    previous != null && previous != fingerprint ||
                    model.uri == newest || key == activeKey || key in stoppedKeys
                val parsed = if (needsParsing) {
                    TripParser.parseFile(
                        context, model.fileName, model.pathLegacyAndroid.orEmpty(), model.uri,
                        persist = model.fileName !in collisions, existingTrip = statistic
                    ) {
                        coroutine.ensureActive()
                    }.second
                } else null
                if (!needsParsing || parsed != null) fingerprints[key] = fingerprint
                if (parsed != null) stoppedKeys.remove(key)
                (parsed ?: statistic)?.let { statisticsByUri[key] = it.copy() }
                if (needsParsing) {
                    items[index] = TripPresentation.item(context, model, parsed ?: statistic, useMph)
                    coroutine.ensureActive()
                    publish(items.toList())
                }
            }
            items.toList()
        }
    }

    suspend fun deleteFile(context: Context, item: TripItemState): Boolean = withContext(Dispatchers.IO) {
        filesMutex.withLock {
            currentCoroutineContext().ensureActive()
            val removed = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val file = item.legacyPath?.let(::File)
                file != null && file.exists() && (file.canonicalFile.delete() || file.delete())
            } else {
                context.contentResolver.delete(item.uri, null, null) > 0
            }
            if (removed) {
                fingerprints.remove(item.key)
                statisticsByUri.remove(item.key)
                stoppedKeys.remove(item.key)
                // The legacy DB is keyed by filename, whereas files/rows are keyed by URI.
                // Keep shared metadata when another folder contains the same CSV name.
                if (FileUtil.fillTrips(context).none { it.fileName == item.fileName }) {
                    tripDao.getTripByFileName(item.fileName)?.let { tripDao.delete(it) }
                }
            }
            removed
        }
    }

    suspend fun insertNewData(statisticDbEntity: TripDataDbEntry) {
        withContext(Dispatchers.IO) {
            tripDao.insert(statisticDbEntity)
        }
    }

    suspend fun getAllData(): List<TripDataDbEntry> {
        return withContext(Dispatchers.IO) {
            return@withContext tripDao.getAll()
        }
    }

    suspend fun removeDataById(id: Long) {
        withContext(Dispatchers.IO) {
            tripDao.deleteDataById(id)
        }
    }
}