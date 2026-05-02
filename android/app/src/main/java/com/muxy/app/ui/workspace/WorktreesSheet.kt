package com.muxy.app.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.muxy.app.model.WorktreeDTO
import com.muxy.app.ui.connect.ConnectionViewModel
import com.muxy.app.ui.theme.MuxyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorktreesSheet(viewModel: ConnectionViewModel, projectID: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme by viewModel.session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)
    val worktreesMap by viewModel.session.projectWorktrees.collectAsState()
    val workspace by viewModel.session.workspace.collectAsState()
    val scope = rememberCoroutineScope()

    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(projectID) { viewModel.session.refreshWorktrees(projectID) }

    val worktrees = worktreesMap[projectID].orEmpty()
    val activeID = workspace?.worktreeID

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Worktrees", style = MaterialTheme.typography.titleMedium, color = palette.foreground)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add worktree", tint = palette.foreground)
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(worktrees, key = { it.id }) { worktree ->
                WorktreeRow(
                    worktree = worktree,
                    isActive = worktree.id == activeID,
                    busy = busy == worktree.id,
                    foreground = palette.foreground,
                    onClick = {
                        if (worktree.id == activeID) return@WorktreeRow
                        busy = worktree.id
                        scope.launch {
                            try {
                                viewModel.session.selectWorktree(projectID, worktree.id)
                                onDismiss()
                            } catch (t: Throwable) {
                                error = t.message
                            } finally {
                                busy = null
                            }
                        }
                    },
                    onRemove = {
                        busy = worktree.id
                        scope.launch {
                            try {
                                viewModel.session.removeWorktree(projectID, worktree.id)
                            } catch (t: Throwable) {
                                error = t.message
                            } finally {
                                busy = null
                            }
                        }
                    },
                )
            }
            error?.let {
                item { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }

    if (showAdd) {
        AddWorktreeDialog(
            viewModel = viewModel,
            projectID = projectID,
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun WorktreeRow(
    worktree: WorktreeDTO,
    isActive: Boolean,
    busy: Boolean,
    foreground: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive && !busy, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                isActive -> Icons.Filled.CheckCircle
                worktree.isPrimary -> Icons.Filled.Home
                else -> Icons.Filled.Folder
            },
            contentDescription = null,
            tint = if (isActive) Color(0xFF30A46C) else foreground.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(worktree.name, color = foreground)
            worktree.branch?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = 0.6f)) }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = foreground)
        } else if ((worktree.canBeRemoved ?: !worktree.isPrimary) && !isActive) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun AddWorktreeDialog(viewModel: ConnectionViewModel, projectID: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var useExisting by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedExisting by remember { mutableStateOf("") }
    var inProgress by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectID) {
        runCatching { viewModel.session.listBranches(projectID) }
            .onSuccess { branches ->
                existing = branches.locals
                if (selectedExisting.isEmpty()) selectedExisting = branches.locals.firstOrNull().orEmpty()
            }
            .onFailure { error = it.message }
    }

    val canSubmit = name.isNotBlank() && (
        if (useExisting) selectedExisting.isNotEmpty() else branchName.isNotBlank()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Worktree") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Worktree name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = !useExisting, onClick = { useExisting = false }, label = { Text("New branch") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = useExisting, onClick = { useExisting = true }, label = { Text("Existing") })
                }
                Spacer(Modifier.height(8.dp))
                if (useExisting) {
                    Text("Existing branches:", style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.padding(top = 4.dp)) {
                        Column {
                            existing.forEach { b ->
                                FilterChip(selected = b == selectedExisting, onClick = { selectedExisting = b }, label = { Text(b) })
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("New branch name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit && !inProgress,
                onClick = {
                    val branch = if (useExisting) selectedExisting else branchName.trim()
                    inProgress = true
                    scope.launch {
                        try {
                            viewModel.session.addWorktree(projectID, name.trim(), branch, !useExisting)
                            onDismiss()
                        } catch (t: Throwable) {
                            error = t.message
                        } finally {
                            inProgress = false
                        }
                    }
                },
            ) { Text(if (inProgress) "Adding…" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
