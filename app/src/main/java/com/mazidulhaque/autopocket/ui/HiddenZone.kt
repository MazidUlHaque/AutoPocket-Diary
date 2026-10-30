@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.security.PhoneLock
import com.mazidulhaque.autopocket.security.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ErrorColor: Color @Composable get() = AppTheme.colors.moneyOut

private fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** The whole hidden zone: setup, lock screen, or the zone itself. Screenshots are blocked throughout. */
@Composable
fun HiddenZoneScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val pinLock = remember { PinLock(context) }
    val unlocked by vm.hiddenUnlocked.collectAsState()
    var setUp by remember { mutableStateOf(pinLock.isSetUp) }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val close = { vm.lockHidden(); onBack() }

    when {
        !setUp -> PinSetupScreen(pinLock, isReset = false, onBack = close) { setUp = true; vm.unlockHidden() }
        !unlocked -> LockScreen(
            pinLock, activity,
            onBack = close,
            onUnlocked = vm::unlockHidden,
            onErase = {
                vm.eraseHiddenZone()
                pinLock.clear()
                setUp = false
            },
        )
        else -> HiddenContent(vm, pinLock, activity, onBack = close)
    }
}

@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        actions = { actions() },
    )
}

/** Two PIN boxes (new + confirm). Returns the PIN through [onValid] once both match. */
@Composable
private fun NewPinFields(onValid: (String?) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val digitsOnly = pin.all { it.isDigit() }
    val longEnough = pin.length in PinLock.MIN_LENGTH..PinLock.MAX_LENGTH
    val matches = pin == confirm
    LaunchedEffect(pin, confirm) { onValid(if (digitsOnly && longEnough && matches) pin else null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            pin, { if (it.length <= PinLock.MAX_LENGTH) pin = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.new_pin_label, PinLock.MIN_LENGTH, PinLock.MAX_LENGTH)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            confirm, { if (it.length <= PinLock.MAX_LENGTH) confirm = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.type_the_pin_again)) },
            singleLine = true,
            isError = confirm.isNotEmpty() && !matches,
            supportingText = { if (confirm.isNotEmpty() && !matches) Text(stringResource(R.string.the_pins_dont_match)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PinSetupScreen(pinLock: PinLock, isReset: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneLockAvailable = remember { PhoneLock.available(context) }
    var newPin by remember { mutableStateOf<String?>(null) }
    var allowPhoneLock by remember { mutableStateOf(phoneLockAvailable) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(topBar = { SimpleTopBar(if (isReset) stringResource(R.string.choose_a_new_pin) else stringResource(R.string.set_up_the_hidden_zone), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isReset) {
                Text(stringResource(R.string.a_private_locked_place_for_entries))
                listOf(
                    stringResource(R.string.entries_you_move_here_disappear_from),
                    stringResource(R.string.it_opens_with_your_app_pin),
                    stringResource(R.string.it_locks_by_itself_whenever_you),
                    stringResource(R.string.hiding_an_entry_here_doesnt_hide),
                ).forEach { Row { Text("•  ", fontWeight = FontWeight.Bold); Text(it) } }
            }
            NewPinFields { newPin = it }
            if (phoneLockAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowPhoneLock, { allowPhoneLock = it })
                    Text(stringResource(R.string.also_allow_fingerprint_phone_screen_lock))
                }
            }
            Text(
                if (phoneLockAvailable) stringResource(R.string.if_you_forget_the_pin_you)
                else stringResource(R.string.your_phone_has_no_screen_lock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = newPin != null && !saving,
                onClick = {
                    val pin = newPin ?: return@Button
                    saving = true
                    scope.launch {
                        withContext(Dispatchers.Default) { pinLock.setPin(pin) }
                        pinLock.phoneLockAllowed = allowPhoneLock
                        onDone()
                    }
                },
            ) { Text(if (isReset) stringResource(R.string.save_new_pin) else stringResource(R.string.create_hidden_zone)) }
        }
    }
}

@Composable
private fun LockScreen(
    pinLock: PinLock,
    activity: FragmentActivity?,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    onErase: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val phoneLockReady = remember { activity != null && pinLock.phoneLockAllowed && PhoneLock.available(activity) }
    val phoneLockExists = remember { activity != null && PhoneLock.available(activity) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedFor by remember { mutableLongStateOf(pinLock.lockoutSecondsLeft()) }
    var checking by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    var resettingPin by remember { mutableStateOf(false) }

    val wrongOne = stringResource(R.string.wrong_pin_one)
    val wrongMany = stringResource(R.string.wrong_pin_many)
    val openTitle = stringResource(R.string.prompt_open_title)
    val resetTitle = stringResource(R.string.prompt_reset_title)
    val confirmIt = stringResource(R.string.prompt_confirm)
    fun askPhone() {
        activity?.let { PhoneLock.prompt(it, openTitle, confirmIt, onUnlocked) }
    }
    LaunchedEffect(Unit) { if (phoneLockReady) askPhone() }
    LaunchedEffect(lockedFor) {
        if (lockedFor > 0) { delay(1000); lockedFor = pinLock.lockoutSecondsLeft() }
    }

    if (resettingPin) {
        PinSetupScreen(pinLock, isReset = true, onBack = { resettingPin = false }, onDone = onUnlocked)
        return
    }

    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.hidden_zone), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.enter_your_pin), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                pin, { if (it.length <= PinLock.MAX_LENGTH) pin = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.pin)) },
                singleLine = true,
                enabled = lockedFor == 0L,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (lockedFor > 0) {
                Text(stringResource(R.string.locked_out, lockedFor), color = ErrorColor)
            } else {
                message?.let { Text(it, color = ErrorColor) }
            }
            Button(
                enabled = pin.length >= PinLock.MIN_LENGTH && lockedFor == 0L && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val result = withContext(Dispatchers.Default) { pinLock.check(pin) }
                        checking = false
                        pin = ""
                        when (result) {
                            PinLock.Result.Ok -> onUnlocked()
                            is PinLock.Result.Wrong -> message =
                                if (result.triesBeforeLockout == 1) wrongOne else wrongMany.format(result.triesBeforeLockout)
                            is PinLock.Result.LockedOut -> lockedFor = result.secondsLeft
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.unlock)) }
            if (phoneLockReady) {
                OutlinedButton(onClick = ::askPhone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.use_fingerprint_or_phone_lock)) }
            }
            TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.forgot_pin)) }
        }
    }

    if (forgot) {
        if (phoneLockExists) {
            AlertDialog(
                onDismissRequest = { forgot = false },
                title = { Text(stringResource(R.string.forgot_pin)) },
                text = { Text(stringResource(R.string.confirm_its_you_with_your_phones)) },
                confirmButton = {
                    TextButton(onClick = {
                        forgot = false
                        activity?.let { PhoneLock.prompt(it, resetTitle, confirmIt) { resettingPin = true } }
                    }) { Text(stringResource(R.string.continue_button)) }
                },
                dismissButton = { TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.cancel)) } },
            )
        } else {
            var typed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { forgot = false },
                title = { Text(stringResource(R.string.forgot_pin)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.your_phone_has_no_screen_lock_2),
                        )
                        Text(stringResource(R.string.type_erase_to_confirm), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(typed, { typed = it }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(enabled = typed.trim() == "ERASE", onClick = { forgot = false; onErase() }) {
                        Text(stringResource(R.string.erase_hidden_zone), color = ErrorColor)
                    }
                },
                dismissButton = { TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun HiddenContent(vm: MainViewModel, pinLock: PinLock, activity: FragmentActivity?, onBack: () -> Unit) {
    val period by vm.period.collectAsState()
    val summary by vm.hiddenSummary.collectAsState()
    val sources by vm.sources.collectAsState()
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickingRange by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var phoneLockAllowed by remember { mutableStateOf(pinLock.phoneLockAllowed) }
    val phoneLockExists = remember { activity != null && PhoneLock.available(activity) }

    if (changingPin) {
        PinSetupScreen(pinLock, isReset = true, onBack = { changingPin = false }, onDone = { changingPin = false })
        return
    }

    Scaffold(
        topBar = {
            SimpleTopBar(stringResource(R.string.hidden_zone), onBack) {
                IconButton(onClick = onBack) { Icon(Icons.Default.Lock, stringResource(R.string.lock_now)) }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.menu)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.change_pin)) }, onClick = { menuOpen = false; changingPin = true })
                    if (phoneLockExists) {
                        DropdownMenuItem(
                            text = { Text(if (phoneLockAllowed) stringResource(R.string.turn_off_fingerprint_phone_lock) else stringResource(R.string.allow_fingerprint_phone_lock)) },
                            onClick = {
                                menuOpen = false
                                phoneLockAllowed = !phoneLockAllowed
                                pinLock.phoneLockAllowed = phoneLockAllowed
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_hidden_entry)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.only_you_can_see_these_they),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PeriodPicker(
                    period,
                    onType = { if (it == com.mazidulhaque.autopocket.logic.PeriodType.CUSTOM) pickingRange = true else vm.setPeriodType(it) },
                    onPrevious = vm::previousPeriod,
                    onNext = vm::nextPeriod,
                    onPickRange = { pickingRange = true },
                )
            }
            item { HeroCard(summary, stringResource(R.string.hidden_totals)) }
            item { Spacer(Modifier.height(4.dp)) }
            if (summary.transactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nothing_hidden_in_this_period_to),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(summary.transactions, key = { it.id }) { t -> TransactionRow(t) { editing = t } }
        }
    }

    if (pickingRange) {
        RangePickerDialog(period, onDismiss = { pickingRange = false }) { from, to ->
            vm.setCustomPeriod(from, to)
            pickingRange = false
        }
    }
    if (adding) {
        TransactionDialog(
            null, sources,
            onDismiss = { adding = false },
            onSave = { t, remember -> vm.save(t.copy(hidden = true), remember); adding = false },
            onDelete = null,
        )
    }
    editing?.let { t ->
        TransactionDialog(
            t, sources,
            onDismiss = { editing = null },
            onSave = { updated, remember -> vm.save(updated, remember); editing = null },
            onDelete = { vm.delete(t); editing = null },
            hideLabel = stringResource(R.string.move_back_to_main_screen),
            onToggleHidden = { vm.setHidden(t, false); editing = null },
        )
    }
}
