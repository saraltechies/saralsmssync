package com.saraltechies.saralsmssync.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.saraltechies.saralsmssync.R
import com.saraltechies.saralsmssync.core.Otp
import com.saraltechies.saralsmssync.core.SyncCrypto
import com.saraltechies.saralsmssync.data.AppPrefs
import com.saraltechies.saralsmssync.data.InboxDb
import com.saraltechies.saralsmssync.data.InboxMessage
import com.saraltechies.saralsmssync.sync.Forwarder
import com.saraltechies.saralsmssync.sync.LinkState
import com.saraltechies.saralsmssync.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Popup { NONE, FORWARD_CONSENT, ADD_PHONE, RENAME, LEAVE, CLEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLeftGroup: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs.get(context) }
    val db = remember { InboxDb.get(context) }
    val scope = rememberCoroutineScope()

    var forwarding by remember { mutableStateOf(prefs.forwarding) }
    var reading by remember { mutableStateOf(prefs.reading) }
    var popup by remember { mutableStateOf(Popup.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(canForward(context)) }
    var batteryFree by remember { mutableStateOf(isBatteryUnrestricted(context)) }

    val link by SyncService.link.collectAsState()
    val lastSent by Forwarder.lastSent.collectAsState()
    val version by db.changes.collectAsState()
    val messages by produceState(emptyList<InboxMessage>(), version) {
        value = withContext(Dispatchers.IO) { db.newest() }
    }

    // Comes back from Settings with permissions or battery choices possibly changed.
    LifecycleResumeEffect(Unit) {
        smsGranted = canForward(context)
        batteryFree = isBatteryUnrestricted(context)
        SyncService.update(context)
        onPauseOrDispose { }
    }

    val forwardPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        smsGranted = canForward(context)
        // Stays on when refused, so the card below explains what to allow.
        prefs.forwarding = true
        forwarding = true
        SyncService.update(context)
    }
    val readPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        prefs.reading = true
        reading = true
        SyncService.update(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { popup = Popup.ADD_PHONE }) {
                        Icon(Icons.Default.QrCode2, stringResource(R.string.add_phone))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.send_test)) },
                            onClick = {
                                menuOpen = false
                                val body = context.getString(R.string.test_message_body)
                                scope.launch(Dispatchers.IO) {
                                    Forwarder.enqueue(context, "Saral", body, System.currentTimeMillis(), -1)
                                    Forwarder.flush(context)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_phone)) },
                            onClick = {
                                menuOpen = false
                                popup = Popup.RENAME
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clear_messages)) },
                            onClick = {
                                menuOpen = false
                                popup = Popup.CLEAR
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.leave_group)) },
                            onClick = {
                                menuOpen = false
                                popup = Popup.LEAVE
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ToggleCard(
                    title = stringResource(R.string.forward_title),
                    detail = when {
                        forwarding && !smsGranted -> stringResource(R.string.forward_no_permission)
                        forwarding && lastSent != null ->
                            stringResource(R.string.forward_last_sent, formatTime(context, lastSent!!))
                        forwarding -> stringResource(R.string.forward_on)
                        else -> stringResource(R.string.forward_off)
                    },
                    checked = forwarding,
                    onChange = { on ->
                        if (on) {
                            popup = Popup.FORWARD_CONSENT
                        } else {
                            prefs.forwarding = false
                            forwarding = false
                            SyncService.update(context)
                        }
                    },
                )
            }
            item {
                ToggleCard(
                    title = stringResource(R.string.read_title),
                    detail = when {
                        !reading -> stringResource(R.string.read_off)
                        link == LinkState.CONNECTED -> stringResource(R.string.read_connected)
                        else -> stringResource(R.string.read_connecting)
                    },
                    checked = reading,
                    onChange = { on ->
                        if (on) {
                            readPermissions.launch(notificationPermissions())
                        } else {
                            prefs.reading = false
                            reading = false
                            SyncService.update(context)
                        }
                    },
                )
            }
            if (forwarding && !smsGranted) {
                item {
                    ActionCard(
                        text = stringResource(R.string.sms_blocked),
                        action = stringResource(R.string.open_app_settings),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                    )
                }
            }
            if ((forwarding || reading) && !batteryFree) {
                item {
                    ActionCard(
                        text = stringResource(R.string.battery_help),
                        action = stringResource(R.string.battery_open),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                }
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_messages),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(messages, key = { it.message.id }) { MessageCard(it) }
        }
    }

    when (popup) {
        Popup.NONE -> Unit
        Popup.FORWARD_CONSENT -> AlertDialog(
            onDismissRequest = { popup = Popup.NONE },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body)) },
            confirmButton = {
                TextButton(onClick = {
                    popup = Popup.NONE
                    forwardPermissions.launch(
                        notificationPermissions() + Manifest.permission.RECEIVE_SMS,
                    )
                }) { Text(stringResource(R.string.consent_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { popup = Popup.NONE }) { Text(stringResource(R.string.cancel)) }
            },
        )
        Popup.ADD_PHONE -> AddPhoneDialog(prefs) { popup = Popup.NONE }
        Popup.RENAME -> RenameDialog(prefs) { popup = Popup.NONE }
        Popup.CLEAR -> AlertDialog(
            onDismissRequest = { popup = Popup.NONE },
            title = { Text(stringResource(R.string.clear_messages)) },
            text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    popup = Popup.NONE
                    scope.launch(Dispatchers.IO) { db.clearMessages() }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { popup = Popup.NONE }) { Text(stringResource(R.string.cancel)) }
            },
        )
        Popup.LEAVE -> AlertDialog(
            onDismissRequest = { popup = Popup.NONE },
            title = { Text(stringResource(R.string.leave_group)) },
            text = { Text(stringResource(R.string.leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    popup = Popup.NONE
                    prefs.leaveGroup()
                    SyncService.update(context)
                    scope.launch(Dispatchers.IO) { db.clearAll() }
                    onLeftGroup()
                }) { Text(stringResource(R.string.leave)) }
            },
            dismissButton = {
                TextButton(onClick = { popup = Popup.NONE }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ToggleCard(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ActionCard(text: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun MessageCard(item: InboxMessage) {
    val context = LocalContext.current
    val message = item.message
    val code = remember(message.body) { Otp.find(message.body) }
    val lateBy = (item.arrivedAt - message.smsTime) / 1000
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message.sender, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    message.deviceName,
                    if (message.simSlot >= 0) stringResource(R.string.sim_label, message.simSlot + 1) else null,
                    formatTime(context, message.smsTime),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
            if (message.shortened) {
                Text(
                    stringResource(R.string.shortened),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lateBy >= 60) {
                Text(
                    stringResource(R.string.arrived_late, DateUtils.formatElapsedTime(lateBy)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (code != null) {
                TextButton(onClick = { copySensitive(context, code) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.copy_code, code))
                }
            }
        }
    }
}

@Composable
private fun AddPhoneDialog(prefs: AppPrefs, onClose: () -> Unit) {
    val context = LocalContext.current
    val key = remember { prefs.groupKey }
    if (key == null) {
        onClose()
        return
    }
    val code = remember(key) { SyncCrypto.pairingCode(key) }
    val qr = remember(code) { qrBitmap(code).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.add_phone)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.add_phone_help))
                Image(bitmap = qr, contentDescription = null, modifier = Modifier.size(240.dp))
                Text(stringResource(R.string.group_check, SyncCrypto.groupCheck(key)))
                Text(
                    stringResource(R.string.add_phone_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = { copySensitive(context, code) }) { Text(stringResource(R.string.copy_pairing)) }
        },
    )
}

@Composable
private fun RenameDialog(prefs: AppPrefs, onClose: () -> Unit) {
    var name by remember { mutableStateOf(prefs.deviceName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.rename_phone)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.rename_label)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    prefs.deviceName = name
                    onClose()
                },
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Forwarding needs SMS access, and notifications so that it always shows on this phone. */
private fun canForward(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun notificationPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

private fun isBatteryUnrestricted(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun formatTime(context: Context, time: Long): String = DateUtils.formatDateTime(
    context,
    time,
    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
)

/** Copies text, and asks Android not to preview it on screen, as it's a code. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.app_name), text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private fun qrBitmap(text: String): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 720, 720)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}
