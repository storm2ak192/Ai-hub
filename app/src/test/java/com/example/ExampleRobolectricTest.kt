package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DefaultSitesSeed
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.HubRepository
import com.example.util.ExternalLinkMatch
import com.example.util.FaviconResolver
import com.example.util.UrlSecurityValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HubRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsDataStore = SettingsDataStore(context)
        repository = HubRepository(
            siteDao = database.siteDao(),
            historyDao = database.historyDao(),
            downloadDao = database.downloadDao(),
            settingsDataStore = settingsDataStore
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify MainActivity launches immediately and renders Home UI`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_sites_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fab_add_site").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fav_quick_chip_site_chatgpt").assertIsDisplayed()

        // Navigate across tabs smoothly
        composeTestRule.onNodeWithTag("nav_tab_favorites").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_main_list").assertIsDisplayed()
    }

    @Test
    fun `verify FaviconResolver extracts original high-res site icon from HTML head`() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="shortcut icon" href="/favicon.ico">
                <link rel="icon" type="image/png" sizes="32x32" href="/icons/favicon-32x32.png">
                <link rel="apple-touch-icon" sizes="180x180" href="/assets/apple-touch-icon-180.png">
            </head>
            </html>
        """.trimIndent()

        val extracted = FaviconResolver.extractBestIconHref(
            html = sampleHtml,
            origin = "https://example.org",
            baseUrl = "https://example.org/home"
        )
        assertEquals("https://example.org/assets/apple-touch-icon-180.png", extracted)

        val instantFallback = FaviconResolver.buildInstantFaviconUrl("https://www.github.com/explore")
        assertEquals("https://www.google.com/s2/favicons?domain_url=https://github.com&sz=128", instantFallback)
    }

    @Test
    fun `verify app name is AI Hub`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Hub", appName)
    }

    @Test
    fun `verify 7 default AI sites have unique isolated profile keys`() {
        val defaults = DefaultSitesSeed.getDefaultSites()
        assertEquals(7, defaults.size)

        val expectedNames = listOf("ChatGPT", "Gemini", "Grok", "Qwen", "DeepSeek", "Claude", "Perplexity")
        assertEquals(expectedNames, defaults.map { it.name })

        val uniqueProfiles = defaults.map { it.profileKey }.toSet()
        assertEquals("Each site must have a distinct isolated profileKey", 7, uniqueProfiles.size)
        assertTrue(defaults.all { !it.iconUrl.isNullOrBlank() })
    }

    @Test
    fun `verify per-site desktop mode persistence`() = runBlocking {
        val customSite = repository.addCustomSite(
            name = "Claude Desktop",
            url = "https://claude.ai",
            description = "Desktop workspace",
            iconUrl = null,
            iconPreset = "claude",
            accentColorHex = 0xFFD97706L,
            desktopMode = false
        )
        assertFalse(customSite.desktopMode)

        repository.updateSite(customSite.copy(desktopMode = true))
        val updated = database.siteDao().getSiteById(customSite.id)
        assertNotNull(updated)
        assertTrue(updated!!.desktopMode)
    }

    @Test
    fun `verify external deep link routing preserves full path and query`() = runBlocking {
        database.siteDao().insertSites(DefaultSitesSeed.getDefaultSites())
        val customSite = repository.addCustomSite(
            name = "Notion",
            url = "https://www.notion.so",
            description = "Workspace notes",
            iconUrl = null,
            iconPreset = "globe",
            accentColorHex = 0xFF7C3AEDL
        )
        assertNotEquals("profile_chatgpt", customSite.profileKey)

        val allSites = repository.getAllSitesSnapshot()

        val chatGptDeepUrl = "https://chatgpt.com/c/67b89-test-thread?model=gpt-4o"
        val match1 = UrlSecurityValidator.resolveExternalLink(chatGptDeepUrl, allSites)
        assertTrue(match1 is ExternalLinkMatch.MatchedSite)
        val matchedChatGpt = match1 as ExternalLinkMatch.MatchedSite
        assertEquals("site_chatgpt", matchedChatGpt.site.id)
        assertEquals(chatGptDeepUrl, matchedChatGpt.fullUrl)

        val notionDeepUrl = "https://notion.so/myworkspace/Project-Roadmap-12345"
        val match2 = UrlSecurityValidator.resolveExternalLink(notionDeepUrl, allSites)
        assertTrue(match2 is ExternalLinkMatch.MatchedSite)
        assertEquals(customSite.id, (match2 as ExternalLinkMatch.MatchedSite).site.id)
        assertEquals(notionDeepUrl, match2.fullUrl)

        val blocked = UrlSecurityValidator.resolveExternalLink("javascript:alert(1)", allSites)
        assertTrue(blocked is ExternalLinkMatch.BlockedInvalidUrl)
    }

    @Test
    fun `verify backup json export and restore preserves sites and settings`() = runBlocking {
        database.siteDao().insertSites(DefaultSitesSeed.getDefaultSites())
        repository.addCustomSite(
            name = "GitHub",
            url = "https://github.com",
            description = "Code hosting",
            iconUrl = null,
            iconPreset = "code",
            accentColorHex = 0xFF2563EBL,
            desktopMode = true
        )
        val exportedJson = repository.exportBackupJson()
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("GitHub"))

        database.siteDao().deleteAllSites()
        assertEquals(0, repository.getAllSitesSnapshot().size)

        val restored = repository.importBackupJson(exportedJson)
        assertTrue(restored)
        val sitesAfterRestore = repository.getAllSitesSnapshot()
        assertEquals(8, sitesAfterRestore.size)
        assertTrue(sitesAfterRestore.first { it.name == "GitHub" }.desktopMode)
    }

    @Test
    fun `verify manual zoom percent persistence and silent desktop mode toggle on exit`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.viewmodel.HubViewModel(app)

        val site = repository.addCustomSite(
            name = "Zoomable Site",
            url = "https://perplexity.ai",
            description = "Search AI",
            iconUrl = null,
            iconPreset = "perplexity",
            accentColorHex = 0xFF0EA5E9L,
            desktopMode = false
        )
        assertEquals(100, site.zoomPercent)

        vm.openSite(site)
        val activeBefore = vm.activeBrowserSession.value
        assertNotNull(activeBefore)

        // Toggle desktop mode on & off — must NOT emit any snackbar message
        vm.toggleSiteDesktopMode(activeBefore!!.site)
        org.junit.Assert.assertNull(vm.snackbarMessage.value)
        assertTrue(vm.activeBrowserSession.value!!.site.desktopMode)

        vm.toggleSiteDesktopMode(vm.activeBrowserSession.value!!.site)
        org.junit.Assert.assertNull(vm.snackbarMessage.value)
        assertFalse(vm.activeBrowserSession.value!!.site.desktopMode)

        // Update manual zoom (clamped 50..200)
        vm.updateSiteZoomPercent(vm.activeBrowserSession.value!!.site, 130)
        assertEquals(130, vm.activeBrowserSession.value!!.site.zoomPercent)

        vm.updateSiteZoomPercent(vm.activeBrowserSession.value!!.site, 350)
        assertEquals(200, vm.activeBrowserSession.value!!.site.zoomPercent)

        vm.updateSiteZoomPercent(vm.activeBrowserSession.value!!.site, 20)
        assertEquals(50, vm.activeBrowserSession.value!!.site.zoomPercent)

        // Exit browser to Home — snackbarMessage must remain null
        vm.closeBrowserToHome()
        org.junit.Assert.assertNull(vm.activeBrowserSession.value)
        org.junit.Assert.assertNull(vm.snackbarMessage.value)
    }

    @Test
    fun `verify default sites self-heal when DB is empty even if DataStore was previously marked seeded`() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        settingsDataStore.clearDeletedDefaultIds()
        // Simulate previous app version having marked DataStore seeded while SQLite was wiped
        settingsDataStore.markSeeded()
        database.siteDao().deleteAllSites()
        assertEquals(0, database.siteDao().getSiteCount())

        // Calling ensureDefaultSitesSeeded MUST automatically restore all 7 default AI sites
        repository.ensureDefaultSitesSeeded()
        val restoredSites = repository.getAllSitesSnapshot()
        assertEquals(7, restoredSites.size)
        assertEquals(
            listOf("ChatGPT", "Gemini", "Grok", "Qwen", "DeepSeek", "Claude", "Perplexity"),
            restoredSites.map { it.name }
        )

        // Intentionally deleting 1 default site keeps it deleted across restarts
        val grok = restoredSites.first { it.id == "site_grok" }
        repository.deleteSite(grok)
        repository.ensureDefaultSitesSeeded()
        assertEquals(6, repository.getAllSitesSnapshot().size)
        assertFalse(repository.getAllSitesSnapshot().any { it.id == "site_grok" })

        // Disabling 1 default site and calling restoreDefaultSites restores all 7 AND re-enables disabled defaults
        val claude = repository.getAllSitesSnapshot().first { it.id == "site_claude" }
        repository.toggleEnabled(claude)
        assertFalse(database.siteDao().getSiteById("site_claude")!!.isEnabled)

        repository.restoreDefaultSites()
        val fullRestored = repository.getAllSitesSnapshot()
        assertEquals(7, fullRestored.size)
        assertTrue(fullRestored.all { it.isEnabled })
    }
}


