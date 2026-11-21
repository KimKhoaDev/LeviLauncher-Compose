package org.levimc.launcher.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.levimc.launcher.ui.adapter.QuickLaunchAdapter
import org.levimc.launcher.ui.compose.*
import java.util.function.Consumer

/** Compose glass list for the Quick Launch page; dialogs and launching stay in QuickLaunchActivity. */
object QuickLaunchUi {
    @JvmStatic
    fun create(
        activity: BaseActivity,
        items: List<QuickLaunchAdapter.QuickLaunchItem>,
        onAction: Consumer<QuickLaunchAdapter.ActionType>,
    ): ComposeView = ComposeView(activity).apply {
        setContent {
            GlassTheme {
                val g = LocalGlass.current
                GlassBackground {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items) { item ->
                            GlassCard(Modifier.clickable { onAction.accept(item.actionType) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        GText(item.title, 16, FontWeight.Bold)
                                        GText(item.description, 13, color = g.subtext)
                                    }
                                    GText("›", 22, FontWeight.Bold, g.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
