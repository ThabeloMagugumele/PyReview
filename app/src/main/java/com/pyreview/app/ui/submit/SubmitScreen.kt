package com.pyreview.app.ui.submit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pyreview.app.R
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OfflineBanner
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.theme.CodeTextStyle
import com.pyreview.app.util.pluralize
import kotlinx.coroutines.launch

@Composable
fun SubmitScreen(
    viewModel: SubmitViewModel,
    onPhotosRead: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val draft = viewModel.draft
    val online by viewModel.isOnline.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }

    lateinit var takePhoto: () -> Unit
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (saved && uri != null) {
            viewModel.addPhotos(listOf(uri))
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = "Photo ${draft.photos.size} added",
                    actionLabel = "Take next",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) takePhoto()
            }
        }
    }
    takePhoto = {
        if (draft.photos.size >= SubmitViewModel.MAX_PHOTOS) {
            viewModel.message = "You can add up to ${SubmitViewModel.MAX_PHOTOS} photos per file."
        } else {
            val uri = viewModel.newPhotoUri(context)
            pendingPhoto = uri
            cameraLauncher.launch(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SubmitViewModel.MAX_PHOTOS),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPhotos(uris) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadPythonFile(context, uri)
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.message = null
        }
    }

    val hasPhotos = draft.photos.isNotEmpty()
    val reading = viewModel.readingProgress != null

    ScreenScaffold(
        snackbarHostState = snackbar,
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (hasPhotos) {
                    PrimaryButton(
                        text = "Read ${pluralize(draft.photos.size, "photo")}",
                        busy = reading,
                    ) { viewModel.readPhotos(onPhotosRead) }
                } else {
                    PrimaryButton(
                        text = "Submit for review",
                        enabled = online && draft.code.isNotBlank(),
                        onClick = onSubmit,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("STEP 1 OF 3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("New submission", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (!online) {
                OfflineBanner("No internet right now. You need to be online to review code, but your old reviews are still in History.")
                Spacer(Modifier.height(12.dp))
            }

            CodeEditor(
                fileName = draft.fileName,
                onFileNameChange = { draft.fileName = it },
                code = draft.code,
                onCodeChange = viewModel::onCodeChanged,
                enabled = !hasPhotos,
                onPaste = { viewModel.pasteFromClipboard(context) },
                onClear = {
                    val old = viewModel.clearCode()
                    scope.launch {
                        val result = snackbar.showSnackbar("Code cleared", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreCode(old)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceTile(R.drawable.ic_upload, "Upload .py", "from Files", Modifier.weight(1f)) {
                    fileLauncher.launch(arrayOf("text/*", "application/octet-stream", "application/x-python-code", "*/*"))
                }
                SourceTile(R.drawable.ic_camera, "Camera", "photo of code", Modifier.weight(1f)) { takePhoto() }
                SourceTile(R.drawable.ic_gallery, "Gallery", "pick photos", Modifier.weight(1f)) {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (hasPhotos) {
                PhotoStrip(
                    photos = draft.photos,
                    onMoveLeft = { viewModel.movePhoto(it, it - 1) },
                    onMoveRight = { viewModel.movePhoto(it, it + 1) },
                    onRemove = viewModel::removePhoto,
                    enabled = !reading,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (reading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(viewModel.readingProgress.orEmpty(), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }

            NoticeCard(
                if (hasPhotos) {
                    "Photos are read in this order. It's fine if they overlap because repeated lines get " +
                        "removed automatically. You'll check everything on the next screen."
                } else {
                    "Photos get turned into text before the review. For a long file, take a few photos. " +
                        "You'll check the result on the next screen."
                },
            )
            Spacer(Modifier.height(8.dp))
            NoticeCard(
                "Tip: get the code to fill the screen, avoid glare, hold your phone straight, and let each " +
                    "photo overlap the last one by a line or two.",
                accent = com.pyreview.app.ui.theme.LocalStatusColors.current.good,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

}

@Composable
private fun CodeEditor(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedPanel {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = fileName,
                    onValueChange = { onFileNameChange(it.replace("\n", "").take(60)) },
                    singleLine = true,
                    textStyle = CodeTextStyle.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                val lineCount = if (code.isEmpty()) 0 else code.count { it == '\n' } + 1
                Text(
                    "${pluralize(lineCount, "line")} · Python 3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                enabled = enabled,
                textStyle = CodeTextStyle,
                placeholder = {
                    Text(
                        if (enabled) "Type or paste your Python code here" else "Using photos instead. Remove them to type code.",
                        style = CodeTextStyle,
                    )
                },
                minLines = if (enabled) 8 else 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Default,
                ),
                shape = RoundedCornerShape(0.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onPaste, enabled = enabled) { Text("Paste") }
                TextButton(onClick = onClear, enabled = enabled && code.isNotEmpty()) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun SourceTile(icon: Int, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhotoStrip(
    photos: List<Uri>,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    enabled: Boolean,
) {
    OutlinedPanel {
        Column(Modifier.padding(12.dp)) {
            Text("Photos, in reading order", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tap or hold a photo to see it bigger. Use the arrows to change the order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            var previewIndex by remember { mutableStateOf<Int?>(null) }
            previewIndex?.let { start -> PhotoPreviewDialog(photos, start) { previewIndex = null } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photos.forEachIndexed { index, uri ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Photo ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 96.dp, height = 128.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .combinedClickable(
                                        onClickLabel = "View photo ${index + 1}",
                                        onClick = { previewIndex = index },
                                        onLongClick = { previewIndex = index },
                                    ),
                            )
                            Box(
                                Modifier
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            IconButton(
                                onClick = { onRemove(index) },
                                enabled = enabled,
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo ${index + 1}",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                                        .padding(2.dp)
                                        .size(16.dp),
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onMoveLeft(index) }, enabled = enabled && index > 0) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move photo ${index + 1} earlier")
                            }
                            IconButton(onClick = { onMoveRight(index) }, enabled = enabled && index < photos.lastIndex) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move photo ${index + 1} later")
                            }
                        }
                    }
                }
            }
            if (photos.size > 1) {
                var confirmRemoveAll by remember { mutableStateOf(false) }
                TextButton(onClick = { confirmRemoveAll = true }, enabled = enabled) {
                    Text("Remove all photos")
                }
                if (confirmRemoveAll) {
                    AlertDialog(
                        onDismissRequest = { confirmRemoveAll = false },
                        title = { Text("Remove all ${photos.size} photos?") },
                        text = { Text("You'll need to take or pick them again.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmRemoveAll = false
                                (photos.indices.reversed()).forEach(onRemove)
                            }) { Text("Remove all", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { confirmRemoveAll = false }) { Text("Keep them") } },
                    )
                }
            }
        }
    }
}
