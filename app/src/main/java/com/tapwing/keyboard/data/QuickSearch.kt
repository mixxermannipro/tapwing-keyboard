package com.tapwing.keyboard.data

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.URLEncoder

data class SearchApp(val label: String, val packageName: String, val usesWebSearchIntent: Boolean = false)

enum class QuickSearchEngine(
    val label: String,
    private val urlPrefix: String,
    private val app: SearchApp? = null
) {
    GOOGLE("Google", "https://www.google.com/search?q=",
        SearchApp("Google", "com.google.android.googlequicksearchbox", usesWebSearchIntent = true)),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", SearchApp("DuckDuckGo", "com.duckduckgo.mobile.android")),
    BING("Bing", "https://www.bing.com/search?q=", SearchApp("Bing", "com.microsoft.bing")),
    BRAVE("Brave Search", "https://search.brave.com/search?q=", SearchApp("Brave", "com.brave.browser")),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=", SearchApp("Ecosia", "com.ecosia.android")),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=");

    fun url(query: String): String? {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return null
        return urlPrefix + URLEncoder.encode(cleaned, "UTF-8")
    }

    fun selectedApp(packageName: String): SearchApp? = app?.takeIf { it.packageName == packageName }

    fun installedApp(context: Context): SearchApp? {
        val candidate = app ?: return null
        val intent = candidate.searchIntent("Tapwing", Uri.parse(urlPrefix + "Tapwing"))
        return candidate.takeIf { intent.resolveActivity(context.packageManager) != null }
    }
}

private fun SearchApp.searchIntent(query: String, url: Uri): Intent =
    if (usesWebSearchIntent) {
        Intent(Intent.ACTION_WEB_SEARCH).setPackage(packageName).putExtra(SearchManager.QUERY, query)
    } else {
        browserIntent(url).setPackage(packageName)
    }

private fun browserIntent(url: Uri) = Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)

internal fun openQuickSearch(context: Context, prefs: TapwingPrefs, query: String): Boolean {
    val engine = prefs.quickSearchEngine
    val url = engine.url(query)?.let(Uri::parse) ?: return false
    val app = engine.selectedApp(prefs.quickSearchAppPackage)
    if (app != null && context.tryOpen(app.searchIntent(query.trim(), url))) return true

    val manager = context.packageManager
    // A neutral web address identifies browsers without selecting an app link for the search engine.
    val web = browserIntent(Uri.parse("https://example.com"))
    val handlers = manager.queryIntentActivities(web, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
        .filter { result -> result.filter?.let { it.countDataAuthorities() == 0 && it.countDataPaths() == 0 } == true }
        .map { it.activityInfo.packageName }
        .distinct()
    val preferred = manager.resolveActivity(web, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    if (preferred != null && preferred in handlers &&
        context.tryOpen(browserIntent(url).setPackage(preferred))
    ) return true

    val browsers = handlers.map { browserIntent(url).setPackage(it) }
    val primary = browsers.firstOrNull() ?: return false
    if (browsers.size == 1) return context.tryOpen(primary)
    return context.tryOpen(Intent.createChooser(primary, null)
        .putExtra(Intent.EXTRA_INITIAL_INTENTS, browsers.drop(1).toTypedArray()))
}

private fun Context.tryOpen(intent: Intent): Boolean {
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
