package org.levimc.launcher.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.levimc.launcher.R
import org.levimc.launcher.core.mods.Mod
import org.levimc.launcher.core.mods.ModManager
import org.levimc.launcher.core.packages.engine.Cancel
import org.levimc.launcher.ui.compose.*
import org.levimc.launcher.ui.packages.InstalledFragment
import org.levimc.launcher.ui.packages.PackageActions
import org.levimc.launcher.ui.views.MainViewModel
import org.levimc.launcher.ui.views.MainViewModelFactory
import java.io.File
import java.lang.ref.WeakReference

/** Mod details in Compose: glass hero, enable switch, metadata and package actions. */
class ModDetailActivity : BaseActivity() {
    private lateinit var viewModel: MainViewModel
    private var currentMod by mutableStateOf<Mod?>(null)
    private var modFilenameArg: String? = null
    private var pkgRow by mutableStateOf<InstalledFragment.Model.Row?>(null)
    private val pkgCancel = Cancel()
    private var pkgRequest = 0
    private var deleteRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.hasExtra("mod_filename") || !intent.hasExtra("mod_position")) {
            Toast.makeText(this, R.string.error_loading_mod, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        modFilenameArg = intent.getStringExtra("mod_filename")
        viewModel = ViewModelProvider(this, MainViewModelFactory(application)).get(MainViewModel::class.java)
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    ModDetailScreen(
                        currentMod, pkgRow,
                        onToggle = ::toggle, onConfigure = ::openModConfig, onDelete = ::confirmDelete,
                        onUpdate = { row -> ModManager.getInstance().getCurrentVersion()?.let { PackageActions.update(this@ModDetailActivity, it, row, pkgDone(false)) } },
                        onPromote = { row -> ModManager.getInstance().getCurrentVersion()?.let { PackageActions.promote(this@ModDetailActivity, it, row, pkgDone(false)) } },
                        onOpenPackage = { row -> PackageActions.openPackage(this@ModDetailActivity, ModManager.getInstance().getCurrentVersion(), row) },
                    )
                }
            }
        })
        viewModel.getModsLiveData().observe(this) { mods ->
            if (mods == null) return@observe
            val found = mods.firstOrNull { it.getId() == modFilenameArg }
            if (found == null && deleteRequested) { finish(); return@observe }
            currentMod = found
            loadPkgState(mods)
        }
        viewModel.refreshMods()
    }

    private fun toggle(enabled: Boolean) {
        val mod = currentMod ?: return
        if (enabled == mod.isEnabled()) return
        mod.setEnabled(enabled)
        viewModel.setModEnabled(mod.getId(), enabled)
        currentMod = mod
        Toast.makeText(this, if (enabled) R.string.mod_enabled else R.string.mod_disabled, Toast.LENGTH_SHORT).show()
    }

    private fun openModConfig() {
        val mod = currentMod ?: return
        if (!mod.hasEditableConfig()) return
        startActivity(Intent(this, ModConfigActivity::class.java)
            .putExtra(ModConfigActivity.EXTRA_MOD_NAME, mod.getDisplayName())
            .putExtra(ModConfigActivity.EXTRA_MOD_ROOT, mod.getModRootPath()))
    }

    private fun confirmDelete() {
        val version = ModManager.getInstance().getCurrentVersion()
        val mod = currentMod
        if (mod == null || version == null) return
        PackageActions.deleteModFolder(this, version, mod, pkgDone(true))
    }

    private fun pkgDone(delete: Boolean): PackageActions.Done {
        val self = WeakReference(this)
        return PackageActions.Done { changed ->
            val a = self.get()
            if (a == null || !changed || a.isFinishing || a.isDestroyed) return@Done
            if (delete) a.deleteRequested = true
            a.viewModel.reloadMods()
        }
    }

    private fun loadPkgState(mods: List<Mod>) {
        val request = ++pkgRequest
        InstalledFragment.Loader.loadForMods(pkgCancel, ModManager.getInstance().getCurrentVersion(), mods) { rows ->
            if (request != pkgRequest || isFinishing || isDestroyed) return@loadForMods
            pkgRow = modFilenameArg?.let { InstalledFragment.Model.ownerOf(rows, it) }
        }
    }

    override fun onDestroy() {
        pkgCancel.cancel()
        super.onDestroy()
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GText(label, 13, color = g.subtext, modifier = Modifier.weight(0.4f))
        GText(value, 14, FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun ModDetailScreen(
    mod: Mod?, row: InstalledFragment.Model.Row?,
    onToggle: (Boolean) -> Unit, onConfigure: () -> Unit, onDelete: () -> Unit,
    onUpdate: (InstalledFragment.Model.Row) -> Unit, onPromote: (InstalledFragment.Model.Row) -> Unit, onOpenPackage: (InstalledFragment.Model.Row) -> Unit,
) {
    val g = LocalGlass.current
    GlassScaffold {
        if (mod == null) {
            GlassCard { GText("…", 16, color = g.subtext) }
            return@GlassScaffold
        }
        val author = mod.getAuthor()?.trim().takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.mod_unknown_author)
        val version = mod.getVersion()?.trim().takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.mod_unknown_version)
        val description = mod.getDescription()?.trim().takeUnless { it.isNullOrEmpty() }
        val iconPath = mod.getIconPath()?.trim().takeUnless { it.isNullOrEmpty() }
        val mcVersions = if (mod.getMinecraftVersions().isEmpty()) stringResource(R.string.mod_all_versions) else mod.getMinecraftVersions().joinToString(", ")
        val configText = if (!mod.hasEditableConfig() || mod.getConfigFileCount() <= 0) stringResource(R.string.mod_no_config_files)
        else androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.mod_config_files_count, mod.getConfigFileCount(), mod.getConfigFileCount())

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (iconPath != null && File(iconPath).isFile) FileImage(iconPath, 64)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    GText(mod.getDisplayName() ?: "", 22, FontWeight.Bold)
                    GText(stringResource(R.string.mod_author_byline, author), 13, color = g.subtext)
                    Badge(stringResource(if (mod.isEnabled()) R.string.mod_status_enabled else R.string.mod_status_disabled), g.accent)
                }
                GlassSwitch(mod.isEnabled(), onToggle)
            }
        }
        if (row != null) {
            GlassCard {
                var chip = stringResource(R.string.pkg_mod_managed, row.pkg.identity, row.pkg.version)
                if (!row.explicit()) chip += " · " + stringResource(R.string.pkg_installed_dependency)
                GText(chip, 13, FontWeight.SemiBold, g.accent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.canUpdate()) GlassButton(stringResource(R.string.pkg_installed_update_available, row.targetVersion), { onUpdate(row) }, primary = true)
                    if (!row.explicit() && !row.leviLamina) GlassButton(stringResource(R.string.pkg_action_promote), { onPromote(row) })
                    if (row.hasPackagePage()) GlassButton(stringResource(R.string.pkg_menu_open_package), { onOpenPackage(row) })
                }
            }
        }
        GlassCard {
            if (description != null) {
                GText(stringResource(R.string.mod_detail_about), 15, FontWeight.Bold)
                GText(description, 13, color = g.subtext)
            }
            MetaRow(stringResource(R.string.mod_version_label), version)
            MetaRow(stringResource(R.string.mod_minecraft_versions_label), mcVersions)
            MetaRow(stringResource(R.string.mod_config_files_label), configText)
        }
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mod.hasEditableConfig()) GlassButton(stringResource(R.string.configure), onConfigure, primary = true)
                GlassButton(stringResource(R.string.delete_mod), onDelete)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
