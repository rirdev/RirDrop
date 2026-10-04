package com.rirdev.rirdrop.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class SharedItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri? = null,
    val localFile: File? = null
)

data class SharedFolderItem(
    val id: String,
    val name: String,
    val treeUri: Uri,
    val path: String = "",
    val fileCount: Int = 0
)

class LocalHttpServer(
    private val context: Context,
    port: Int = 53318,
    private val serverAlias: String
) : NanoHTTPD("0.0.0.0", port) {

    private val sharedFiles = ConcurrentHashMap<String, SharedItem>()
    private val sharedFolders = ConcurrentHashMap<String, SharedFolderItem>()

    fun setSharedItems(items: List<SharedItem>) {
        sharedFiles.clear()
        for (item in items) {
            sharedFiles[item.id] = item
        }
    }

    fun addSharedItem(item: SharedItem) {
        sharedFiles[item.id] = item
    }

    fun removeSharedItem(id: String) {
        sharedFiles.remove(id)
    }

    fun setSharedFolders(folders: List<SharedFolderItem>) {
        sharedFolders.clear()
        for (f in folders) {
            sharedFolders[f.id] = f
        }
    }

    fun addSharedFolder(folder: SharedFolderItem) {
        sharedFolders[folder.id] = folder
    }

    fun removeSharedFolder(id: String) {
        sharedFolders.remove(id)
    }

    fun getSharedFiles(): List<SharedItem> = sharedFiles.values.toList()
    fun getSharedFolders(): List<SharedFolderItem> = sharedFolders.values.toList()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val headers = session.headers

        // Enable CORS for PC web and Electron apps
        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Range, Authorization, X-RirDrop-Client",
            "Access-Control-Expose-Headers" to "Content-Range, Content-Length, Accept-Ranges"
        )

        if (method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
            return res
        }

        try {
            when {
                // 1. Status Ping
                uri == "/api/status" -> {
                    val status = JSONObject().apply {
                        put("status", "online")
                        put("alias", serverAlias)
                        put("platform", "android")
                        put("sharedCount", sharedFiles.size)
                        put("sharedFolderCount", sharedFolders.size)
                        put("version", "1.0.0")
                    }
                    val res = newFixedLengthResponse(Response.Status.OK, "application/json", status.toString())
                    corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
                    return res
                }

                // 2. Shared Overview OR Folder Browsing
                uri == "/api/shared" || uri == "/api/folders" -> {
                    val queryParams = session.parameters
                    val folderId = queryParams["folderId"]?.firstOrNull() ?: session.parms["folderId"]

                    if (!folderId.isNullOrBlank()) {
                        // BROWSE SPECIFIC FOLDER
                        val folder = sharedFolders[folderId]
                            ?: return jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "Folder not found"), corsHeaders)

                        val subPathRaw = queryParams["subPath"]?.firstOrNull() ?: session.parms["subPath"] ?: ""
                        val subPath = try { URLDecoder.decode(subPathRaw, "UTF-8") } catch (_: Exception) { subPathRaw }

                        val targetDir = findDocumentFile(context, folder.treeUri, subPath)
                            ?: return jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "Directory not found"), corsHeaders)

                        val itemsArray = JSONArray()
                        val children = targetDir.listFiles()
                        for (child in children) {
                            val name = child.name ?: "Unnamed"
                            val isDir = child.isDirectory
                            val childRel = if (subPath.isBlank()) name else "$subPath/$name"
                            val size = if (isDir) 0L else child.length()
                            val mime = child.type ?: guessMimeType(name)

                            val encodedRel = java.net.URLEncoder.encode(childRel, "UTF-8")
                            val itemObj = JSONObject().apply {
                                put("name", name)
                                put("relPath", childRel)
                                put("folderId", folderId)
                                put("isDirectory", isDir)
                                put("size", size)
                                put("sizeFormatted", if (isDir) "Folder" else formatBytes(size))
                                put("mimeType", mime)
                                put("isVideo", isVideoMime(mime, name))
                                put("isAudio", isAudioMime(mime, name))
                                put("isImage", isImageMime(mime, name))
                                put("streamUrl", "/api/stream?folderId=$folderId&relPath=$encodedRel")
                                put("downloadUrl", "/api/download?folderId=$folderId&relPath=$encodedRel")
                            }
                            itemsArray.put(itemObj)
                        }

                        val result = JSONObject().apply {
                            put("folderId", folderId)
                            put("folderName", folder.name)
                            put("subPath", subPath)
                            put("folderItems", itemsArray)
                            put("count", itemsArray.length())
                        }
                        return jsonResponse(Response.Status.OK, result, corsHeaders)
                    } else {
                        // TOP-LEVEL OVERVIEW (Both Quick Drop Files and Shared Folders)
                        val qdArray = JSONArray()
                        for (item in sharedFiles.values) {
                            qdArray.put(JSONObject().apply {
                                put("id", item.id)
                                put("name", item.name)
                                put("size", item.size)
                                put("sizeFormatted", formatBytes(item.size))
                                put("mimeType", item.mimeType)
                                put("isVideo", isVideoMime(item.mimeType, item.name))
                                put("isAudio", isAudioMime(item.mimeType, item.name))
                                put("streamUrl", "/api/stream?fileId=${item.id}")
                                put("downloadUrl", "/api/download?fileId=${item.id}")
                            })
                        }

                        val foldersArray = JSONArray()
                        for (folder in sharedFolders.values) {
                            foldersArray.put(JSONObject().apply {
                                put("id", folder.id)
                                put("name", folder.name)
                                put("path", folder.path)
                                put("fileCount", folder.fileCount)
                                put("previewThumb", null as String?)
                            })
                        }

                        val json = JSONObject().apply {
                            put("status", "online")
                            put("alias", serverAlias)
                            put("platform", "android")
                            put("quickDropFiles", qdArray)
                            put("sharedFolders", foldersArray)
                            put("files", qdArray) // Backwards compatibility
                            put("count", sharedFiles.size)
                        }
                        return jsonResponse(Response.Status.OK, json, corsHeaders)
                    }
                }

                // 3. Streaming (with HTTP 206 Partial Content) & Download
                uri.startsWith("/api/stream") || uri.startsWith("/api/download") -> {
                    val isDownload = uri.startsWith("/api/download")
                    return handleStreamOrDownload(session, uri, isDownload, corsHeaders)
                }
            }
        } catch (e: Exception) {
            val errRes = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server Error: ${e.message}")
            corsHeaders.forEach { (k, v) -> errRes.addHeader(k, v) }
            return errRes
        }

        val notFound = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        corsHeaders.forEach { (k, v) -> notFound.addHeader(k, v) }
        return notFound
    }

    private fun handleStreamOrDownload(
        session: IHTTPSession,
        uri: String,
        isDownload: Boolean,
        corsHeaders: Map<String, String>
    ): Response {
        val queryParams = session.parameters
        val fileIdParam = queryParams["fileId"]?.firstOrNull() ?: session.parms["fileId"]
        val folderIdParam = queryParams["folderId"]?.firstOrNull() ?: session.parms["folderId"]
        val relPathParam = queryParams["relPath"]?.firstOrNull() ?: session.parms["relPath"]

        // Extract ID from path if present (e.g. /api/download/12345 or /api/stream/12345)
        val pathFileId = when {
            uri.startsWith("/api/download/") -> uri.substringAfter("/api/download/")
            uri.startsWith("/api/stream/") -> uri.substringAfter("/api/stream/")
            else -> null
        }
        val fileId = fileIdParam ?: pathFileId

        val fileName: String
        val totalLength: Long
        val mimeType: String
        val openStream: () -> InputStream?

        if (!folderIdParam.isNullOrBlank() && !relPathParam.isNullOrBlank()) {
            // File from a shared folder
            val folder = sharedFolders[folderIdParam]
                ?: return errorResponse(Response.Status.NOT_FOUND, "Folder not found", corsHeaders)

            val relPath = try { URLDecoder.decode(relPathParam, "UTF-8") } catch (_: Exception) { relPathParam }
            val docFile = findDocumentFile(context, folder.treeUri, relPath)
                ?: return errorResponse(Response.Status.NOT_FOUND, "File in folder not found", corsHeaders)

            fileName = docFile.name ?: "stream_file"
            totalLength = docFile.length()
            mimeType = docFile.type ?: guessMimeType(fileName)
            openStream = { context.contentResolver.openInputStream(docFile.uri) }
        } else if (!fileId.isNullOrBlank()) {
            // File from individual quick drops
            val item = sharedFiles[fileId]
                ?: return errorResponse(Response.Status.NOT_FOUND, "Shared file not found", corsHeaders)

            fileName = item.name
            totalLength = item.size
            mimeType = item.mimeType.ifBlank { guessMimeType(fileName) }
            openStream = {
                when {
                    item.localFile != null && item.localFile.exists() -> FileInputStream(item.localFile)
                    item.uri != null -> context.contentResolver.openInputStream(item.uri)
                    else -> null
                }
            }
        } else {
            return errorResponse(Response.Status.BAD_REQUEST, "Missing file identifier", corsHeaders)
        }

        // Check Range header for HTTP 206 Partial Content (Streaming seek support)
        val headers = session.headers
        val rangeHeader = headers["range"] ?: headers["Range"]

        if (!isDownload && rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true) && totalLength > 0) {
            val rangeSpec = rangeHeader.substringAfter("=").trim()
            var start = 0L
            var end = totalLength - 1L

            if (rangeSpec.contains("-")) {
                val parts = rangeSpec.split("-")
                if (parts[0].isNotBlank()) {
                    start = parts[0].toLongOrNull() ?: 0L
                }
                if (parts.size > 1 && parts[1].isNotBlank()) {
                    end = parts[1].toLongOrNull() ?: (totalLength - 1L)
                }
            }
            if (end >= totalLength) {
                end = totalLength - 1L
            }

            if (start > end || start >= totalLength) {
                val rangeErr = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "Requested Range Not Satisfiable")
                corsHeaders.forEach { (k, v) -> rangeErr.addHeader(k, v) }
                rangeErr.addHeader("Content-Range", "bytes */$totalLength")
                return rangeErr
            }

            val rawStream = openStream()
                ?: return errorResponse(Response.Status.INTERNAL_ERROR, "Cannot open file stream", corsHeaders)

            // Skip to start position
            if (start > 0) {
                var skipped = 0L
                while (skipped < start) {
                    val s = rawStream.skip(start - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val contentLength = end - start + 1L
            val boundedStream = object : InputStream() {
                private var remaining = contentLength

                override fun read(): Int {
                    if (remaining <= 0) return -1
                    val b = rawStream.read()
                    if (b != -1) remaining--
                    return b
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0) return -1
                    val toRead = min(len.toLong(), remaining).toInt()
                    val count = rawStream.read(b, off, toRead)
                    if (count != -1) remaining -= count
                    return count
                }

                override fun close() {
                    rawStream.close()
                }
            }

            val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, boundedStream, contentLength)
            corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
            res.addHeader("Content-Range", "bytes $start-$end/$totalLength")
            res.addHeader("Content-Length", contentLength.toString())
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
            return res
        }

        // Full Content (HTTP 200)
        val stream = openStream()
            ?: return errorResponse(Response.Status.INTERNAL_ERROR, "Cannot read file", corsHeaders)

        val res = newFixedLengthResponse(Response.Status.OK, mimeType, stream, totalLength)
        corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
        res.addHeader("Accept-Ranges", "bytes")
        res.addHeader("Content-Length", totalLength.toString())
        val disposition = if (isDownload) "attachment; filename=\"$fileName\"" else "inline; filename=\"$fileName\""
        res.addHeader("Content-Disposition", disposition)
        return res
    }

    private fun findDocumentFile(context: Context, treeUri: Uri, relPath: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (relPath.isBlank()) return root

        var current: DocumentFile = root
        val segments = relPath.split("/").filter { it.isNotBlank() }
        for (segment in segments) {
            val next = current.findFile(segment) ?: return null
            current = next
        }
        return current
    }

    private fun jsonResponse(status: Response.Status, json: JSONObject, cors: Map<String, String>): Response {
        val res = newFixedLengthResponse(status, "application/json", json.toString())
        cors.forEach { (k, v) -> res.addHeader(k, v) }
        return res
    }

    private fun errorResponse(status: Response.Status, msg: String, cors: Map<String, String>): Response {
        val res = newFixedLengthResponse(status, "text/plain", msg)
        cors.forEach { (k, v) -> res.addHeader(k, v) }
        return res
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "m4a", "aac" -> "audio/mp4"
            "opus" -> "audio/opus"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun isVideoMime(mime: String, name: String): Boolean =
        mime.startsWith("video/") || name.matches(Regex(""".*\.(mp4|mkv|webm|avi|mov|3gp)$""", RegexOption.IGNORE_CASE))

    private fun isAudioMime(mime: String, name: String): Boolean =
        mime.startsWith("audio/") || name.matches(Regex(""".*\.(mp3|flac|wav|ogg|m4a|aac|opus)$""", RegexOption.IGNORE_CASE))

    private fun isImageMime(mime: String, name: String): Boolean =
        mime.startsWith("image/") || name.matches(Regex(""".*\.(jpg|jpeg|png|webp|gif|svg)$""", RegexOption.IGNORE_CASE))
}
