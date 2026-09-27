package com.anutk11.airpodscontrol

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

class ClassicL2capTransport(
    private val device: BluetoothDevice,
    private val log: (String) -> Unit
) {
    companion object {
        private const val PSM = 0x1001
        // Defensive per-read budget, including coalesced notifications; not a protocol frame size.
        private const val MAX_RESPONSE_BYTES = 4096
        private val HANDSHAKE = byteArrayOf(
            0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        private val SET_FEATURES = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xFF.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        private val REQUEST_NOTIFICATIONS = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
    }

    private var socket: BluetoothSocket? = null

    fun connectAndHandshake(): Boolean {
        try {
            log("Opening Classic L2CAP PSM 0x" + PSM.toString(16))
            val s = createClassicSocket(device, PSM)
            socket = s
            log("Classic BluetoothSocket created")
            s.connect()
            log("L2CAP connected")

            val input = s.inputStream
            val output = s.outputStream

            send(output, "HANDSHAKE", HANDSHAKE)
            val response = readAvailable(input, 1200)

            if (response.isEmpty()) {
                log("ERROR: no response after AACP handshake")
                return false
            }

            log("Handshake response: " + hex(response))
            send(output, "SET_FEATURES", SET_FEATURES)
            send(output, "REQUEST_NOTIFICATIONS", REQUEST_NOTIFICATIONS)

            val notifications = readAvailable(input, 1200)
            if (notifications.isNotEmpty()) {
                log("AACP data: " + hex(notifications))
            } else {
                log("Connected; no notification packet received yet")
            }

            log("AACP transport ready")
            return true
        } catch (e: Throwable) {
            log("ERROR: " + e.javaClass.simpleName + ": " + (e.message ?: "unknown error"))
            close()
            return false
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    private fun send(output: OutputStream, name: String, bytes: ByteArray) {
        log("TX " + name + ": " + hex(bytes))
        output.write(bytes)
        output.flush()
    }

    private fun readAvailable(input: InputStream, timeoutMs: Long): ByteArray {
        val start = System.nanoTime()
        val timeoutNanos = timeoutMs * 1_000_000
        val result = ByteArray(MAX_RESPONSE_BYTES)
        var size = 0

        while (true) {
            val available = input.available()
            if (System.nanoTime() - start >= timeoutNanos) {
                if (size > 0 || available > 0) {
                    throw SocketTimeoutException("AACP response exceeded read deadline")
                }
                return ByteArray(0)
            }
            if (available > 0) {
                if (size == result.size) {
                    throw IOException("AACP response exceeds $MAX_RESPONSE_BYTES bytes")
                }
                val next = input.read()
                if (next == -1) return result.copyOf(size)
                result[size++] = next.toByte()
            } else {
                if (size > 0) return result.copyOf(size)
                Thread.sleep(20)
            }
        }
    }

    private fun hex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    private fun createClassicSocket(
        device: BluetoothDevice,
        psm: Int
    ): BluetoothSocket {
        HiddenApiBypass.setHiddenApiExemptions("L")

        val constructors = BluetoothSocket::class.java.declaredConstructors

        log("BluetoothSocket constructors found: " + constructors.size)
        constructors.forEachIndexed { index, constructor ->
            log(
                "  ctor[" + index + "]: " +
                    constructor.parameterTypes.joinToString(", ") { it.name }
            )
        }

        val constructor = constructors.firstOrNull { constructor ->
            val p = constructor.parameterTypes
            p.size == 7 &&
                p[0] == Int::class.javaPrimitiveType &&
                p[1] == Int::class.javaPrimitiveType &&
                p[2] == Boolean::class.javaPrimitiveType &&
                p[3] == Boolean::class.javaPrimitiveType &&
                p[4] == BluetoothDevice::class.java &&
                p[5] == Int::class.javaPrimitiveType
        } ?: constructors.firstOrNull { constructor ->
            val p = constructor.parameterTypes
            p.size == 9 &&
                p[0] == Int::class.javaPrimitiveType &&
                p[1] == Int::class.javaPrimitiveType &&
                p[2] == Boolean::class.javaPrimitiveType &&
                p[3] == Boolean::class.javaPrimitiveType &&
                p[4] == BluetoothDevice::class.java &&
                p[5] == Int::class.javaPrimitiveType &&
                p[7] == Boolean::class.javaPrimitiveType &&
                p[8] == Boolean::class.javaPrimitiveType
        }

        if (constructor == null) {
            throw NoSuchMethodException("No compatible BluetoothSocket constructor")
        }

        val args: Array<Any?> = when (constructor.parameterTypes.size) {
            7 -> arrayOf(3, -1, false, false, device, psm, null)
            9 -> arrayOf(3, -1, false, false, device, psm, null, false, false)
            else -> throw NoSuchMethodException("Unsupported BluetoothSocket constructor")
        }

        log("Using BluetoothSocket constructor with " + constructor.parameterTypes.size + " parameters")
        constructor.isAccessible = true
        return constructor.newInstance(*args) as BluetoothSocket
    }
}
