package org.levimc.launcher.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.levimc.launcher.R
import org.levimc.launcher.ui.compose.*
import org.levimc.launcher.ui.widget.UiKit

private const val AVATAR_URL = "https://avatars.githubusercontent.com/u/62042544?v=4"
private const val MAINTAINER_AVATAR_URL = "https://yt3.googleusercontent.com/ft4khqXZ_fQn-DbSLg91kQy3_JUQ_73rbg18nOcmMtunX5bq25jzrThWQAk9YsFkTKFesUL7sg8=s160-c-k-c0x00ffffff-no-rj"
private const val URL_AFDIAN = "https://afdian.com/a/DreamGuXiang"
private const val URL_PATREON = "https://www.patreon.com/c/DreamGuXiang"
private const val URL_REPO = "https://github.com/LiteLDev/LeviLauncher-Android"
private const val URL_ORG = "https://github.com/LiteLDev"
private const val URL_ISSUES = "https://github.com/LiteLDev/LeviLauncher-Android/issues"
private const val URL_YOUTUBE = "https://www.youtube.com/c/mrpokeg"
private const val URL_LICENSE = "https://github.com/LiteLDev/LeviLauncher-Android/blob/HEAD/LICENSE"
private const val URL_RHYMC = "https://www.rhymc.com/"

/** About page, ported to Jetpack Compose with the liquid-glass design system. The nav shell stays in BaseActivity. */
class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent { GlassTheme { AboutScreen(::openUrl) } }
        })
        setActiveNavTab(R.id.nav_tab_about)
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }
}

@Composable
private fun PersonRow(avatar: String, name: String, badge: String, role: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteAvatar(avatar, 52)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GText(name, 16, FontWeight.Bold); Badge(badge, g.accent)
            }
            GText(role, 13, color = g.subtext)
        }
    }
}

@Composable
fun AboutScreen(open: (String) -> Unit) {
    val g = LocalGlass.current
    GlassScaffold {
        GlassCard {
            GText(stringResource(R.string.about_page_title), 28, FontWeight.Bold)
            GText(stringResource(R.string.misc_about_subtitle), 14, color = g.subtext)
        }
        GlassCard {
            SectionHeader("✍️", UiKit.accent(androidx.compose.ui.platform.LocalContext.current), stringResource(R.string.misc_about_authors))
            PersonRow(AVATAR_URL, stringResource(R.string.about_author_name), stringResource(R.string.about_author_badge), stringResource(R.string.about_author_role))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(stringResource(R.string.about_patreon), { open(URL_PATREON) })
                GlassButton(stringResource(R.string.about_afdian), { open(URL_AFDIAN) })
            }
            PersonRow(MAINTAINER_AVATAR_URL, "MrPokeG", stringResource(R.string.maintainer), stringResource(R.string.misc_about_maintainer_role))
            GlassButton(stringResource(R.string.youtube), { open(URL_YOUTUBE) })
        }
        GlassCard {
            SectionHeader("🙏", UiKit.AMBER, stringResource(R.string.misc_about_thanks))
            GText(stringResource(R.string.misc_about_thanks_desc), 13, color = g.subtext)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GText(stringResource(R.string.misc_about_rhymc), 15, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.misc_about_website), { open(URL_RHYMC) })
            }
        }
        GlassCard {
            SectionHeader("💖", UiKit.ROSE, stringResource(R.string.misc_about_sponsors))
            GText(stringResource(R.string.misc_about_sponsors_desc), 13, color = g.subtext)
        }
        GlassCard {
            SectionHeader("⭐", UiKit.BLUE, stringResource(R.string.about_source_title))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(stringResource(R.string.about_github_repo), { open(URL_REPO) })
                GlassButton(stringResource(R.string.misc_about_github_org), { open(URL_ORG) })
            }
            GText(stringResource(R.string.about_contribute_title), 15, FontWeight.SemiBold)
            GText(stringResource(R.string.misc_about_contribute_desc), 13, color = g.subtext)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(stringResource(R.string.about_issues), { open(URL_ISSUES) })
                GlassButton(stringResource(R.string.about_star_fork), { open(URL_REPO) }, primary = true)
            }
            GText(stringResource(R.string.misc_about_license_name), 15, FontWeight.SemiBold)
            GText(stringResource(R.string.misc_about_license_tip), 13, color = g.subtext)
            GlassButton(stringResource(R.string.misc_about_license_name), { open(URL_LICENSE) })
        }
        Spacer(Modifier.height(8.dp))
    }
}
