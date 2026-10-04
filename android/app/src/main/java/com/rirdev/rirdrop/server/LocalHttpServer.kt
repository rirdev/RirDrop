package com.rirdev.rirdrop.server

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class SharedItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri? = null,
    val localFile: File? = null
)

class LocalHttpServer(
    private val context: Context,
    port: Int = 53318,
    private val serverAlias: String
) : NanoHTTPD("0.0.0.0", port) {

    private val sharedFiles = ConcurrentHashMap<String, SharedItem>()

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

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // Enable CORS
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Range"
        )

        if (method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            headers.forEach { (k, v) -> res.addHeader(k, v) }
            return res
        }

        try {
            when {
                uri == "/api/status" -> {
                    val status = JSONObject().apply {
                        put("status", "online")
                        put("alias", serverAlias)
                        put("platform", "android")
                        put("sharedCount", sharedFiles.size)
                        put("version", "1.0.0")
                    }
                    val res = newFixedLengthResponse(Response.Status.OK, "application/json", status.toString())
                    headers.forEach { (k, v) -> res.addHeader(k, v) }
                    return res
                }

                uri == "/api/shared" -> {
                    val arr = JSONArray()
                    for (item in sharedFiles.values) {
                        arr.put(JSONObject().apply {
                            put("id", item.id)
                            put("name", item.name)
                            put("size", item.size)
                            put("mimeType", item.mimeType)
                            put("downloadUrl", "/api/download/${item.id}")
                        })
                    }
                    val json = JSONObject().apply {
                        put("count", sharedFiles.size)
                        put("files", arr)
                    }
                    val res = newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                    headers.forEach { (k, v) -> res.addHeader(k, v) }
                    return res
                }

                uri.startsWith("/api/download/") -> {
                    val fileId = uri.substringAfter("/api/download/")
                    val item = sharedFiles[fileId] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")

                    val stream: InputStream = when {
                        item.localFile != null && item.localFile.exists() -> FileInputStream(item.localFile)
                        item.uri != null -> context.contentResolver.openInputStream(item.uri) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Cannot read file")
                        else -> return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File source unavailable")
                    }

                    val res = newFixedLengthResponse(Response.Status.OK, item.mimeType, stream, item.size)
                    headers.forEach { (k, v) -> res.addHeader(k, v) }
                    res.addHeader("Content-Disposition", "attachment; filename=\"${item.name}\"")
                    res.addHeader("Accept-Ranges", "bytes")
                    return res
                }
            }
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server Error: ${e.message}")
        }

        val notFound = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        headers.forEach { (k, v) -> notFound.addHeader(k, v) }
        return notFound
    }
}
