package com.sentinel.core.vpn

import java.nio.ByteBuffer

/**
 * PacketEngine: Zero-allocation high-performance packet parser.
 * Designed for the Sentinel high-speed VPN loop.
 */
object PacketEngine {

    // IP Protocol Constants
    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17

    /**
     * Extracts destination IP as a String with bounds check.
     */
    fun getDestIP(buffer: ByteBuffer): String {
        if (buffer.limit() < 20) return "0.0.0.0" 
        val offset = 16 // Offset for Destination IP in IPv4 header
        val a = buffer.get(offset).toInt() and 0xFF
        val b = buffer.get(offset + 1).toInt() and 0xFF
        val c = buffer.get(offset + 2).toInt() and 0xFF
        val d = buffer.get(offset + 3).toInt() and 0xFF
        return "$a.$b.$c.$d"
    }

    fun getSourceIP(buffer: ByteBuffer): String {
        if (buffer.limit() < 16) return "0.0.0.0"
        val offset = 12 // Offset for Source IP in IPv4 header
        val a = buffer.get(offset).toInt() and 0xFF
        val b = buffer.get(offset + 1).toInt() and 0xFF
        val c = buffer.get(offset + 2).toInt() and 0xFF
        val d = buffer.get(offset + 3).toInt() and 0xFF
        return "$a.$b.$c.$d"
    }

    fun getProtocol(buffer: ByteBuffer): Int {
        if (buffer.limit() < 10) return -1
        return buffer.get(9).toInt() and 0xFF
    }

    fun getIPHeaderLength(buffer: ByteBuffer): Int {
        if (buffer.limit() < 1) return 0
        val version = (buffer.get(0).toInt() and 0xF0) shr 4
        return if (version == 4) {
            (buffer.get(0).toInt() and 0x0F) * 4
        } else {
            40 // IPv6 fixed header length
        }
    }

    fun getSourcePort(buffer: ByteBuffer, ipHeaderLength: Int): Int {
        if (buffer.limit() < ipHeaderLength + 2) return 0
        return buffer.getShort(ipHeaderLength).toInt() and 0xFFFF
    }

    fun getDestPort(buffer: ByteBuffer, ipHeaderLength: Int): Int {
        if (buffer.limit() < ipHeaderLength + 4) return 0
        return buffer.getShort(ipHeaderLength + 2).toInt() and 0xFFFF
    }

    fun isIpv4(buffer: ByteBuffer): Boolean {
        if (buffer.limit() < 1) return false
        return (buffer.get(0).toInt() and 0xF0) shr 4 == 4
    }

    /**
     * Craft a bit-accurate DNS NXDOMAIN response.
     * Preserves the client's Transaction ID and Question section for protocol stability.
     */
    fun forgeNxDomain(originalPacket: ByteBuffer, ipHeaderLength: Int, udpHeaderLength: Int): ByteArray? {
        return try {
            val dnsOffset = ipHeaderLength + udpHeaderLength
            val transactionId = originalPacket.getShort(dnsOffset)
            
            // Header: 12 bytes
            val response = ByteBuffer.allocate(512)
            response.putShort(transactionId) // Same ID
            response.putShort(0x8183.toShort()) // Flags: QR=1, AA=1, RD=1, RA=1, RCODE=3 (NXDOMAIN)
            response.putShort(1.toShort()) // QDCOUNT: 1
            response.putShort(0.toShort()) // ANCOUNT: 0
            response.putShort(0.toShort()) // NSCOUNT: 0
            response.putShort(0.toShort()) // ARCOUNT: 0
            
            // Echo original Question section (starts at offset 12 in DNS)
            originalPacket.position(dnsOffset + 12)
            while (originalPacket.hasRemaining()) {
                val byte = originalPacket.get()
                response.put(byte)
                if (byte.toInt() == 0) break // End of name
            }
            response.putShort(originalPacket.short) // QTYPE
            response.putShort(originalPacket.short) // QCLASS
            
            val result = ByteArray(response.position())
            response.flip()
            response.get(result)
            result
        } catch (e: Exception) {
            null // Fallback to silent drop
        }
    }

