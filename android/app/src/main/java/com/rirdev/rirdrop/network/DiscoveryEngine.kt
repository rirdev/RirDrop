package com.rirdev.rirdrop.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

data class PeerDevice(
    val alias: String,
    val os: String,
    val ip: String,
    val httpPort: Int,
    val deviceType: String,
    val lastSeen: Long = System.currentTimeMillis()
)

class DiscoveryEngine(
    private val context: Context,
    private val alias: String,
    private val httpPort: Int = 53318,
    private val onPeersUpdated: (List<PeerDevice>) -> Unit
) {
    private val discoveryPort = 53317
    private val peers = ConcurrentHashMap<String, PeerDevice>()
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scope: CoroutineScope? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // 1. Acquire Wi-Fi Multicast Lock so Android router packet filtering is bypassed
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("rirdrop_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        // 2. Bind UDP Socket
        try {
            socket = DatagramSocket(discoveryPort).apply {
                broadcast = true
                reuseAddress = true
            }
        } catch (e: Exception) {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(discoveryPort))
                    broadcast = true
                }
            } catch (err: Exception) {
                // If port in use, bind ephemeral and listen
                socket = DatagramSocket().apply { broadcast = true }
            }
        }

        // 3. Start Listener Loop
        scope?.launch {
            val buffer = ByteArray(4096)
            while (isRunning && socket != null && !socket!!.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val text = String(packet.data, 0, packet.length)
                    handleIncomingPacket(text, senderIp, packet.port)
                } catch (_: Exception) {}
            }
        }

        // 4. Start Recurring Broadcast Loop (every 3.5 seconds)
        scope?.launch {
            while (isRunning) {
                broadcastAnnouncement()
                delay(3500)
            }
        }

        // 5. Stale Peer Pruner (every 4 seconds)
        scope?.launch {
            while (isRunning) {
                delay(4000)
                val now = System.currentTimeMillis()
                var changed = false
                val it = peers.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (now - entry.value.lastSeen > 10000) {
                        it.remove()
                        changed = true
                    }
                }
                if (changed) {
                    onPeersUpdated(peers.values.toList())
                }
            }
        }
    }

    private fun handleIncomingPacket(rawJson: String, senderIp: String, senderPort: Int) {
        try {
            val json = JSONObject(rawJson)
            if (json.optString("protocol") != "RIRDROP") return

            val localIp = NetworkUtils.getLocalIpAddress()
            val peerHttpPort = json.optInt("httpPort", 53318)
            if (senderIp == localIp && peerHttpPort == this.httpPort) return

            val peerKey = "$senderIp:$peerHttpPort"
            val peer = PeerDevice(
                alias = json.optString("alias", "RirDrop Peer"),
                os = json.optString("os", "unknown"),
                ip = senderIp,
                httpPort = peerHttpPort,
                deviceType = json.optString("deviceType", "desktop"),
                lastSeen = System.currentTimeMillis()
            )

            val isNew = !peers.containsKey(peerKey)
            peers[peerKey] = peer

            if (isNew) {
                onPeersUpdated(peers.values.toList())
            }

            // If probe was sent, reply directly
            if (json.optString("type") == "DISCOVER") {
                sendDirectAnnounce(senderIp, senderPort)
            }
        } catch (_: Exception) {}
    }

    fun broadcastAnnouncement() {
        val s = socket ?: return
        if (s.isClosed) return
        try {
            val payload = JSONObject().apply {
                put("protocol", "RIRDROP")
                put("type", "ANNOUNCE")
                put("alias", alias)
                put("os", "android")
                put("deviceType", "mobile")
                put("httpPort", httpPort)
                put("version", "1.0.0")
                put("timestamp", System.currentTimeMillis())
            }.toString().toByteArray()

            val bcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(payload, payload.size, bcastAddr, discoveryPort)
            s.send(packet)
        } catch (_: Exception) {}
    }

    private fun sendDirectAnnounce(targetIp: String, targetPort: Int) {
        val s = socket ?: return
        if (s.isClosed) return
        try {
            val payload = JSONObject().apply {
                put("protocol", "RIRDROP")
                put("type", "ANNOUNCE")
                put("alias", alias)
                put("os", "android")
                put("deviceType", "mobile")
                put("httpPort", httpPort)
                put("version", "1.0.0")
                put("timestamp", System.currentTimeMillis())
            }.toString().toByteArray()

            val addr = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(payload, payload.size, addr, targetPort)
            s.send(packet)
        } catch (_: Exception) {}
    }

    fun scanNow() {
        val s = socket ?: return
        if (s.isClosed) return
        try {
            val payload = JSONObject().apply {
                put("protocol", "RIRDROP")
                put("type", "DISCOVER")
                put("alias", alias)
                put("os", "android")
                put("httpPort", httpPort)
            }.toString().toByteArray()

            val bcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(payload, payload.size, bcastAddr, discoveryPort)
            s.send(packet)
        } catch (_: Exception) {}
    }

    fun getPeers(): List<PeerDevice> = peers.values.toList()

    fun stop() {
        isRunning = false
        scope?.cancel()
        scope = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
