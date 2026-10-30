@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.data.isIncoming
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val previewMoney = DecimalFormat("#,##0.00")
private val previewDate = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/**
 * Adding SMS without the SMS permission: by sharing them to the app, pasting them, or importing an
 * SMS backup file. Found transactions are shown first so the user can untick any before adding.
 */
@Composable
fun AddFromSmsScreen(vm: MainViewModel, sharedText: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by remember { mutableStateOf(sharedText ?: "") }
    var account by remember { mutableStateOf("") }
    val found = remember { mutableStateListOf<Transaction>() }
    val chosen = remember { mutableStateListOf<Long>() }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val importedFormat = stringResource(R.string.imported_n)
    val failed = stringResource(R.string.sms_file_failed)

    fun search() {
        busy = true
        scope.launch {
            val list = vm.previewSms(text, account)
            found.clear(); found.addAll(list)
            chosen.clear(); chosen.addAll(list.mapNotNull { it.smsId })
            searched = true
            busy = false
        }
    }
    // Shared text is searched straight away.
    androidx.compose.runtime.LaunchedEffect(sharedText) { if (!sharedText.isNullOrBlank()) search() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val folder by vm.autoImportFolder.collectAsState()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setAutoImportFolder(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val message = try { importedFormat.format(vm.importSmsFile(uri)) } catch (e: Exception) { failed }
                busy = false
                snackbar.showSnackbar(message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_from_sms)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.share_how_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.share_how_text))
                    }
                }
            }
            item {
                Text(stringResource(R.string.paste_title), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    text, { text = it; searched = false },
                    label = { Text(stringResource(R.string.paste_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            item {
                OutlinedTextField(
                    account, { account = it; searched = false },
                    label = { Text(stringResource(R.string.paste_account_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(enabled = text.isNotBlank() && !busy, onClick = ::search) { Text(stringResource(R.string.find_transactions)) }
            }
            if (searched && found.isEmpty()) {
                item { Text(stringResource(R.string.nothing_found_in_text), color = AppTheme.colors.warnText) }
            }
            if (found.isNotEmpty()) {
                item { Text(stringResource(R.string.found_check_these), style = MaterialTheme.typography.titleMedium) }
                items(found, key = { it.smsId ?: it.hashCode().toLong() }) { t ->
                    val id = t.smsId ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in chosen, { on -> if (on) chosen.add(id) else chosen.remove(id) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (t.isIncoming) "+" else "−") + "৳ " + previewMoney.format(t.amount) + " · " + kindLabel(t.kind),
                                fontWeight = FontWeight.Medium,
                                color = if (t.isIncoming) AppTheme.colors.moneyIn else AppTheme.colors.moneyOut,
                            )
                            Text(
                                listOfNotNull(
                                    accountName(t.source),
                                    t.counterparty,
                                    Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
                                        .format(previewDate.withLocale(LocalConfiguration.current.locales[0])),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Button(
                        enabled = chosen.isNotEmpty(),
                        onClick = {
                            vm.addFromPreview(found.filter { (it.smsId ?: 0L) in chosen })
                            found.clear(); chosen.clear(); text = ""; searched = false
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.add_n_transactions, chosen.size)) }
                }
                item {
                    Text(
                        stringResource(R.string.date_from_sms_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!com.mazidulhaque.autopocket.BuildConfig.AUTO_SMS) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    AppCard {
                        Text(stringResource(R.string.auto_import_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_import_text))
                        Text(stringResource(R.string.auto_import_step1))
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.riteshsahu.SMSBackupRestore"))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(stringResource(R.string.open_play_store)) }
                        Text(stringResource(R.string.auto_import_step2))
                        Text(stringResource(R.string.auto_import_step3))
                        if (folder == null) {
                            Button(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.choose_backup_folder)) }
                        } else {
                            Text(stringResource(R.string.auto_import_on), color = AppTheme.colors.ok, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.importFromFolder() }) { Text(stringResource(R.string.read_now)) }
                                OutlinedButton(onClick = { vm.setAutoImportFolder(null) }) { Text(stringResource(R.string.turn_off)) }
                            }
                        }
                        Text(stringResource(R.string.auto_import_privacy), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text(stringResource(R.string.import_file_title), style = MaterialTheme.typography.titleMedium) }
            item { Text(stringResource(R.string.import_file_text)) }
            item {
                OutlinedButton(enabled = !busy, onClick = { openFile.launch(arrayOf("text/xml", "application/xml", "*/*")) }) {
                    Text(stringResource(R.string.choose_sms_file))
                }
            }
        }
    }
}