    /**
     * Wrap a raw DNS payload in an IPv4 + UDP header.
     * Swaps Source and Destination to inject a response back to the client app.
     */
    fun createResponsePacket(originalPacket: ByteBuffer, ipHeaderLength: Int, dnsPayload: ByteArray): ByteArray {
        val ipv4HeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipv4HeaderLen + udpHeaderLen + dnsPayload.size
        
        val response = ByteBuffer.allocate(totalLen)
        
        // 1. IPv4 Header
        response.put(0x45.toByte()) // Version(4) + IHL(5)
        response.put(0.toByte()) // DSCP/ECN
        response.putShort(totalLen.toShort())
        response.putShort(0.toShort()) // ID
        response.putShort(0x4000.toShort()) // Flags (Don't Fragment)
        response.put(64.toByte()) // TTL
        response.put(PROTOCOL_UDP.toByte())
        response.putShort(0.toShort()) // Checksum placeholder — computed below after header is written

        // Swap IPs
        val srcIp = originalPacket.getInt(16) // Original Dest
        val dstIp = originalPacket.getInt(12) // Original Source
        response.putInt(srcIp)
        response.putInt(dstIp)

        // IPv4 header is now fully written (20 bytes). Compute real checksum and back-fill it.
        response.putShort(10, ipv4Checksum(response.array(), ipv4HeaderLen))
        
        // 2. UDP Header
        val srcPort = originalPacket.getShort(ipHeaderLength + 2) // Original Dest Port
        val dstPort = originalPacket.getShort(ipHeaderLength) // Original Src Port
        response.putShort(srcPort)
        response.putShort(dstPort)
        response.putShort((udpHeaderLen + dnsPayload.size).toShort())
        response.putShort(0.toShort()) // Checksum (0 is allowed for UDP)
        
        // 3. DNS Payload
        response.put(dnsPayload)
        
        return response.array()
    }

    /**
     * Craft a basic TCP RST packet.
     * Causes the client app to receive a 'Connection Refused' error instead of hanging.
     */
    fun createTcpRstPacket(originalPacket: ByteBuffer, ipHeaderLength: Int): ByteArray {
        val ipv4HeaderLen = 20
        val tcpHeaderLen = 20
        val totalLen = ipv4HeaderLen + tcpHeaderLen
        
        val response = ByteBuffer.allocate(totalLen)
        
        // 1. IPv4 Header
        response.put(0x45.toByte())
        response.put(0.toByte())
        response.putShort(totalLen.toShort())
        response.putShort(0.toShort())
        response.putShort(0x4000.toShort())
        response.put(64.toByte())
        response.put(PROTOCOL_TCP.toByte())
        response.putShort(0.toShort()) // Checksum
        
        val srcIp = originalPacket.getInt(16)
        val dstIp = originalPacket.getInt(12)
        response.putInt(srcIp)
        response.putInt(dstIp)
        
        // 2. TCP Header
        val srcPort = originalPacket.getShort(ipHeaderLength + 2)
        val dstPort = originalPacket.getShort(ipHeaderLength)
        response.putShort(srcPort)
        response.putShort(dstPort)
        
        // SEQ = original ACK, ACK = original SEQ + (1 if SYN/FIN else payloadLen)
        val originalSeq = originalPacket.getInt(ipHeaderLength + 4)
        val originalAck = originalPacket.getInt(ipHeaderLength + 8)
        response.putInt(originalAck)
        response.putInt(originalSeq + 1)
        
        response.putShort(0x5004.toShort()) // Offset 5 (20 bytes), Flags: RST (0x04)
        response.putShort(0.toShort()) // Window
        response.putShort(0.toShort()) // Checksum
        response.putShort(0.toShort()) // Urgent
        
        return response.array()
    }

    // RFC 791 one's-complement checksum over a byte range.
    // Called with the 20-byte IPv4 header (checksum field already zeroed).
    private fun ipv4Checksum(data: ByteArray, length: Int): Short {
        var sum = 0
        var i = 0
        while (i < length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toShort()
    }
}
