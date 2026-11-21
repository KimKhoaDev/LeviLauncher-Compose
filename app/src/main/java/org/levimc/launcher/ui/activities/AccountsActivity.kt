package org.levimc.launcher.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.levimc.launcher.R
import org.levimc.launcher.core.auth.MsftAccountStore
import org.levimc.launcher.core.auth.MsftAuthManager
import org.levimc.launcher.settings.FeatureSettings
import org.levimc.launcher.ui.compose.*
import org.levimc.launcher.ui.dialogs.CustomAlertDialog
import org.levimc.launcher.ui.dialogs.LoadingDialog
import org.levimc.launcher.util.AccountTextUtils
import org.levimc.launcher.util.DialogUtils
import java.util.concurrent.Executors

/** Accounts page, ported to Jetpack Compose. Auth flow and stores are unchanged. */
class AccountsActivity : BaseActivity() {
    private var enabled by mutableStateOf(true)
    private var accounts by mutableStateOf<List<MsftAccountStore.MsftAccount>>(emptyList())
    private val executor = Executors.newSingleThreadExecutor()
    private var loadingDialog: LoadingDialog? = null
    private lateinit var loginLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null && data.getBooleanExtra(MsftLoginActivity.EXTRA_LOGIN_COMPLETED, false)) {
                val name = data.getStringExtra(MsftLoginActivity.EXTRA_LOGIN_NAME) ?: getString(R.string.not_signed_in)
                Toast.makeText(this, getString(R.string.ms_login_success, name), Toast.LENGTH_SHORT).show()
            }
            refreshUI()
            refreshNavAccountUI()
        }
        setContentView(ComposeView(this).apply {
            setContent {
                GlassTheme {
                    AccountsScreen(
                        enabled, accounts,
                        onAdd = { loginLauncher.launch(Intent(this@AccountsActivity, MsftLoginActivity::class.java)) },
                        onSettings = { startActivity(Intent(this@AccountsActivity, SettingsActivity::class.java)) },
                        onSetActive = ::setActive,
                        onDelete = ::confirmDelete,
                    )
                }
            }
        })
        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        // The feature switch may have been changed in Settings ("Go to Settings").
        if (FeatureSettings.getInstance().isLauncherManagedMcLoginEnabled != enabled) refreshUI()
    }

    override fun onNavAccountChanged() = refreshUI()

    private fun refreshUI() {
        enabled = FeatureSettings.getInstance().isLauncherManagedMcLoginEnabled
        accounts = if (enabled) (MsftAccountStore.list(this) ?: emptyList()) else emptyList()
        refreshNavAccountUI()
    }

    private fun setActive(account: MsftAccountStore.MsftAccount) {
        loadingDialog = DialogUtils.ensure(this, loadingDialog)
        DialogUtils.showWithMessage(loadingDialog, getString(R.string.ms_login_auth_xbox_device))
        executor.execute {
            try {
                val refreshed = MsftAuthManager.refreshAccountAndActivate(this, account.id)
                val name = AccountTextUtils.displayNameOrNotSigned(this, refreshed)
                runOnUiThread {
                    DialogUtils.dismissQuietly(loadingDialog)
                    Toast.makeText(this, getString(R.string.ms_login_success, name), Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    DialogUtils.dismissQuietly(loadingDialog)
                    Toast.makeText(this, getString(R.string.ms_login_failed_detail, MsftAuthManager.describeError(e)), Toast.LENGTH_LONG).show()
                    refreshUI()
                }
            }
        }
    }

    private fun confirmDelete(account: MsftAccountStore.MsftAccount) {
        CustomAlertDialog(this)
            .setTitleText(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_confirm))
            .setPositiveButton(getString(R.string.ms_delete)) {
                MsftAccountStore.remove(this, account.id)
                Toast.makeText(this, R.string.ms_delete, Toast.LENGTH_SHORT).show()
                refreshUI()
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }
}

@Composable
private fun AccountsScreen(
    enabled: Boolean, accounts: List<MsftAccountStore.MsftAccount>,
    onAdd: () -> Unit, onSettings: () -> Unit,
    onSetActive: (MsftAccountStore.MsftAccount) -> Unit, onDelete: (MsftAccountStore.MsftAccount) -> Unit,
) {
    val g = LocalGlass.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val active = accounts.firstOrNull { it.active }
    GlassScaffold {
        GlassCard {
            GText(stringResource(R.string.misc_accounts_title), 28, FontWeight.Bold)
            GText(stringResource(R.string.misc_accounts_subtitle), 14, color = g.subtext)
        }
        if (!enabled) {
            GlassCard {
                GText(stringResource(R.string.misc_accounts_disabled_title), 17, FontWeight.Bold)
                GText(stringResource(R.string.misc_accounts_disabled_desc), 13, color = g.subtext)
                GlassButton(stringResource(R.string.misc_accounts_go_settings), onSettings, primary = true)
            }
        } else {
            GlassCard {
                GText(stringResource(R.string.misc_accounts_current), 13, FontWeight.SemiBold, g.subtext)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val url = AccountTextUtils.sanitizeUrl(active?.xboxAvatarUrl)
                    if (url != null) RemoteAvatar(url, 64)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GText(AccountTextUtils.displayNameOrNotSigned(ctx, active), 20, FontWeight.Bold)
                        if (active != null) {
                            Badge(stringResource(R.string.accounts_active_privilege), g.accent)
                            GText(active.xuid ?: "", 12, color = g.subtext)
                        }
                    }
                }
            }
            GlassCard {
                GText(stringResource(R.string.misc_accounts_list), 17, FontWeight.Bold)
                if (accounts.isEmpty()) GText(stringResource(R.string.not_signed_in), 13, color = g.subtext)
                accounts.forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            GText(AccountTextUtils.titleOrUnknown(a), 15, FontWeight.SemiBold)
                            GText(AccountTextUtils.subtitle(a), 12, color = g.subtext)
                        }
                        if (!a.active) GlassButton(stringResource(R.string.ms_set_active), { onSetActive(a) })
                        GlassButton(stringResource(R.string.ms_delete), { onDelete(a) })
                    }
                }
            }
            GlassButton(stringResource(R.string.ms_add_account), onAdd, Modifier.fillMaxWidth(), primary = true)
        }
        Spacer(Modifier.height(8.dp))
    }
}
