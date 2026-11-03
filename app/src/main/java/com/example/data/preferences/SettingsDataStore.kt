package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.localization.AppLanguage
import com.example.ui.theme.ThemeMode
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_hub_settings")

@JsonClass(generateAdapter = true)
data class AppPreferencesSnapshot(
    val themeMode: String = ThemeMode.DARK.name,
    val language: String = AppLanguage.ARABIC.name,
    val seededDefaults: Boolean = true,
    val deletedDefaultIds: Set<String> = emptySet()
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SEEDED_DEFAULTS = booleanPreferencesKey("seeded_defaults")
        val DELETED_DEFAULT_IDS = stringSetPreferencesKey("deleted_default_ids")
        val ISOLATED_COOKIE_VAULT_PREFIX = "cookie_vault_"
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LANGUAGE] ?: AppLanguage.ARABIC.name
        runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.ARABIC)
    }

    suspend fun isSeeded(): Boolean {
        return context.dataStore.data.first()[Keys.SEEDED_DEFAULTS] ?: false
    }

    suspend fun markSeeded() {
        context.dataStore.edit { it[Keys.SEEDED_DEFAULTS] = true }
    }

    suspend fun getDeletedDefaultIds(): Set<String> {
        return context.dataStore.data.first()[Keys.DELETED_DEFAULT_IDS] ?: emptySet()
    }

    suspend fun markDefaultSiteDeleted(siteId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DELETED_DEFAULT_IDS] ?: emptySet()
            prefs[Keys.DELETED_DEFAULT_IDS] = current + siteId
        }
    }

    suspend fun clearDeletedDefaultIds() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.DELETED_DEFAULT_IDS)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun getSnapshot(): AppPreferencesSnapshot {
        val prefs = context.dataStore.data.first()
        return AppPreferencesSnapshot(
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name,
            language = prefs[Keys.LANGUAGE] ?: AppLanguage.ARABIC.name,
            seededDefaults = prefs[Keys.SEEDED_DEFAULTS] ?: true,
            deletedDefaultIds = prefs[Keys.DELETED_DEFAULT_IDS] ?: emptySet()
        )
    }

    suspend fun restoreSnapshot(snapshot: AppPreferencesSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = snapshot.themeMode
            prefs[Keys.LANGUAGE] = snapshot.language
            prefs[Keys.SEEDED_DEFAULTS] = true
            prefs[Keys.DELETED_DEFAULT_IDS] = snapshot.deletedDefaultIds
        }
    }

    // Fallback per-profile cookie vault when WebView MULTI_PROFILE is unavailable on older WebView APKs
    suspend fun saveProfileCookieVault(profileKey: String, serializedCookiesJson: String) {
        val key = stringPreferencesKey("${Keys.ISOLATED_COOKIE_VAULT_PREFIX}$profileKey")
        context.dataStore.edit { it[key] = serializedCookiesJson }
    }

    suspend fun loadProfileCookieVault(profileKey: String): String? {
        val key = stringPreferencesKey("${Keys.ISOLATED_COOKIE_VAULT_PREFIX}$profileKey")
        return context.dataStore.data.first()[key]
    }

    suspend fun clearProfileCookieVault(profileKey: String) {
        val key = stringPreferencesKey("${Keys.ISOLATED_COOKIE_VAULT_PREFIX}$profileKey")
        context.dataStore.edit { it.remove(key) }
    }
}
