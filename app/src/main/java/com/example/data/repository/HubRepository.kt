package com.example.data.repository

import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DefaultSitesSeed
import com.example.data.local.HistoryDao
import com.example.data.local.HistoryEntity
import com.example.data.local.SiteDao
import com.example.data.local.SiteEntity
import com.example.data.preferences.AppPreferencesSnapshot
import com.example.data.preferences.SettingsDataStore
import com.example.ui.localization.AppLanguage
import com.example.ui.theme.ThemeMode
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@JsonClass(generateAdapter = true)
data class HubBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val preferences: AppPreferencesSnapshot,
    val sites: List<SiteEntity>
)

class HubRepository(
    private val siteDao: SiteDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val allSitesFlow: Flow<List<SiteEntity>> = siteDao.observeAllSites()
    val historyFlow: Flow<List<HistoryEntity>> = historyDao.observeHistory()
    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeDownloads()
    val themeModeFlow: Flow<ThemeMode> = settingsDataStore.themeModeFlow
    val languageFlow: Flow<AppLanguage> = settingsDataStore.languageFlow

    /**
     * Self-healing default site seeder:
     * Ensures all 7 built-in AI sites (ChatGPT, Gemini, Grok, Qwen, DeepSeek, Claude, Perplexity)
     * are present whenever they haven't been explicitly deleted by the user — recovering automatically
     * even if the local SQLite database was previously reset while DataStore preferences remained.
     */
    suspend fun ensureDefaultSitesSeeded() {
        val currentSites = siteDao.getAllSitesSnapshot()
        val existingIds = currentSites.map { it.id }.toSet()
        val deletedDefaultIds = settingsDataStore.getDeletedDefaultIds()

        val missingDefaults = DefaultSitesSeed.getDefaultSites().filter { defaultSite ->
            defaultSite.id !in existingIds && defaultSite.id !in deletedDefaultIds
        }

        if (missingDefaults.isNotEmpty()) {
            if (currentSites.isEmpty()) {
                siteDao.insertSitesIgnoreExisting(missingDefaults)
            } else {
                val maxSort = currentSites.maxOfOrNull { it.sortOrder } ?: -1
                val adjusted = missingDefaults.mapIndexed { idx, site ->
                    site.copy(sortOrder = maxSort + 1 + idx)
                }
                siteDao.insertSitesIgnoreExisting(adjusted)
            }
        }
        settingsDataStore.markSeeded()
    }

    suspend fun getAllSitesSnapshot(): List<SiteEntity> = siteDao.getAllSitesSnapshot()

    suspend fun addCustomSite(
        name: String,
        url: String,
        description: String,
        iconUrl: String?,
        iconPreset: String,
        accentColorHex: Long,
        javaScriptEnabled: Boolean = true,
        allowCamera: Boolean = true,
        allowMicrophone: Boolean = true,
        allowPopups: Boolean = true,
        allowDownloads: Boolean = true,
        desktopMode: Boolean = false
    ): SiteEntity {
        val currentSites = siteDao.getAllSitesSnapshot()
        val nextSortOrder = (currentSites.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val site = SiteEntity(
            id = "site_custom_$uniqueSuffix",
            name = name.trim(),
            url = url.trim(),
            description = description.trim(),
            iconUrl = iconUrl?.trim()?.takeIf { it.isNotEmpty() },
            iconPreset = iconPreset,
            accentColorHex = accentColorHex,
            isFavorite = false,
            isEnabled = true,
            sortOrder = nextSortOrder,
            isDefault = false,
            profileKey = "profile_custom_$uniqueSuffix",
            javaScriptEnabled = javaScriptEnabled,
            allowCamera = allowCamera,
            allowMicrophone = allowMicrophone,
            allowPopups = allowPopups,
            allowDownloads = allowDownloads,
            desktopMode = desktopMode
        )
        siteDao.insertSite(site)
        return site
    }

    suspend fun updateSite(site: SiteEntity) {
        siteDao.updateSite(site)
    }

    suspend fun toggleFavorite(site: SiteEntity) {
        siteDao.updateSite(site.copy(isFavorite = !site.isFavorite))
    }

    suspend fun toggleEnabled(site: SiteEntity) {
        siteDao.updateSite(site.copy(isEnabled = !site.isEnabled))
    }

    suspend fun deleteSite(site: SiteEntity) {
        if (site.isDefault) {
            settingsDataStore.markDefaultSiteDeleted(site.id)
        }
        siteDao.deleteSite(site)
    }

    suspend fun moveSiteOrder(siteId: String, moveUp: Boolean) {
        val ordered = siteDao.getAllSitesSnapshot().sortedBy { it.sortOrder }.toMutableList()
        val index = ordered.indexOfFirst { it.id == siteId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in ordered.indices) return

        val temp = ordered[index]
        ordered[index] = ordered[targetIndex]
        ordered[targetIndex] = temp

        val reindexed = ordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        siteDao.updateSites(reindexed)
    }

    suspend fun restoreDefaultSites() {
        settingsDataStore.clearDeletedDefaultIds()
        val currentSites = siteDao.getAllSitesSnapshot()
        val existingById = currentSites.associateBy { it.id }
        val defaults = DefaultSitesSeed.getDefaultSites()

        // Re-enable any existing default sites that may have been disabled
        val disabledDefaultsToEnable = defaults.mapNotNull { def ->
            val existing = existingById[def.id]
            if (existing != null && !existing.isEnabled) existing.copy(isEnabled = true) else null
        }
        if (disabledDefaultsToEnable.isNotEmpty()) {
            siteDao.updateSites(disabledDefaultsToEnable)
        }

        // Insert any missing default sites
        val missingDefaults = defaults.filter { it.id !in existingById.keys }
        if (missingDefaults.isNotEmpty()) {
            val maxSort = currentSites.maxOfOrNull { it.sortOrder } ?: -1
            val adjusted = if (currentSites.isEmpty()) {
                missingDefaults
            } else {
                missingDefaults.mapIndexed { idx, site ->
                    site.copy(sortOrder = maxSort + 1 + idx)
                }
            }
            siteDao.insertSitesIgnoreExisting(adjusted)
        }
    }

    suspend fun recordHistory(siteId: String, siteName: String, pageTitle: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        historyDao.insertHistory(
            HistoryEntity(
                siteId = siteId,
                siteName = siteName,
                pageTitle = pageTitle.ifBlank { siteName },
                url = url
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteHistoryById(id)
    suspend fun clearAllHistory() = historyDao.clearAllHistory()
    suspend fun deleteHistoryForSite(siteId: String) = historyDao.deleteHistoryForSite(siteId)

    suspend fun recordDownload(download: DownloadEntity): Long = downloadDao.insertDownload(download)
    suspend fun deleteDownloadItem(id: Long) = downloadDao.deleteDownloadById(id)
    suspend fun clearAllDownloads() = downloadDao.clearAllDownloads()

    suspend fun setThemeMode(mode: ThemeMode) = settingsDataStore.setThemeMode(mode)
    suspend fun setLanguage(language: AppLanguage) = settingsDataStore.setLanguage(language)

    suspend fun exportBackupJson(): String {
        val payload = HubBackupPayload(
            preferences = settingsDataStore.getSnapshot(),
            sites = siteDao.getAllSitesSnapshot()
        )
        val adapter = moshi.adapter(HubBackupPayload::class.java).indent("  ")
        return adapter.toJson(payload)
    }

    suspend fun importBackupJson(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(HubBackupPayload::class.java)
            val payload = adapter.fromJson(json) ?: return false
            if (payload.sites.isEmpty()) return false
            settingsDataStore.restoreSnapshot(payload.preferences)
            siteDao.deleteAllSites()
            siteDao.insertSites(payload.sites.mapIndexed { idx, site ->
                site.copy(
                    sortOrder = idx,
                    profileKey = site.profileKey.ifBlank { "profile_${site.id}" }
                )
            })
            true
        } catch (_: Exception) {
            false
        }
    }
}
