package com.sentinel.core.logs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sentinel.ui.logs.PacketLog

@Entity(tableName = "packet_logs")
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val destination: String,
    val status: String,
    val timestampMs: Long,
    @ColumnInfo(defaultValue = "") val threatLabel: String = ""
) {
    fun toPacketLog() = PacketLog(
        appName     = appName,
        packageName = packageName,
        destination = destination,
        status      = status,
        timestampMs = timestampMs,
        threatLabel = threatLabel
    )
}

fun PacketLog.toEntity() = PacketLogEntity(
    appName     = appName,
    packageName = packageName,
    destination = destination,
    status      = status,
    timestampMs = timestampMs,
    threatLabel = threatLabel
)
