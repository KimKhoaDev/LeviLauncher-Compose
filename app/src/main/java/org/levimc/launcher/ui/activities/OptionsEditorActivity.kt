package org.levimc.launcher.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.levimc.launcher.R
import org.levimc.launcher.core.content.OptionsEditor
import org.levimc.launcher.core.content.OptionsEditor.OptionProperty
import org.levimc.launcher.ui.adapter.OptionsPropertiesAdapter
import org.levimc.launcher.ui.compose.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** options.txt editor in Compose; the property list reuses OptionsPropertiesAdapter through AndroidView. */
class OptionsEditorActivity : BaseActivity() {
    companion object {
        const val EXTRA_OPTIONS_PATH = "options_path"
        const val EXTRA_STORAGE_TYPE = "storage_type"
    }

    private lateinit var optionsEditor: OptionsEditor
    private val adapter = OptionsPropertiesAdapter()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var allProperties: List<OptionProperty> = emptyList()
    private var visible by mutableStateOf<Map<String, List<OptionProperty>>>(emptyMap())
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var dirty by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_OPTIONS_PATH)
        if (path == null) {
            Toast.makeText(this, R.string.options_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val storage = intent.getStringExtra(EXTRA_STORAGE_TYPE)
        val title = getString(R.string.edit_options) + if (storage != null) " ($storage)" else ""
        optionsEditor = OptionsEditor(File(path))
        adapter.setOnOptionChangedListener { property, newValue ->
            optionsEditor.setValue(property.key, newValue)
            property.value = newValue
            dirty = true
        }
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    OptionsScreen(title, query, { query = it; filter() }, visible, adapter, loading, message, dirty, ::save)
                }
            }
        })
        load()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun group(list: List<OptionProperty>): Map<String, List<OptionProperty>> {
        val out = LinkedHashMap<String, MutableList<OptionProperty>>()
        for (p in list) out.getOrPut(p.category) { ArrayList() }.add(p)
        return out
    }

    private fun filter() {
        val q = query.lowercase().trim()
        val filtered = if (q.isEmpty()) allProperties else allProperties.filter {
            it.displayName.lowercase().contains(q) || it.key.lowercase().contains(q) ||
                it.category.lowercase().contains(q) || it.value.lowercase().contains(q)
        }
        message = if (filtered.isEmpty() && allProperties.isNotEmpty()) getString(R.string.no_matching_options) else null
        visible = group(filtered)
    }

    private fun load() {
        loading = true
        executor.execute {
            try {
                optionsEditor.load()
                runOnUiThread {
                    loading = false
                    if (!optionsEditor.isLoaded) { message = getString(R.string.options_file_not_found); return@runOnUiThread }
                    allProperties = optionsEditor.optionProperties
                    if (allProperties.isEmpty()) message = getString(R.string.no_options_found) else filter()
                }
            } catch (e: Exception) {
                runOnUiThread { loading = false; message = getString(R.string.error_loading_options) + ": " + e.message }
            }
        }
    }

    private fun save() {
        if (!dirty) return
        loading = true
        dirty = false
        executor.execute {
            try {
                optionsEditor.save()
                runOnUiThread { loading = false; Toast.makeText(this, R.string.options_saved, Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false; dirty = true
                    Toast.makeText(this, getString(R.string.save_failed) + ": " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
private fun OptionsScreen(
    title: String, query: String, onQuery: (String) -> Unit, visible: Map<String, List<OptionProperty>>,
    adapter: OptionsPropertiesAdapter, loading: Boolean, message: String?, dirty: Boolean, onSave: () -> Unit,
) {
    val g = LocalGlass.current
    GlassBackground {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard {
                GText(title, 22, FontWeight.Bold)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(g.tint).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    BasicTextField(query, onQuery, singleLine = true, textStyle = TextStyle(color = g.text, fontSize = 15.sp), cursorBrush = SolidColor(g.accent), modifier = Modifier.fillMaxWidth())
                    if (query.isEmpty()) GText("🔎", 15, color = g.subtext)
                }
                if (dirty) GlassButton(stringResource(R.string.save), onSave, Modifier.fillMaxWidth(), primary = true)
                if (loading) GText("…", 16, color = g.subtext)
            }
            if (message != null) GlassCard { GText(message, 14, color = g.subtext) }
            else if (visible.isNotEmpty()) {
                GlassCard(Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx -> RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx); this.adapter = adapter } },
                        update = { adapter.setProperties(visible) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
