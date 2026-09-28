package com.mhss.app.presentation.sync

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.util.Constants
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSyncScreen(
    viewModel: CloudSyncViewModel = koinViewModel()
) {
    val login by viewModel.login.collectAsStateWithLifecycle("")
    val savedOwner by viewModel.owner.collectAsStateWithLifecycle("")
    val savedRepo by viewModel.repo.collectAsStateWithLifecycle("")
    val autoSync by viewModel.autoSync.collectAsStateWithLifecycle(false)
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle("")
    val lastError by viewModel.lastError.collectAsStateWithLifecycle("")
    val externalNotes by viewModel.externalNotes.collectAsStateWithLifecycle(false)
    val status by viewModel.status.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var token by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") }
    var repo by rememberSaveable { mutableStateOf("PenguinBrainData") }
    LaunchedEffect(savedOwner, savedRepo) {
        if (savedOwner.isNotBlank()) owner = savedOwner
        if (savedRepo.isNotBlank()) repo = savedRepo
    }
    val loading = status == CloudSyncStatus.Loading
    val signedIn = login.isNotBlank()

    Scaffold(
        topBar = { MyBrainAppBar(stringResource(R.string.cloud_sync)) }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.penguin_app_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.cloud_sync),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.cloud_sync_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!signedIn) {
                SyncCard {
                    Text(stringResource(R.string.cloud_sync_intro), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.cloud_sync_account_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.cloud_sync_step_repo), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.cloud_sync_step_token), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.cloud_sync_step_sign_in), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { uriHandler.openUri(Constants.CLOUD_SYNC_CREATE_REPO_LINK) }) {
                            Text(stringResource(R.string.cloud_sync_create_repo))
                        }
                        OutlinedButton(onClick = { uriHandler.openUri(Constants.CLOUD_SYNC_CREATE_TOKEN_LINK) }) {
                            Text(stringResource(R.string.cloud_sync_create_token))
                        }
                    }
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.cloud_sync_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text(stringResource(R.string.cloud_sync_owner)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text(stringResource(R.string.cloud_sync_repo)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.signIn(token, owner, repo) },
                    enabled = !loading && token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.cloud_sync_sign_in))
                }
            } else {
                SyncCard {
                    Text(
                        stringResource(R.string.cloud_sync_signed_in_as, login),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        stringResource(R.string.cloud_sync_repository, "$savedOwner/$savedRepo"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val lastText = lastSync.toLongOrNull()?.let {
                        stringResource(
                            R.string.cloud_sync_last,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                        )
                    } ?: stringResource(R.string.cloud_sync_never)
                    Text(lastText, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { viewModel.syncNow() },
                    enabled = !loading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.cloud_sync_now))
                }
                SyncCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.cloud_sync_auto),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = autoSync, onCheckedChange = { viewModel.setAutoSync(it) })
                    }
                }
                if (externalNotes) {
                    Text(
                        stringResource(R.string.cloud_sync_external_notes_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { uriHandler.openUri(Constants.PENGUIN_BRAIN_WEBSITE_LINK) }) {
                        Text(stringResource(R.string.cloud_sync_open_website))
                    }
                    TextButton(onClick = { viewModel.signOut() }, enabled = !loading) {
                        Text(stringResource(R.string.cloud_sync_sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            when (val s = status) {
                is CloudSyncStatus.Synced -> Text(
                    stringResource(R.string.cloud_sync_success, s.summary.pulledChanges, s.summary.pushedChanges),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                is CloudSyncStatus.Error -> Text(
                    stringResource(R.string.cloud_sync_failed, s.message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> if (status == CloudSyncStatus.Idle && lastError.isNotBlank() && signedIn) {
                    Text(
                        stringResource(R.string.cloud_sync_failed, lastError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SyncCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}
