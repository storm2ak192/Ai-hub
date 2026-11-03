package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val description: String,
    val iconUrl: String? = null,
    val iconPreset: String = "hub",
    val accentColorHex: Long = 0xFF7C3AEDL,
    val isFavorite: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    // Isolated WebView profile name (unique per site, never shared across sites)
    val profileKey: String,
    // Per-site security & capability controls
    val javaScriptEnabled: Boolean = true,
    val allowCamera: Boolean = true,
    val allowMicrophone: Boolean = true,
    val allowPopups: Boolean = true,
    val allowDownloads: Boolean = true,
    val desktopMode: Boolean = false,
    // Manual page zoom percentage (50% .. 200%, default 100%)
    val zoomPercent: Int = 100,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: String,
    val siteName: String,
    val pageTitle: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemDownloadId: Long = -1L,
    val siteId: String,
    val siteName: String,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val contentLength: Long = -1L,
    val status: String = "COMPLETED",
    val createdAt: Long = System.currentTimeMillis()
)
