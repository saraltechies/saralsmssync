package com.saraltechies.saralsmssync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.saraltechies.saralsmssync.R
import com.saraltechies.saralsmssync.core.SyncCrypto
import com.saraltechies.saralsmssync.data.AppPrefs

/** First run: start a new group on the first phone, or join one from a phone that's already set up. */
@Composable
fun SetupScreen(onPaired: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    fun join(text: String) {
        val key = SyncCrypto.parsePairingCode(text)
        if (key == null) {
            error = R.string.setup_bad_code
        } else {
            AppPrefs.get(context).joinGroup(key)
            onPaired()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.setup_first_phone), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    AppPrefs.get(context).joinGroup(SyncCrypto.newGroupKey())
                    onPaired()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.setup_create)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.setup_other_phone), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.setup_other_phone_help), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    error = null
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    GmsBarcodeScanning.getClient(context, options).startScan()
                        .addOnSuccessListener { barcode -> join(barcode.rawValue.orEmpty()) }
                        .addOnFailureListener { error = R.string.setup_scan_failed }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.setup_scan)) }

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    error = null
                },
                label = { Text(stringResource(R.string.setup_paste_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { join(code) },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.setup_join)) }

            error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
