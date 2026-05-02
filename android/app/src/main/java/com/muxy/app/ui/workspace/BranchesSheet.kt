package com.muxy.app.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muxy.app.model.VCSBranchesDTO
import com.muxy.app.ui.connect.ConnectionViewModel
import com.muxy.app.ui.theme.MuxyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesSheet(viewModel: ConnectionViewModel, projectID: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme by viewModel.session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)
    val scope = rememberCoroutineScope()

    var branches by remember { mutableStateOf<VCSBranchesDTO?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun reload() {
        loading = true
        error = null
        scope.launch {
            try {
                branches = viewModel.session.listBranches(projectID)
            } catch (t: Throwable) {
                error = t.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(projectID) { reload() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Branches", style = MaterialTheme.typography.titleMedium, color = palette.foreground)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New branch", tint = palette.foreground)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            when {
                loading -> CircularProgressIndicator(color = palette.foreground)
                branches == null -> Text(error ?: "No branches", color = palette.foreground.copy(alpha = 0.7f))
                else -> {
                    val b = branches!!
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(b.locals, key = { it }) { branch ->
                            BranchRow(branch, current = branch == b.current, busy = busy == branch, foreground = palette.foreground) {
                                if (branch == b.current) return@BranchRow
                                busy = branch
                                scope.launch {
                                    try {
                                        viewModel.session.switchBranch(projectID, branch)
                                        onDismiss()
                                    } catch (t: Throwable) {
                                        error = t.message
                                    } finally {
                                        busy = null
                                    }
                                }
                            }
                        }
                        error?.let {
                            item {
                                Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; newName = "" },
            title = { Text("New Branch") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("branch-name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Creates and switches to a new branch from HEAD.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val name = newName.trim()
                        showCreate = false
                        newName = ""
                        scope.launch {
                            try {
                                viewModel.session.createBranch(projectID, name)
                                onDismiss()
                            } catch (t: Throwable) {
                                error = t.message
                            }
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; newName = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BranchRow(branch: String, current: Boolean, busy: Boolean, foreground: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !current && !busy, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (current) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (current) Color(0xFF30A46C) else foreground.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(branch, color = foreground, modifier = Modifier.weight(1f))
        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = foreground)
    }
}
