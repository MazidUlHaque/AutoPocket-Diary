package com.mazidulhaque.autopocket.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.BuildConfig
import com.mazidulhaque.autopocket.R

@Composable
fun MoreScreen(
    hasSmsPermission: Boolean,
    onAddSms: () -> Unit,
    onSmsHelp: () -> Unit,
    onHidden: () -> Unit,
    onBudgets: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onDonate: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.more), style = MaterialTheme.typography.headlineMedium) }
        item {
            AppCard(padding = 6.dp) {
                Column {
                    if (BuildConfig.AUTO_SMS) {
                        MoreRow(Glyphs.Key, stringResource(R.string.sms_access), if (hasSmsPermission) stringResource(R.string.sms_access_on) else stringResource(R.string.sms_access_off), onSmsHelp)
                    }
                    MoreRow(Glyphs.Sms, stringResource(R.string.add_from_sms), stringResource(R.string.add_from_sms_sub), onAddSms)
                    MoreRow(Glyphs.Lock, stringResource(R.string.hidden_zone), stringResource(R.string.hidden_zone_sub), onHidden)
                    MoreRow(Glyphs.Bars, stringResource(R.string.budgets), stringResource(R.string.budgets_sub), onBudgets)
                    MoreRow(Glyphs.Folder, stringResource(R.string.backup_and_export), stringResource(R.string.backup_sub), onBackup)
                }
            }
        }
        item {
            AppCard(padding = 6.dp) {
                Column {
                    MoreRow(Glyphs.Gear, stringResource(R.string.settings), stringResource(R.string.settings_sub), onSettings)
                    MoreRow(Glyphs.Info, stringResource(R.string.about_and_privacy), stringResource(R.string.about_sub), onAbout)
                    MoreRow(Glyphs.Heart, stringResource(R.string.donate), stringResource(R.string.donate_sub), onDonate)
                }
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip12().clickableRow(onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphBadge(icon, MaterialTheme.colorScheme.primary, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
        }
        Icon(Glyphs.Chevron, null, Modifier.size(18.dp), tint = AppTheme.colors.muted)
    }
}

private fun Modifier.clip12() = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
private fun Modifier.clickableRow(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

/**
 * How to turn on SMS access in the Auto edition. Android blocks SMS for apps installed outside the
 * Play Store until the user allows "restricted settings" for the app; this walks through it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsPermissionHelpScreen(hasSmsPermission: Boolean, onRequest: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    fun openAppInfo() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    Scaffold(
        containerColor = AppTheme.colors.ground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.turn_on_sms)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.ground),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasSmsPermission) {
                AppCard(color = AppTheme.colors.inTile) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Glyphs.Check, null, tint = AppTheme.colors.ok)
                        Text(stringResource(R.string.sms_is_on), fontWeight = FontWeight.Bold, color = AppTheme.colors.ok)
                    }
                }
            }
            Text(stringResource(R.string.sms_help_intro))
            Step(1, stringResource(R.string.sms_step1_title), stringResource(R.string.sms_step1_text)) {
                Button(onClick = onRequest, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.allow_sms_access)) }
            }
            Step(2, stringResource(R.string.sms_step2_title), stringResource(R.string.sms_step2_text)) {
                OutlinedButton(onClick = ::openAppInfo, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.open_app_info)) }
            }
            Step(3, stringResource(R.string.sms_step3_title), stringResource(R.string.sms_step3_text)) {
                OutlinedButton(onClick = ::openAppInfo, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.open_app_info)) }
            }
            AppCard(color = AppTheme.colors.warnBackground, border = AppTheme.colors.warnBorder) {
                Text(stringResource(R.string.sms_help_stuck_title), fontWeight = FontWeight.Bold, color = AppTheme.colors.warnText)
                Text(stringResource(R.string.sms_help_stuck_text), color = AppTheme.colors.warnText)
            }
            Text(stringResource(R.string.sms_help_privacy), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String, action: @Composable () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(text)
        action()
    }
}
