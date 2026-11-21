package org.levimc.launcher.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.levimc.launcher.R
import org.levimc.launcher.core.mods.config.ModConfigManager
import org.levimc.launcher.ui.adapter.ModConfigAdapter
import org.levimc.launcher.ui.compose.*
import org.levimc.launcher.ui.dialogs.CustomAlertDialog
import java.io.File

/**
 * Mod config editor page in Compose. The schema-driven editor list (ModConfigAdapter) is hosted
 * through AndroidView so editing behavior is unchanged.
 */
class ModConfigActivity : BaseActivity() {
    companion object {
        const val EXTRA_MOD_NAME = "mod_name"
        const val EXTRA_MOD_ROOT = "mod_root"
    }

    private val configManager = ModConfigManager()
    private var files by mutableStateOf<List<ModConfigManager.ConfigFile>>(emptyList())
    private var current by mutableStateOf<ModConfigManager.ConfigFile?>(null)
    private var adapter by mutableStateOf<ModConfigAdapter?>(null)
    private var loading by mutableStateOf(false)
    private var emptyMessage by mutableStateOf<String?>(null)
    private var modName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modName = intent.getStringExtra(EXTRA_MOD_NAME) ?: ""
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    ModConfigScreen(
                        title = getString(R.string.mod_config_title_format, modName),
                        files = files, current = current, adapter = adapter, loading = loading, emptyMessage = emptyMessage,
                        onPick = { loadConfig(it) }, onSave = { saveCurrentConfig() },
                    )
                }
            }
        })
        val root = intent.getStringExtra(EXTRA_MOD_ROOT)
        if (root.isNullOrBlank()) { emptyMessage = getString(R.string.mod_config_not_found); return }
        loadConfigFiles(File(root))
    }

    private fun loadConfigFiles(modRoot: File) {
        loading = true
        Thread {
            val found = configManager.scanConfigFiles(modRoot)
            runOnUiThread {
                loading = false
                files = found
                if (found.isEmpty()) emptyMessage = getString(R.string.mod_config_not_found) else loadConfig(found[0])
            }
        }.start()
    }

    private fun loadConfig(file: ModConfigManager.ConfigFile) {
        current = file
        loading = true
        emptyMessage = null
        Thread {
            try {
                val loaded = configManager.load(file)
                runOnUiThread { loading = false; adapter = ModConfigAdapter(loaded.value, loaded.schema) }
            } catch (e: Exception) {
                runOnUiThread { loading = false; adapter = null; emptyMessage = getString(R.string.mod_config_load_failed, e.message) }
            }
        }.start()
    }

    private fun saveCurrentConfig() {
        val a = adapter ?: return
        val file = current ?: return
        val error = a.validate()
        if (error != null) {
            CustomAlertDialog(this).setTitleText(getString(R.string.mod_config_validation_failed)).setMessage(error)
                .setPositiveButton(getString(R.string.dialog_positive_ok), null).show()
            return
        }
        loading = true
        Thread {
            try {
                configManager.save(file, a.root)
                runOnUiThread { loading = false; Toast.makeText(this, R.string.mod_config_saved, Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    CustomAlertDialog(this).setTitleText(getString(R.string.mod_config_save_failed)).setMessage(e.message)
                        .setPositiveButton(getString(R.string.dialog_positive_ok), null).show()
                }
            }
        }.start()
    }
}

@Composable
private fun ModConfigScreen(
    title: String, files: List<ModConfigManager.ConfigFile>, current: ModConfigManager.ConfigFile?,
    adapter: ModConfigAdapter?, loading: Boolean, emptyMessage: String?,
    onPick: (ModConfigManager.ConfigFile) -> Unit, onSave: () -> Unit,
) {
    val g = LocalGlass.current
    GlassBackground {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard {
                GText(title, 22, FontWeight.Bold, g.accent)
                if (files.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        files.forEach { f -> GlassButton(f.displayName, { onPick(f) }, primary = f == current) }
                    }
                }
                GlassButton(stringResource(R.string.save), onSave, Modifier.fillMaxWidth(), primary = true)
                if (loading) GText("…", 16, color = g.subtext)
            }
            if (emptyMessage != null) {
                GlassCard { GText(emptyMessage, 14, color = g.subtext) }
            } else if (adapter != null) {
                GlassCard(Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx -> RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx) } },
                        update = { it.adapter = adapter },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
