@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.AppInfo
import com.mazidulhaque.autopocket.BuildConfig
import com.mazidulhaque.autopocket.security.AppIntegrity

@Composable
private fun privacyPoints() = listOf(
    stringResource(R.string.everything_stays_on_this_phone),
    stringResource(R.string.the_app_has_no_internet_permission),
    stringResource(R.string.your_data_is_stored_encrypted_on),
    stringResource(R.string.no_ads_no_accounts_no_signup),
    stringResource(R.string.not_included_in_google_backup_you),
)

@Composable
private fun limitPoints() = listOf(
    stringResource(if (BuildConfig.AUTO_SMS) R.string.it_only_sees_transactions_that_sent else R.string.limit_standard_sees),
    stringResource(R.string.it_reads_sms_automatically_and_may),
    stringResource(R.string.it_is_a_personal_record_not),
    stringResource(R.string.not_affiliated_with_bkash_nagad_rocket),
)

@Composable
private fun Bullets(points: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEach { p ->
            Row {
                Text("•  ", fontWeight = FontWeight.Bold)
                Text(p)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(stringResource(R.string.welcome_to_autopocket_diary), stringResource(R.string.private_by_design), stringResource(R.string.good_to_know), stringResource(R.string.free_made_with_care))
    val last = titles.lastIndex
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    ) {
        Text("${page + 1} / ${titles.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(titles[page], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (page) {
                0 -> Text(
                    stringResource(R.string.your_personal_diary_of_money_coming),
                )
                1 -> Bullets(privacyPoints())
                2 -> Bullets(limitPoints())
                else -> Text(
                    stringResource(if (BuildConfig.AUTO_SMS) R.string.welcome_free else R.string.welcome_free_standard, AppInfo.CREATOR, AppInfo.COPYRIGHT),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) TextButton(onClick = { page-- }) { Text(stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (page < last) page++ else onFinish() }) {
                Text(if (page < last) stringResource(R.string.next) else stringResource(R.string.get_started))
            }
        }
    }
}

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) { content() }
    }
}

@Composable
fun IntegrityWarning() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.dangerBackground)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.unofficial_copy), style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.dangerText)
            Text(
                stringResource(R.string.this_copy_of_autopocket_diary_was),
            )
            TextButton(onClick = { openLink(context, AppInfo.OFFICIAL_DOWNLOAD) }) { Text(stringResource(R.string.get_the_official_app)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onShowWelcome: () -> Unit) {
    val context = LocalContext.current
    val status = AppIntegrity.status(context)
    SubScreen(stringResource(R.string.about_and_privacy), onBack) {
        Text(stringResource(R.string.autopocket_diary), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.version_n, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.created_by, AppInfo.CREATOR))
        Text(AppInfo.COPYRIGHT)
        Spacer(Modifier.height(8.dp))
        when (status) {
            AppIntegrity.Status.OFFICIAL -> Text(stringResource(R.string.official_version_signed_by_the_developer), color = AppTheme.colors.ok)
            AppIntegrity.Status.DEVELOPMENT_BUILD -> Text(stringResource(R.string.development_build), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppIntegrity.Status.UNOFFICIAL -> IntegrityWarning()
        }

        SectionTitle(stringResource(R.string.your_privacy))
        Bullets(privacyPoints())

        SectionTitle(stringResource(R.string.what_the_app_can_and_cant))
        Bullets(limitPoints())

        SectionTitle(stringResource(R.string.permissions))
        Bullets(
            listOf(
                stringResource(if (BuildConfig.AUTO_SMS) R.string.read_sms_to_find_bank_and else R.string.permission_standard_no_sms),
                stringResource(R.string.fingerprint_screen_lock_only_to_open),
                stringResource(R.string.thats_all_no_internet_contacts_location),
            ),
        )

        SectionTitle(stringResource(R.string.official_download))
        Text(stringResource(R.string.only_install_autopocket_diary_from_the))
        TextButton(onClick = { openLink(context, AppInfo.OFFICIAL_DOWNLOAD) }) { Text(AppInfo.OFFICIAL_DOWNLOAD) }
        Text(
            stringResource(R.string.signing_fingerprint_sha256_careful_users_can),
            style = MaterialTheme.typography.bodySmall,
        )
        SelectionContainer {
            Text(
                AppIntegrity.certificateSha256(context) ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        SectionTitle(stringResource(R.string.license))
        Text(
            stringResource(R.string.license_text, AppInfo.COPYRIGHT),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onShowWelcome) { Text(stringResource(R.string.show_welcome_screens_again)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DonateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.number_copied)
    SubScreen(stringResource(R.string.donate), onBack) {
        Text(stringResource(R.string.autopocket_diary_is_completely_free_to), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.if_youd_like_you_can_send),
        )
        Spacer(Modifier.height(12.dp))
        Bullets(
            listOf(
                stringResource(R.string.donation_minimum, AppInfo.DONATION_MINIMUM),
                stringResource(R.string.the_app_never_takes_money_automatically),
                stringResource(R.string.donating_is_completely_optional_and_unlocks),
            ),
        )
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.bkash_personal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(AppInfo.DONATION_BKASH, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    clipboard.setText(AnnotatedString(AppInfo.DONATION_BKASH))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.copy_number)) }
            }
        }
        SectionTitle(stringResource(R.string.how_to_donate))
        Text(
            stringResource(R.string.donate_steps, AppInfo.DONATION_MINIMUM),
        )
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.warnBackground)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.stay_safe), style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.warnText)
                Text(
                    stringResource(R.string.the_developer_will_never_call_or),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
