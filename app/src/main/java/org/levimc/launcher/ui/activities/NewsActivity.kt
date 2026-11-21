package org.levimc.launcher.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.levimc.launcher.R
import org.levimc.launcher.core.news.NewsFeed
import org.levimc.launcher.core.news.NewsItem
import org.levimc.launcher.core.news.NewsNotificationHelper
import org.levimc.launcher.core.news.NewsRepository
import org.levimc.launcher.core.news.NewsState
import org.levimc.launcher.ui.compose.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** News page, ported to Jetpack Compose (liquid-glass cards in a lazy list). */
class NewsActivity : BaseActivity() {
    companion object {
        const val EXTRA_NEWS_ID = "news_id"
        const val EXTRA_NEWS_URL = "news_url"
    }

    private var items by mutableStateOf<List<NewsItem>>(emptyList())
    private var unread by mutableStateOf<Set<String>>(emptySet())
    private var loading by mutableStateOf(false)
    private var rendered by mutableStateOf(false)
    private var needsNotificationPermission by mutableStateOf(false)
    private var scrollToId by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateNotificationButton() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    NewsScreen(
                        items, unread, loading, rendered, needsNotificationPermission, scrollToId,
                        onRefresh = { refreshNews(true) },
                        onEnableNotifications = { requestNotificationPermission() },
                        onOpen = { openUrl(it) },
                    )
                }
            }
        })
        setActiveNavTab(0)
        scrollToId = intent?.getStringExtra(EXTRA_NEWS_ID)
        updateNotificationButton()
        NewsRepository.loadCached(this) { feed, _ -> render(feed) }
        refreshNews(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scrollToId = intent.getStringExtra(EXTRA_NEWS_ID)
    }

    override fun onNewsChanged() {
        NewsRepository.loadCached(this) { feed, _ -> render(feed) }
    }

    private fun refreshNews(userRequested: Boolean) {
        loading = true
        val callback = NewsRepository.Callback { feed, error ->
            loading = false
            render(feed)
            if (userRequested) {
                Toast.makeText(
                    this,
                    if (error == null) R.string.news_refreshed else R.string.news_refresh_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        if (userRequested) NewsRepository.refresh(this, callback) else NewsRepository.refreshIfStale(this, callback)
    }

    private fun render(feedIn: NewsFeed?) {
        val feed = feedIn ?: NewsFeed()
        unread = NewsState.getUnreadIds(this, feed)
        items = feed.items?.filterNotNull() ?: emptyList()
        rendered = true
        loading = false
        NewsState.markAllRead(this, feed)
    }

    private fun openUrl(url: String?) {
        if (url.isNullOrEmpty()) return
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        if (!("https".equals(scheme, true) || "http".equals(scheme, true))) return
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun updateNotificationButton() {
        NewsNotificationHelper.initialize(this)
        needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private fun formatDate(value: String?): String = try {
    dateFormat.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
} catch (e: Exception) { value ?: "" }

@Composable
private fun NewsScreen(
    items: List<NewsItem>, unread: Set<String>, loading: Boolean, rendered: Boolean,
    needsNotificationPermission: Boolean, scrollToId: String?,
    onRefresh: () -> Unit, onEnableNotifications: () -> Unit, onOpen: (String?) -> Unit,
) {
    val g = LocalGlass.current
    val state = rememberLazyListState()
    LaunchedEffect(scrollToId, items) {
        val i = items.indexOfFirst { it.id == scrollToId }
        if (i >= 0) state.animateScrollToItem(i + 1)
    }
    GlassBackground {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GlassCard {
                    GText(stringResource(R.string.news_title), 28, FontWeight.Bold)
                    GText(stringResource(R.string.news_subtitle), 14, color = g.subtext)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(stringResource(R.string.news_refresh), { if (!loading) onRefresh() }, primary = true)
                        if (needsNotificationPermission) GlassButton(stringResource(R.string.news_enable_notifications), onEnableNotifications)
                    }
                    if (loading && !rendered) GText("…", 16, color = g.subtext)
                }
            }
            if (rendered && items.isEmpty()) item { GlassCard { GText(stringResource(R.string.news_empty), 14, color = g.subtext) } }
            itemsIndexed(items, key = { i, it -> it.id ?: i }) { _, item ->
                GlassCard(Modifier.clickable { onOpen(item.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.id in unread) Box(Modifier.size(8.dp).clip(CircleShape).background(g.accent))
                        Badge(if (item.category.isNullOrEmpty()) "News" else item.category, g.accent)
                        if (item.important) Badge(stringResource(R.string.news_important), Color(0xFFF43F5E))
                        Spacer(Modifier.weight(1f))
                        GText(formatDate(item.publishedAt), 12, color = g.subtext)
                    }
                    GText(item.title ?: "", 17, FontWeight.Bold)
                    if (!item.summary.isNullOrEmpty()) GText(item.summary, 13, color = g.subtext)
                    if (!item.url.isNullOrEmpty()) GText(stringResource(R.string.news_read_more) + " ↗", 13, FontWeight.SemiBold, g.accent)
                }
            }
        }
    }
}
