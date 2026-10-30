@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.BackupFile
import com.mazidulhaque.autopocket.logic.BackupCrypto
import kotlinx.coroutines.launch
import java.time.LocalDate

private val WarnBg: Color @Composable get() = AppTheme.colors.warnBackground
private val WarnText: Color @Composable get() = AppTheme.colors.warnText

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun InfoCard(text: String, warn: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (warn) WarnBg else MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(text, Modifier.padding(12.dp), color = if (warn) WarnText else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun PasswordField(value: String, label: String, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Turns a backup problem into a message in the user's language. */
@Composable
private fun backupErrorMessages(): (Exception) -> String {
    val wrong = stringResource(R.string.err_wrong_password)
    val notBackup = stringResource(R.string.err_not_backup)
    val damaged = stringResource(R.string.err_damaged)
    val tooNew = stringResource(R.string.err_too_new)
    val tooLarge = stringResource(R.string.err_too_large)
    val other = stringResource(R.string.something_went_wrong)
    return { e ->
        when (e) {
            is BackupCrypto.WrongPassword -> wrong
            is BackupCrypto.NotABackup -> notBackup
            is BackupFile.Invalid -> when (e.problem) {
                BackupFile.Problem.NOT_OURS -> notBackup
                BackupFile.Problem.TOO_NEW -> tooNew
                BackupFile.Problem.TOO_LARGE -> tooLarge
                BackupFile.Problem.DAMAGED -> damaged
            }
            else -> other
        }
    }
}

@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val period by vm.period.collectAsState()
    var busy by remember { mutableStateOf(false) }

    // Backup
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var includeHidden by remember { mutableStateOf(true) }
    val passwordOk = password.length >= BackupCrypto.MIN_PASSWORD && password == confirm

    // Restore
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupFile.Contents?>(null) }

    // Export
    var exportEverything by remember { mutableStateOf(false) }

    val errors = backupErrorMessages()
    val savedFormat = stringResource(R.string.backup_saved)
    val csvFormat = stringResource(R.string.spreadsheet_saved)
    val restoredFormat = stringResource(R.string.restored_n)
    fun runTask(task: suspend () -> String) {
        busy = true
        scope.launch {
            val message = try { task() } catch (e: Exception) { errors(e) }
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runTask {
            val n = vm.makeBackup(uri, password, includeHidden)
            password = ""; confirm = ""
            savedFormat.format(n)
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; restorePassword = ""; restoreError = null }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runTask { csvFormat.format(vm.exportCsv(uri, exportEverything)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_and_export)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoCard(
                stringResource(R.string.everything_here_works_on_this_phone),
            )

            Section(stringResource(R.string.save_a_backup_file))
            Text(
                stringResource(R.string.saves_all_your_entries_titles_and),
            )
            Text(
                stringResource(R.string.you_choose_where_to_save_it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PasswordField(password, stringResource(R.string.backup_password_label, BackupCrypto.MIN_PASSWORD)) { password = it }
            PasswordField(confirm, stringResource(R.string.type_the_password_again), isError = confirm.isNotEmpty() && confirm != password) { confirm = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeHidden, { includeHidden = it })
                Text(stringResource(R.string.include_hidden_zone_entries_protected_by), style = MaterialTheme.typography.bodySmall)
            }
            InfoCard(stringResource(R.string.remember_this_password_without_it_the), warn = true)
            Button(
                enabled = passwordOk && !busy,
                onClick = { saveBackup.launch("AutoPocketDiary-backup-${LocalDate.now()}.apdbackup") },
            ) { Text(stringResource(R.string.choose_where_to_save_backup)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(stringResource(R.string.restore_from_a_backup_file))
            Text(stringResource(R.string.opens_a_backup_file_you_saved))
            InfoCard(stringResource(R.string.restoring_replaces_everything_currently_in_the), warn = true)
            OutlinedButton(enabled = !busy, onClick = { openBackup.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.choose_backup_file)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(stringResource(R.string.export_to_spreadsheet_csv))
            Text(stringResource(R.string.creates_a_file_you_can_open))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !exportEverything, onClick = { exportEverything = false }, label = { Text(periodLabel(period)) })
                FilterChip(selected = exportEverything, onClick = { exportEverything = true }, label = { Text(stringResource(R.string.everything)) })
            }
            InfoCard(stringResource(R.string.this_file_is_not_locked_anyone), warn = true)
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val name = if (exportEverything) "all" else period.label.replace(Regex("[^A-Za-z0-9]+"), "-")
                    saveCsv.launch("AutoPocketDiary-$name.csv")
                },
            ) { Text(stringResource(R.string.choose_where_to_save_spreadsheet)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.backup_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.enter_the_password_you_chose_when))
                    PasswordField(restorePassword, stringResource(R.string.password), isError = restoreError != null) { restorePassword = it; restoreError = null }
                    restoreError?.let { Text(it, color = AppTheme.colors.moneyOut) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restorePassword.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                pendingRestore = vm.readBackup(uri, restorePassword)
                                restoreUri = null
                            } catch (e: Exception) {
                                restoreError = errors(e)
                            }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.open)) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingRestore?.let { contents ->
        val count = contents.transactions.count { !it.deleted }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.replace_with_backup)) },
            text = {
                Text(
                    stringResource(R.string.backup_holds, count),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    runTask {
                        vm.restore(contents)
                        restoredFormat.format(count)
                    }
                }) { Text(stringResource(R.string.replace), color = AppTheme.colors.moneyOut, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
