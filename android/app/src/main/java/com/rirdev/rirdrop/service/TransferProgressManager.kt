package com.rirdev.rirdrop.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveDownloadTask(
    val id: String,
    val fileName: String,
    val progress: Float, // 0.0f to 1.0f
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedFormatted: String,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

object TransferProgressManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _tasks = MutableStateFlow<Map<String, ActiveDownloadTask>>(emptyMap())

    val activeDownloads: StateFlow<List<ActiveDownloadTask>> = _tasks
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun updateProgress(id: String, fileName: String, bytesDownloaded: Long, totalBytes: Long, speedFormatted: String) {
        val pct = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        _tasks.update { current ->
            val updated = ActiveDownloadTask(
                id = id,
                fileName = fileName,
                progress = pct,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                speedFormatted = speedFormatted,
                isFinished = false
            )
            current + (id to updated)
        }
    }

    fun markFinished(id: String, fileName: String) {
        _tasks.update { current ->
            val existing = current[id]
            if (existing != null) {
                current + (id to existing.copy(progress = 1f, isFinished = true))
            } else {
                current
            }
        }
        scope.launch {
            delay(2500)
            removeTask(id)
        }
    }

    fun markFailed(id: String, error: String) {
        _tasks.update { current ->
            val existing = current[id]
            if (existing != null) {
                current + (id to existing.copy(isFailed = true, errorMessage = error))
            } else {
                current
            }
        }
        scope.launch {
            delay(3500)
            removeTask(id)
        }
    }

    fun removeTask(id: String) {
        _tasks.update { it - id }
    }
}
