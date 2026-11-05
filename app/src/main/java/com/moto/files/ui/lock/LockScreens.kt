package com.moto.files.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moto.files.model.LockKind
import com.moto.files.security.BiometricGate
import com.moto.files.security.LockManager

/**
 * App-lock gate: the only UI shown while locked. Biometric is offered once (if enabled);
 * every non-success path returns to the custom PIN/Pattern below.
 */
@Composable
fun LockGate(
    locks: LockManager,
    onUnlocked: () -> Unit,
    showBiometric: ((BiometricGate.Outcome) -> Unit) -> Unit
) {
    val s = locks.settings
    var error by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    var lockout by remember { mutableLongStateOf(locks.lockoutLeftMs()) }
    var triedBio by remember { mutableStateOf(false) }

    LaunchedEffect(s.biometric, triedBio) {
        if (s.biometric && !triedBio) {
            triedBio = true
            showBiometric { if (it == BiometricGate.Outcome.Success) onUnlocked() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Moto Files", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.kind == LockKind.PATTERN) "Draw pattern" else "Enter PIN",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(32.dp))
            if (lockout > 0) {
                Text("Locked — retry in ${lockout / 1000 + 1}s",
                    color = MaterialTheme.colorScheme.error)
            } else if (s.kind == LockKind.PATTERN) {
                PatternPad(reset) {
                    if (locks.authenticate(it)) onUnlocked()
                    else { error = true; reset++; lockout = locks.lockoutLeftMs() }
                }
            } else {
                PinPad(
                    pinLength = if (s.kind == LockKind.PIN) 4 else 0,
                    reset = reset
                ) {
                    if (locks.authenticate(it)) onUnlocked()
                    else { error = true; reset++; lockout = locks.lockoutLeftMs() }
                }
            }
            if (error && lockout == 0L) {
                Spacer(Modifier.height(16.dp))
                Text("Incorrect", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** In-app 3x3 pattern pad. Clears itself after every completed attempt. */
@Composable
fun PatternPad(reset: Int = 0, onDone: (String) -> Unit) {
    val picked = remember { mutableStateListOf<Int>() }
    LaunchedEffect(reset) { if (reset > 0) picked.clear() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (c in 0..2) {
                val i = r * 3 + c
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (i in picked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable {
                            if (i !in picked) {
                                picked.add(i)
                                if (picked.size >= 4) {
                                    val v = picked.joinToString("-")
                                    picked.clear()
                                    onDone(v)
                                }
                            }
                        }
                )
            }
        }
    }
}

/** In-app PIN/password pad. [pinLength] 0 = free-length password. [reset] clears input. */
@Composable
fun PinPad(pinLength: Int, reset: Int = 0, onDone: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    LaunchedEffect(reset) { if (reset > 0) input = "" }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val dots = if (pinLength > 0) pinLength else 8
            repeat(dots) { i ->
                Box(
                    Modifier
                        .size(if (i < input.length) 16.dp else 13.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < input.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        val keys = listOf("1","2","3","4","5","6","7","8","9","<-","0","OK")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { k ->
                        FilledTonalButton(
                            onClick = {
                                when (k) {
                                    "<-" -> input = input.dropLast(1)
                                    "OK" -> if (input.isNotEmpty()) {
                                        val v = input; input = ""; onDone(v)
                                    }
                                    else -> {
                                        input += k
                                        if (pinLength in 1..8 && input.length == pinLength) {
                                            val v = input; input = ""; onDone(v)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) { Text(k, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
    }
}

/**
 * Set/change the app lock. If a lock already exists, the current credential must be
 * entered first. The OS lock screen is never involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSetupScreen(
    locks: LockManager,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(if (locks.lockEnabled) -1 else 0) }
    var kind by remember { mutableStateOf(LockKind.PIN) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                -1 -> {
                    Text("Enter current credential",
                        style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    val k = locks.settings.kind
                    if (k == LockKind.PATTERN) {
                        PatternPad(reset) {
                            if (locks.authenticate(it)) step = 0 else { error = true; reset++ }
                        }
                    } else {
                        PinPad(if (k == LockKind.PIN) 4 else 0, reset) {
                            if (locks.authenticate(it)) step = 0 else { error = true; reset++ }
                        }
                    }
                }
                0 -> {
                    Text("Choose lock type", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    LockKind.entries.filter { it != LockKind.NONE }.forEach { k ->
                        OutlinedButton(
                            onClick = { kind = k; step = 1 },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) { Text(k.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
                else -> {
                    Text(
                        if (step == 1) "Enter new ${kind.name.lowercase()}"
                        else "Confirm ${kind.name.lowercase()}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(24.dp))
                    if (kind == LockKind.PATTERN) {
                        PatternPad(reset) { v ->
                            if (step == 1) { first = v; step = 2; error = false }
                            else if (v == first) { locks.setCredential(kind, v); onDone() }
                            else { error = true; reset++ }
                        }
                    } else {
                        PinPad(if (kind == LockKind.PIN) 4 else 0, reset) { v ->
                            if (step == 1) { first = v; step = 2; error = false }
                            else if (v == first) { locks.setCredential(kind, v); onDone() }
                            else { error = true; reset++ }
                        }
                    }
                }
            }
            if (error) {
                Spacer(Modifier.height(16.dp))
                Text("Mismatch — try again", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
