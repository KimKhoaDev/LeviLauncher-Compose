package org.levimc.launcher.ui.compose

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.levimc.launcher.ui.widget.UiKit
import org.levimc.launcher.util.PersonalizationManager
import kotlin.math.cos
import kotlin.math.sin

/** Colors for the Compose "liquid glass" design system, derived from the launcher's existing theme. */
@Immutable
class GlassColors(
    val dark: Boolean,
    val accent: Color,
    val text: Color,
    val subtext: Color,
    val tint: Color,
    val ambient: List<Color>,
    val base: Color,
)

val LocalGlass = staticCompositionLocalOf<GlassColors> { error("GlassTheme missing") }
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = remember {
        val dark = PersonalizationManager(ctx).isDarkMode(ctx)
        val accent = Color(UiKit.accent(ctx))
        GlassColors(
            dark = dark,
            accent = accent,
            text = if (dark) Color.White else Color(0xFF111418),
            subtext = if (dark) Color(0xB3FFFFFF) else Color(0x99111418),
            tint = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.45f),
            ambient = listOf(accent, Color(UiKit.BLUE), Color(UiKit.ROSE), Color(UiKit.AMBER)),
            base = if (dark) Color(0xFF0B0D12) else Color(0xFFEFF2F7),
        )
    }
    CompositionLocalProvider(LocalGlass provides colors, content = content)
}

/** Animated ambient background that the glass surfaces refract/blur, plus a scrolling page column. */
@Composable
fun GlassScaffold(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val g = LocalGlass.current
    val backdrop = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(g.base)) {
            g.ambient.forEachIndexed { i, c ->
                val ang = a * 6.28f + i * 1.7f
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = if (g.dark) 0.45f else 0.5f), Color.Transparent)),
                    size.minDimension * 0.7f,
                    Offset(size.width * (0.5f + 0.38f * cos(ang)), size.height * (0.5f + 0.38f * sin(ang * 1.2f))),
                )
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val g = LocalGlass.current
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(26.dp)
    val base = modifier.fillMaxWidth()
    val surface = if (backdrop != null) {
        base.drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = { vibrancy(); blur(16f.dp.toPx()) },
            onDrawSurface = { drawRect(g.tint) },
        )
    } else base.clip(shape).background(g.tint)
    Column(surface.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun GText(text: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, color: Color? = null, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    BasicText(text, modifier, TextStyle(color = color ?: g.text, fontSize = size.sp, fontWeight = weight))
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val g = LocalGlass.current
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) 0.93f else 1f, spring(0.5f, 700f), label = "press")
    Box(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
            .background(if (primary) g.accent else g.tint)
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { GText(text, 14, FontWeight.SemiBold, if (primary) Color.White else g.text) }
}

@Composable
fun SectionHeader(emoji: String, color: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(color).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            BasicText(emoji, style = TextStyle(fontSize = 17.sp))
        }
        GText(title, 17, FontWeight.Bold)
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) { GText(text, 11, FontWeight.SemiBold, color) }
}

private val httpClient = OkHttpClient()

@Composable
fun RemoteAvatar(url: String, size: Int = 52) {
    val g = LocalGlass.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.byteStream()?.let { BitmapFactory.decodeStream(it)?.asImageBitmap() } else null
                }
            } catch (e: Exception) { null }
        }
    }
    val shape = CircleShape
    Box(Modifier.size(size.dp).clip(shape).background(g.tint)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
