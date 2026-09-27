package com.anutk11.airpodscontrol

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Experimental Apple Accessory Protocol transport.
 * AAP/AACP uses Bluetooth Classic L2CAP PSM 0x1001.
 * Android's public createL2capChannel API is LE-only, so this class
 * constructs the hidden BluetoothSocket constructor reflectively.
 */
class ClassicL2capTransport(
    private val device: BluetoothDevice,
    private val log: (String) -> Unit
) {
    companion object {
        private const val PSM = 0x1001
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
            log("Opening Classic L2CAP PSM 0x${PSM.toString(16)}")
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
            log("Handshake response: ${hex(response)}")

            send(output, "SET_FEATURES", SET_FEATURES)
            send(output, "REQUEST_NOTIFICATIONS", REQUEST_NOTIFICATIONS)

            val notifications = readAvailable(input, 1200)
            if (notifications.isNotEmpty()) {
                log("AACP data: ${hex(notifications)}")
            } else {
                log("Connected; no notification packet received yet")
            }
            log("AACP transport ready")
            return true
        } catch (e: Throwable) {
            log("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            close()
            return false
        }
    }

    fun close() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }

    private fun send(output: OutputStream, name: String, bytes: ByteArray) {
        log("TX $name: ${hex(bytes)}")
        output.write(bytes)
        output.flush()
    }

    private fun readAvailable(input: InputStream, timeoutMs: Long): ByteArray {
        val end = System.currentTimeMillis() + timeoutMs
        val result = ArrayList<Byte>()
        while (System.currentTimeMillis() < end) {
            while (input.available() > 0) result.add(input.read().toByte())
            if (result.isNotEmpty()) break
            Thread.sleep(20)
        }
        return result.toByteArray()
    }

    private fun createClassicSocket(device: BluetoothDevice, psm: Int): BluetoothSocket {
        // Android has changed the private BluetoothSocket constructor signature
        // across releases. LibrePods uses HiddenApiBypass for this exact reason.
        // Do not hard-code a 7-argument signature.
        val constructors = HiddenApiBypass.getDeclaredConstructors(BluetoothSocket::class.java)
        log("BluetoothSocket constructors found: ${constructors.size}")

        val constructor = constructors.firstOrNull { c ->
            val p = c.parameterTypes
            p.size >= 6 &&
                p[0] == Int::class.javaPrimitiveType &&
                p[1] == Int::class.javaPrimitiveType &&
                p[2] == Boolean::class.javaPrimitiveType &&
                p[3] == Boolean::class.javaPrimitiveType &&
                p[4] == BluetoothDevice::class.java &&
                p[5] == Int::class.javaPrimitiveType
        } ?: throw NoSuchMethodException(
            "No compatible BluetoothSocket constructor. Signatures: " +
                constructors.joinToString(" | ") { it.parameterTypes.joinToString(",", "(", ")") }
        )

        val p = constructor.parameterTypes
        val args = when (p.size) {
            6 -> arrayOf<Any?>(3, -1, false, false, device, psm)
            7 -> arrayOf<Any?>(3, -1, false, false, device, psm, null)
            8 -> arrayOf<Any?>(3, -1, false, false, device, psm, null, null)
            else -> throw NoSuchMethodException("Unsupported BluetoothSocket constructor size: ${p.size}")
        }

        log("Using BluetoothSocket constructor with ${p.size} parameters")
        return HiddenApiBypass.newInstance(BluetoothSocket::class.java, p, *args) as BluetoothSocket
    }

