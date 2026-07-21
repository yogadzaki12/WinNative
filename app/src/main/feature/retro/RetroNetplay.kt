package com.winlator.cmod.feature.retro

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.swordfish.libretrodroid.GLRetroView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RetroNetplaySession(
    private val retroView: GLRetroView,
    private val localPort: Int,
    private val remotePort: Int,
    private val isHost: Boolean,
    private val onStatus: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val remoteKeys = ConcurrentHashMap.newKeySet<Int>()
    private var socket: Socket? = null
    private var server: ServerSocket? = null
    private var out: DataOutputStream? = null
    private var frameCounter = 0

    companion object {
        private const val MSG_KEY = 1
        private const val MSG_STATE = 2
        private const val STATE_EVERY_FRAMES = 120
    }

    val isRunning: Boolean get() = running.get()

    fun startHost(listenPort: Int) {
        if (!running.compareAndSet(false, true)) return
        thread(name = "retro-netplay-host", isDaemon = true) {
            try {
                onStatusUi("NetPlay: waiting on $listenPort…")
                val ss = ServerSocket()
                server = ss
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(listenPort))
                val client = ss.accept()
                socket = client
                wire(client)
                onStatusUi("NetPlay: client connected")
                readLoop(client)
            } catch (t: Throwable) {
                if (running.get()) onStatusUi("NetPlay: ${t.message ?: "host failed"}")
            } finally {
                stop()
            }
        }
    }

    fun startClient(host: String, port: Int) {
        if (!running.compareAndSet(false, true)) return
        thread(name = "retro-netplay-client", isDaemon = true) {
            try {
                onStatusUi("NetPlay: connecting $host:$port…")
                val s = Socket()
                socket = s
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 12_000)
                wire(s)
                onStatusUi("NetPlay: connected")
                readLoop(s)
            } catch (t: Throwable) {
                if (running.get()) onStatusUi("NetPlay: ${t.message ?: "connect failed"}")
            } finally {
                stop()
            }
        }
    }

    private fun wire(s: Socket) {
        s.tcpNoDelay = true
        out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
    }

    private fun readLoop(s: Socket) {
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        while (running.get() && !s.isClosed) {
            when (input.readInt()) {
                MSG_KEY -> {
                    val keyCode = input.readInt()
                    val action = input.readInt()
                    main.post {
                        if (!running.get()) return@post
                        if (action == KeyEvent.ACTION_DOWN) remoteKeys.add(keyCode) else remoteKeys.remove(keyCode)
                        retroView.sendKeyEvent(action, keyCode, remotePort)
                    }
                }
                MSG_STATE -> {
                    val len = input.readInt().coerceIn(0, 8 * 1024 * 1024)
                    val bytes = ByteArray(len)
                    input.readFully(bytes)
                    if (!isHost) {
                        main.post {
                            runCatching { retroView.unserializeState(bytes) }
                        }
                    }
                }
                else -> break
            }
        }
    }

    fun sendLocalKey(keyCode: Int, action: Int) {
        val stream = out ?: return
        thread(name = "retro-netplay-send", isDaemon = true) {
            runCatching {
                synchronized(stream) {
                    stream.writeInt(MSG_KEY)
                    stream.writeInt(keyCode)
                    stream.writeInt(action)
                    stream.flush()
                }
            }
        }
    }

    fun onFrameRendered() {
        if (!isHost || !running.get()) return
        frameCounter++
        if (frameCounter % STATE_EVERY_FRAMES != 0) return
        val stream = out ?: return
        thread(name = "retro-netplay-state", isDaemon = true) {
            runCatching {
                val state = retroView.serializeState()
                synchronized(stream) {
                    stream.writeInt(MSG_STATE)
                    stream.writeInt(state.size)
                    stream.write(state)
                    stream.flush()
                }
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { out?.close() }
        runCatching { socket?.close() }
        runCatching { server?.close() }
        out = null
        socket = null
        server = null
        remoteKeys.forEach { code ->
            main.post { retroView.sendKeyEvent(KeyEvent.ACTION_UP, code, remotePort) }
        }
        remoteKeys.clear()
    }

    private fun onStatusUi(msg: String) {
        main.post { onStatus(msg) }
    }
}
