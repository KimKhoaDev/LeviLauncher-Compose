package org.levimc.launcher.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.levimc.launcher.R
import org.levimc.launcher.core.curseforge.CurseForgeClient
import org.levimc.launcher.core.curseforge.models.Content
import org.levimc.launcher.core.curseforge.models.ContentSearchResponse
import org.levimc.launcher.ui.compose.*
import org.levimc.launcher.util.UIHelper
import kotlin.math.ceil
import kotlin.math.max

/** CurseForge catalog browser in Compose (glass cards, dropdown filters, built-in pagination). */
class CurseForgeActivity : BaseActivity() {
    private companion object { const val PAGE_SIZE = 20 }

    private class Category(val name: String, val id: Int)
    private class SortOption(val name: String, val field: String, val order: String)

    private val client = CurseForgeClient.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var categories: List<Category>
    private lateinit var sortOptions: List<SortOption>

    private var query by mutableStateOf("")
    private var catIndex by mutableIntStateOf(0)
    private var sortIndex by mutableIntStateOf(0)
    private var versionIndex by mutableIntStateOf(0)
    private var gameVersions by mutableStateOf<List<String>>(emptyList())
    private var contents by mutableStateOf<List<Content>>(emptyList())
    private var page by mutableIntStateOf(1)
    private var totalPages by mutableIntStateOf(1)
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var canRetry by mutableStateOf(false)
    private var requestSequence = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categories = listOf(
            Category(getString(R.string.catalog_cf_all_classes), 0), Category("Addons", 4984), Category("Maps", 6913),
            Category("Skins", 6925), Category("Textures", 6929), Category("Scripts", 6940),
        )
        sortOptions = listOf(
            SortOption(getString(R.string.catalog_cf_sort_popular), CurseForgeClient.SORT_POPULARITY, "desc"),
            SortOption(getString(R.string.catalog_cf_sort_downloads), CurseForgeClient.SORT_TOTAL_DOWNLOADS, "desc"),
            SortOption(getString(R.string.catalog_cf_sort_updated), CurseForgeClient.SORT_LAST_UPDATED, "desc"),
            SortOption(getString(R.string.catalog_cf_sort_name), CurseForgeClient.SORT_NAME, "asc"),
        )
        val allVersions = getString(R.string.catalog_cf_all_versions)
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    CurseForgeScreen(
                        query, { query = it }, { runSearch() },
                        categories.map { it.name }, catIndex, { catIndex = it; page = 1; loadContent() },
                        sortOptions.map { it.name }, sortIndex, { sortIndex = it; page = 1; loadContent() },
                        listOf(allVersions) + gameVersions, versionIndex, gameVersions.isNotEmpty(), { versionIndex = it; page = 1; loadContent() },
                        contents, page, totalPages, loading, message, canRetry,
                        onRetry = { loadContent(); if (gameVersions.isEmpty()) loadGameVersions() },
                        onPrev = { if (page > 1) { page--; loadContent() } },
                        onNext = { if (page < totalPages) { page++; loadContent() } },
                        onOpen = { c -> startActivity(Intent(this@CurseForgeActivity, ContentDetailsActivity::class.java).putExtra(ContentDetailsActivity.EXTRA_CONTENT, c)) },
                    )
                }
            }
        })
        loadContent()
        loadGameVersions()
    }

    private fun runSearch() { page = 1; loadContent(); UIHelper.hideKeyboard(this) }

    private fun loadGameVersions() {
        client.getGameVersions(object : CurseForgeClient.CurseForgeCallback<List<String>> {
            override fun onSuccess(result: List<String>?) {
                handler.post { if (!isFinishing && !isDestroyed) gameVersions = result ?: emptyList() }
            }
            override fun onError(t: Throwable?) { /* version filter stays disabled; search still works */ }
        })
    }

    private fun loadContent() {
        val q = query.trim()
        val cat = categories[catIndex]
        val sort = sortOptions[sortIndex]
        val version = if (versionIndex == 0) "" else gameVersions.getOrElse(versionIndex - 1) { "" }
        val request = ++requestSequence
        val requested = page
        loading = true
        message = null
        client.searchContent(q, cat.id, version, (requested - 1) * PAGE_SIZE, PAGE_SIZE, sort.field, sort.order,
            object : CurseForgeClient.CurseForgeCallback<ContentSearchResponse> {
                override fun onSuccess(result: ContentSearchResponse?) {
                    handler.post {
                        if (isFinishing || isDestroyed || request != requestSequence) return@post
                        loading = false
                        val data = result?.data
                        if (data != null) {
                            val total = result.pagination?.totalCount ?: 0L
                            totalPages = if (total > 0) max(1, ceil(total.toDouble() / PAGE_SIZE).toInt()) else if (data.size < PAGE_SIZE) requested else requested + 1
                            contents = data.filterNotNull()
                            canRetry = false
                            message = if (data.isEmpty()) getString(R.string.catalog_cf_no_results) else null
                        } else {
                            contents = emptyList(); totalPages = 1; canRetry = false
                            message = getString(R.string.catalog_cf_no_results)
                        }
                    }
                }
                override fun onError(t: Throwable?) {
                    handler.post {
                        if (isFinishing || isDestroyed || request != requestSequence) return@post
                        loading = false
                        val text = getString(R.string.error_message_format, t?.message)
                        if (contents.isEmpty()) { message = text; canRetry = true } else Toast.makeText(this@CurseForgeActivity, text, Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    override fun onDestroy() {
        requestSequence++
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

@Composable
private fun CurseForgeScreen(
    query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    classes: List<String>, classIdx: Int, onClass: (Int) -> Unit,
    sorts: List<String>, sortIdx: Int, onSort: (Int) -> Unit,
    versions: List<String>, versionIdx: Int, versionsEnabled: Boolean, onVersion: (Int) -> Unit,
    contents: List<Content>, page: Int, totalPages: Int, loading: Boolean, message: String?, canRetry: Boolean,
    onRetry: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onOpen: (Content) -> Unit,
) {
    val g = LocalGlass.current
    GlassBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GlassCard {
                    GText(stringResource(R.string.curseforge_title), 26, FontWeight.Bold)
                    GlassTextField(query, onQuery, stringResource(R.string.catalog_cf_search_hint), Modifier.fillMaxWidth(), onSearch)
                    GlassButton(stringResource(R.string.catalog_cf_search), onSearch, Modifier.fillMaxWidth(), primary = true)
                    GlassSelect(stringResource(R.string.catalog_cf_class), classes, classIdx, onClass)
                    GlassSelect(stringResource(R.string.catalog_cf_sort), sorts, sortIdx, onSort)
                    GlassSelect(stringResource(R.string.catalog_cf_game_version), versions, versionIdx, onVersion, enabled = versionsEnabled)
                    if (loading) GText("…", 16, color = g.subtext)
                }
            }
            if (message != null) item {
                GlassCard {
                    GText(message, 14, color = g.subtext)
                    if (canRetry) GlassButton(stringResource(R.string.catalog_retry), onRetry)
                }
            }
            items(contents, key = { it.id }) { c ->
                GlassCard(Modifier.clickable { onOpen(c) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val thumb = c.logo?.thumbnailUrl
                        if (!thumb.isNullOrEmpty()) RemoteAvatar(thumb, 56)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            GText(c.name ?: "", 16, FontWeight.Bold)
                            val by = c.authors?.mapNotNull { it?.name }?.joinToString(", ").orEmpty()
                            if (by.isNotEmpty()) GText(by, 12, color = g.subtext)
                        }
                    }
                    if (!c.summary.isNullOrEmpty()) GText(c.summary, 13, color = g.subtext)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Badge("⬇ ${c.downloadCount}", g.accent)
                        GText(stringResource(R.string.catalog_view) + " ›", 13, FontWeight.SemiBold, g.accent)
                    }
                }
            }
            if (contents.isNotEmpty()) item {
                GlassCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        GlassButton(stringResource(R.string.previous), onPrev)
                        GText("$page / $totalPages", 14, FontWeight.SemiBold)
                        GlassButton(stringResource(R.string.next), onNext)
                    }
                }
            }
        }
    }
}
