package com.example.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ComponentName
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.SiteEntity
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalAppStrings

data class BrowserProviderInfo(
    val packageName: String,
    val label: String
)

object BrowserProviderManager {
    const val CUSTOM_TABS_SERVICE_ACTION =
        "android.support.customtabs.action.CustomTabsService"

    fun discover(context: Context): List<BrowserProviderInfo> {
        val intent = Intent(CUSTOM_TABS_SERVICE_ACTION)
        val providers = context.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { resolveInfo ->
                val service = resolveInfo.serviceInfo ?: return@mapNotNull null
                val packageName = service.packageName ?: return@mapNotNull null
                val label = runCatching {
                    context.packageManager.getApplicationLabel(service.applicationInfo).toString()
                }.getOrDefault(packageName)
                BrowserProviderInfo(packageName, label)
            }
            .distinctBy { it.packageName }

        return providers.sortedWith(
            compareBy<BrowserProviderInfo> {
                when (it.packageName) {
                    "com.android.chrome" -> 0
                    "com.opera.browser" -> 1
                    "com.vivaldi.browser" -> 2
                    else -> 3
                }
            }.thenBy { it.label.lowercase() }
        )
    }

    fun labelFor(context: Context, packageName: String?): String {
        if (packageName.isNullOrBlank()) return ""
        return discover(context).firstOrNull { it.packageName == packageName }?.label
            ?: packageName
    }
}

@Composable
fun BrowserProviderPickerDialog(
    selectedPackage: String?,
    firstUse: Boolean,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (BrowserProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val providers = remember(context) { BrowserProviderManager.discover(context) }
    val isArabic = language == AppLanguage.ARABIC

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isArabic) "اختر متصفحك المفضل" else "Choose your preferred browser",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isArabic) {
                        "يمكنك تغييره في أي وقت من إعدادات التطبيق."
                    } else {
                        "You can change it at any time in app settings."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (providers.isEmpty()) {
                    Text(
                        if (isArabic) {
                            "لم يتم العثور على متصفح يدعم Custom Tabs. ثبّت متصفحًا متوافقًا ثم حاول مرة أخرى."
                        } else {
                            "No browser supporting Custom Tabs was found. Install a compatible browser and try again."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    providers.forEach { provider ->
                        TextButton(
                            onClick = { onSelect(provider) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    provider.label,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (provider.packageName == selectedPackage) {
                                    Text(
                                        if (isArabic) "المحدد" else "Selected",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!firstUse) {
                TextButton(onClick = onDismiss) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        }
    )
}

@Composable
fun BrowserProviderSettingsCard(
    selectedPackage: String?,
    language: AppLanguage,
    onSelectBrowser: (String) -> Unit
) {
    val context = LocalContext.current
    val providers = remember(context) { BrowserProviderManager.discover(context) }
    var showPicker by remember { mutableStateOf(false) }
    val isArabic = language == AppLanguage.ARABIC
    val selectedLabel = providers.firstOrNull { it.packageName == selectedPackage }?.label
        ?: if (selectedPackage.isNullOrBlank()) {
            if (isArabic) "لم يتم الاختيار بعد" else "Not selected"
        } else {
            if (isArabic) "المتصفح غير متاح" else "Browser unavailable"
        }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (isArabic) "المتصفح المفضل" else "Preferred browser",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        BrowserProviderPickerDialog(
            selectedPackage = selectedPackage,
            firstUse = false,
            language = language,
            onDismiss = { showPicker = false },
            onSelect = {
                onSelectBrowser(it.packageName)
                showPicker = false
            }
        )
    }
}

/**
 * Keeps a lightweight Custom Tabs connection alive for the selected browser.
 * Warmup and URL prefetch can reduce browser startup work; actual page speed still
 * depends on the selected browser, network and website.
 */
private object CustomTabsWarmupManager {
    private var activePackage: String? = null
    private var activeConnection: CustomTabsServiceConnection? = null
    private var activeSession: CustomTabsSession? = null

    @Synchronized
    fun prepare(context: Context, packageName: String, url: String) {
        if (activePackage == packageName && activeSession != null) {
            activeSession?.mayLaunchUrl(Uri.parse(url), null, null)
            return
        }

        val appContext = context.applicationContext
        activeConnection?.let { oldConnection ->
            runCatching { appContext.unbindService(oldConnection) }
        }
        activePackage = null
        activeConnection = null
        activeSession = null

        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient
            ) {
                client.warmup(0L)
                val newSession = client.newSession(object : CustomTabsCallback() {})
                synchronized(this@CustomTabsWarmupManager) {
                    activePackage = packageName
                    activeConnection = this
                    activeSession = newSession
                }
                newSession?.mayLaunchUrl(Uri.parse(url), null, null)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(this@CustomTabsWarmupManager) {
                    if (activePackage == packageName) {
                        activePackage = null
                        activeConnection = null
                        activeSession = null
                    }
                }
            }
        }

        val bound = CustomTabsClient.bindCustomTabsService(appContext, packageName, connection)
        if (bound) {
            activePackage = packageName
            activeConnection = connection
        }
    }

    @Synchronized
    fun sessionFor(packageName: String): CustomTabsSession? =
        if (activePackage == packageName) activeSession else null
}

@Composable
fun CustomTabsBrowserScreen(
    site: SiteEntity,
    initialUrl: String,
    selectedBrowserPackage: String?,
    language: AppLanguage,
    onSelectBrowser: (String) -> Unit,
    onReturnHome: () -> Unit,
    onRecordHistory: (String, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providers = remember(context) { BrowserProviderManager.discover(context) }
    val currentOnReturnHome by rememberUpdatedState(onReturnHome)

    var showPicker by remember(site.id, initialUrl) {
        mutableStateOf(selectedBrowserPackage.isNullOrBlank())
    }
    var launched by remember(site.id, initialUrl) { mutableStateOf(false) }
    var hasPaused by remember(site.id, initialUrl) { mutableStateOf(false) }
    var returnHandled by remember(site.id, initialUrl) { mutableStateOf(false) }

    LaunchedEffect(selectedBrowserPackage, providers, initialUrl) {
        if (selectedBrowserPackage.isNullOrBlank() ||
            providers.none { it.packageName == selectedBrowserPackage }
        ) {
            showPicker = true
        } else {
            showPicker = false
            if (!launched) {
                launched = true
                onRecordHistory(site.name, initialUrl)
                try {
                    CustomTabsWarmupManager.prepare(
                        context = context,
                        packageName = selectedBrowserPackage,
                        url = initialUrl
                    )
                    val customTabsIntent = CustomTabsIntent.Builder(
                        CustomTabsWarmupManager.sessionFor(selectedBrowserPackage)
                    )
                        .setShowTitle(true)
                        .setUrlBarHidingEnabled(true)
                        .build()
                    customTabsIntent.intent.setPackage(selectedBrowserPackage)
                    customTabsIntent.launchUrl(context, Uri.parse(initialUrl))
                } catch (_: Exception) {
                    launched = false
                    onShowMessage(
                        if (language == AppLanguage.ARABIC) {
                            "تعذر فتح الموقع باستخدام المتصفح المحدد."
                        } else {
                            "Could not open the site with the selected browser."
                        }
                    )
                    onReturnHome()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, site.id, initialUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && launched) {
                hasPaused = true
            } else if (
                event == Lifecycle.Event.ON_RESUME &&
                hasPaused &&
                launched &&
                !returnHandled
            ) {
                returnHandled = true
                currentOnReturnHome()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPicker) {
        BrowserProviderPickerDialog(
            selectedPackage = selectedBrowserPackage,
            firstUse = selectedBrowserPackage.isNullOrBlank() ||
                providers.none { it.packageName == selectedBrowserPackage },
            language = language,
            onDismiss = {
                if (providers.any { it.packageName == selectedBrowserPackage }) showPicker = false
            },
            onSelect = { provider ->
                showPicker = false
                onSelectBrowser(provider.packageName)
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!showPicker && !launched) {
            CircularProgressIndicator()
        }
    }
}
