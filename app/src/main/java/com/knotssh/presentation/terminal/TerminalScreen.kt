package com.knotssh.presentation.terminal

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.ssh.TerminalStatus
import com.knotssh.ssh.SecretPrompt
import com.knotssh.R
import com.knotssh.presentation.common.RequestNotificationPermissionOnce
import com.knotssh.presentation.theme.TerminalBackground
import com.knotssh.presentation.theme.TerminalText
import com.knotssh.terminal.TerminalEmulator
import com.knotssh.terminal.TerminalPalette
import com.knotssh.terminal.TerminalSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val settings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    val quickCommands by viewModel.quickCommands.collectAsStateWithLifecycle()
    val customKeys by viewModel.customKeys.collectAsStateWithLifecycle()
    val ctrlArmed by viewModel.ctrlArmed.collectAsStateWithLifecycle()
    val altArmed by viewModel.altArmed.collectAsStateWithLifecycle()
    val hostKeyPrompt by viewModel.hostKeyPrompt.collectAsStateWithLifecycle()
    val secretPrompt by viewModel.secretPrompt.collectAsStateWithLifecycle()
    val connection by viewModel.connectionSettings.collectAsStateWithLifecycle()
    val backgroundSessions by viewModel.backgroundSessionsEnabled.collectAsStateWithLifecycle()

    // Without this grant the session notification is created but never reaches the drawer.
    RequestNotificationPermissionOnce(enabled = backgroundSessions)

    // A remote logout leaves nothing to interact with; pause only long enough to read the notice.
    LaunchedEffect(status, connection.closeOnExit, connection.closeOnExitSeconds) {
        if (status is TerminalStatus.Ended && connection.closeOnExit) {
            delay(connection.closeOnExitSeconds * 1_000L)
            onBack()
        }
    }

    // Non-zero IME inset is the stable way to know the keyboard is up.
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val snackbarHost = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var backArmedAt by remember { mutableLongStateOf(0L) }

    val limit by viewModel.limitReached.collectAsStateWithLifecycle()

    // Snackbars are shown from coroutines, so their text is resolved up front.
    val backHintMessage = stringResource(R.string.terminal_back_hint)
    val copiedMessage = stringResource(R.string.terminal_output_copied)
    val emptyClipboardMessage = stringResource(R.string.terminal_clipboard_empty)

    // First press only arms the gesture, so a stray back never drops a live session.
    fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - backArmedAt < BACK_CONFIRM_WINDOW_MS) {
            showLeaveDialog = true
        } else {
            backArmedAt = now
            scope.launch {
                snackbarHost.showSnackbar(backHintMessage)
            }
        }
    }

    BackHandler { onBackPressed() }

    limit?.let { max ->
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.terminal_limit_title)) },
            text = { Text(pluralStringResource(R.plurals.session_limit_message, max, max)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_got_it)) }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.terminal_leave_title)) },
            text = {
                Text(
                    stringResource(
                        if (backgroundSessions) R.string.terminal_leave_message
                        else R.string.terminal_leave_message_no_background
                    )
                )
            },
            confirmButton = {
                if (backgroundSessions) {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        viewModel.detach()
                        onBack()
                    }) { Text(stringResource(R.string.terminal_keep_alive)) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.terminate()
                    onBack()
                }) {
                    Text(
                        text = stringResource(R.string.terminal_terminate),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    // An explicit tap on the buffer always opens the keyboard; the setting only governs the
    // automatic popup once the session connects.
    fun refocus() {
        if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // Throws if the sink has not been attached yet, e.g. during the first composition.
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    // Key bars must never summon the keyboard: they exist precisely so the buffer can be read
    // and navigated with the keyboard down.
    fun tapFeedback() {
        if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    hostKeyPrompt?.let { verdict ->
        HostKeyDialog(
            verdict = verdict,
            onAccept = { viewModel.resolveHostKeyPrompt(true) },
            onReject = { viewModel.resolveHostKeyPrompt(false) }
        )
    }

    secretPrompt?.let { prompt ->
        SecretPromptDialog(
            prompt = prompt,
            onSubmit = { viewModel.resolveSecretPrompt(it) },
            onDismiss = { viewModel.resolveSecretPrompt(null) }
        )
    }

    val context = LocalContext.current
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(status, settings.autoShowKeyboard) {
        if (status is TerminalStatus.Connected && settings.autoShowKeyboard) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.detach()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (backgroundSessions) {
                                stringResource(R.string.terminal_back_keep_alive)
                            } else {
                                stringResource(R.string.action_back)
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setText(AnnotatedString(viewModel.bufferAsText()))
                            snackbarHost.showSnackbar(copiedMessage)
                        }
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.terminal_copy_output)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val text = clipboard.getText()?.text
                            if (text.isNullOrEmpty()) {
                                snackbarHost.showSnackbar(emptyClipboardMessage)
                            } else {
                                viewModel.paste(text)
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.terminal_paste)
                        )
                    }
                    if (status !is TerminalStatus.Connected) {
                        IconButton(onClick = viewModel::reconnect) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.terminal_reconnect),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    StatusBadge(status)
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.terminal_more_actions)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_clear_scrollback)) },
                                onClick = {
                                    viewModel.clearScrollback()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_reset)) },
                                onClick = {
                                    viewModel.resetTerminal()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_end_session)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.terminate()
                                    onBack()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val palette = remember(settings.theme) { settings.theme.palette }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .background(Color(palette.background))
                // Background is applied before the insets so it fills the cutout band, while
                // the keys and the buffer stay clear of it. Bottom resolves to whichever is
                // larger between the IME and the navigation bar.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            TerminalViewport(
                settings = settings,
                palette = palette,
                snapshots = viewModel.snapshot,
                onMeasured = viewModel::onViewportMeasured,
                onTap = ::refocus,
                onScrollLine = viewModel::sendArrow,
                focusRequester = focusRequester,
                onInput = viewModel::sendText,
                modifier = Modifier.weight(1f)
            )

            if (settings.showQuickCommandsBar && quickCommands.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickCommands, key = { it.id }) { command ->
                        SuggestionChip(
                            onClick = {
                                viewModel.sendCommand(command.command)
                                tapFeedback()
                            },
                            label = { Text(command.label) }
                        )
                    }
                }
            }

            if (settings.showAccessoryBar) {
                AccessoryBar(
                    hapticFeedback = settings.hapticFeedback,
                    customKeys = customKeys,
                    ctrlArmed = ctrlArmed,
                    altArmed = altArmed,
                    onSequence = { viewModel.sendSequence(it); tapFeedback() },
                    onText = { viewModel.sendText(it); tapFeedback() },
                    onArrow = viewModel::sendArrow,
                    onHomeEnd = { viewModel.sendHomeEnd(it); tapFeedback() },
                    onToggleCtrl = { viewModel.toggleCtrl(); tapFeedback() },
                    onToggleAlt = { viewModel.toggleAlt(); tapFeedback() },
                    onToggleKeyboard = {
                        tapFeedback()
                        if (keyboardVisible) {
                            keyboardController?.hide()
                        } else {
                            runCatching { focusRequester.requestFocus() }
                            keyboardController?.show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TerminalViewport(
    settings: TerminalSettings,
    palette: TerminalPalette,
    snapshots: StateFlow<TerminalSnapshot>,
    onMeasured: (columns: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit,
    onTap: () -> Unit,
    onScrollLine: (Char) -> Unit,
    focusRequester: FocusRequester,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collected here rather than in the screen so a terminal frame does not recompose the
    // app bar and the key bars.
    val snapshot by snapshots.collectAsStateWithLifecycle()

    val foregroundColor = remember(palette) { Color(palette.foreground) }
    val backgroundColor = remember(palette) { Color(palette.background) }
    // Bright blue from the theme's own ANSI table keeps links legible on every background.
    val linkColor = remember(palette) { Color(palette.color(12)) }

    val fontFamily = remember(settings.font) { settings.font.toFontFamily() }
    val textStyle = remember(settings.fontSize, fontFamily) {
        TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * 1.3f).sp,
            fontFamily = fontFamily
        )
    }

    val measurer = rememberTextMeasurer()
    val cellSize = remember(textStyle) {
        val sample = measurer.measure(AnnotatedString("X".repeat(SAMPLE_WIDTH)), textStyle)
        (sample.size.width.toFloat() / SAMPLE_WIDTH) to sample.size.height.toFloat()
    }

    val cursorAlpha = rememberCursorAlpha(settings.cursorBlink)
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(widthPx, heightPx, cellSize, settings.autoSizePty) {
            val columns = floor(widthPx / cellSize.first).toInt()
                .coerceAtLeast(TerminalEmulator.MIN_COLUMNS)
            val rows = floor(heightPx / cellSize.second).toInt()
                .coerceAtLeast(TerminalEmulator.MIN_ROWS)
            onMeasured(columns, rows, widthPx.toInt(), heightPx.toInt())
        }

        // A full-screen app owns the whole grid, so there is nothing for the list to scroll;
        // the drag is translated into cursor keys instead, one per line of travel.
        val swipeSendsArrows = snapshot.altScreen && settings.swipeScrollsFullScreenApps

        LazyColumn(
            state = listState,
            // Reversed layout keeps the newest line pinned to the bottom without manual scrolling.
            reverseLayout = true,
            userScrollEnabled = !swipeSendsArrows,
            modifier = Modifier
                .fillMaxSize()
                // Tap gestures do not consume drags, so the buffer stays scrollable.
                .pointerInput(Unit) { detectTapGestures { onTap() } }
                .pointerInput(swipeSendsArrows, cellSize.second) {
                    if (!swipeSendsArrows) return@pointerInput
                    val step = cellSize.second.coerceAtLeast(1f)
                    var travel = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { travel = 0f },
                        onDragCancel = { travel = 0f }
                    ) { _, delta ->
                        travel += delta
                        while (travel <= -step) {
                            travel += step
                            onScrollLine('B')
                        }
                        while (travel >= step) {
                            travel -= step
                            onScrollLine('A')
                        }
                    }
                }
            // No content padding: the row count is derived from the viewport height, and any
            // padding would make the grid taller than what actually fits.
        ) {
            // No key: once scrollback reaches its cap every line shifts index on each new row,
            // and keying by index would invalidate the whole list every frame. Position from
            // the bottom is the stable identity for a terminal.
            items(count = snapshot.totalLines) { reversedIndex ->
                val lineIndex = snapshot.totalLines - 1 - reversedIndex
                val line = snapshot.lineAt(lineIndex)
                val cursorColumn = snapshot.cursorCol.takeIf {
                    snapshot.cursorVisible && lineIndex == snapshot.cursorRow && cursorAlpha > 0.5f
                }
                Text(
                    text = remember(line, cursorColumn, settings.ansiColorsEnabled, palette, settings.clickableUrls) {
                        line.toAnnotatedString(
                            colorsEnabled = settings.ansiColorsEnabled,
                            palette = palette,
                            defaultForeground = foregroundColor,
                            defaultBackground = backgroundColor,
                            cursorColumn = cursorColumn,
                            linkifyUrls = settings.clickableUrls,
                            linkColor = linkColor
                        )
                    },
                    style = textStyle,
                    color = foregroundColor,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }

        HiddenKeyboardSink(
            focusRequester = focusRequester,
            onInput = onInput,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(1.dp)
        )
    }
}

/**
 * The IME needs a real editable field to attach to, but a terminal has no input line. This
 * 1dp invisible field provides the editor connection; everything typed is diffed against the
 * previous buffer and forwarded to the shell as raw bytes.
 *
 * The buffer is left alone between keystrokes and only re-centred when it drifts near its
 * bounds: rewriting it on every change cancels the IME's key-repeat, which made a held
 * backspace delete a single character.
 *
 * It must not carry [androidx.compose.foundation.focusable]: that would add a second focus
 * target which swallows the focus request and leaves the keyboard without an editor.
 */
@Composable
private fun HiddenKeyboardSink(
    focusRequester: FocusRequester,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val centred = remember { TextFieldValue(PAD, TextRange(PAD.length)) }
    var value by remember { mutableStateOf(centred) }
    var previous by remember { mutableStateOf(PAD) }

    BasicTextField(
        value = value,
        onValueChange = { updated ->
            val typed = updated.text
            val shared = previous.commonPrefixWith(typed).length
            val removed = previous.length - shared

            if (removed > MAX_DELETE_BURST) {
                // Losing the whole buffer at once means the IME restarted (it does this when
                // the keyboard is hidden and shown again), not that the user deleted 200
                // characters. Re-centre silently instead of firing backspaces at the shell.
                previous = PAD
                value = centred
            } else {
                repeat(removed) { onInput(DEL) }
                typed.substring(shared).takeIf { it.isNotEmpty() }?.let {
                    // Terminals expect CR from the Enter key, not LF.
                    onInput(it.replace('\n', '\r'))
                }
                if (typed.length < PAD_MIN || typed.length > PAD_MAX) {
                    previous = PAD
                    value = centred
                } else {
                    previous = typed
                    value = updated
                }
            }
        },
        modifier = modifier.focusRequester(focusRequester),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            // Password disables suggestions and composing regions, which would otherwise
            // rewrite already-sent characters.
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.None
        ),
        cursorBrush = SolidColor(Color.Transparent)
    )
}

@Composable
private fun rememberCursorAlpha(enabled: Boolean): Float {
    if (!enabled) return 1f
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(BLINK_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    return alpha
}

@Composable
private fun StatusBadge(status: TerminalStatus) {
    val (label, color) = when (status) {
        TerminalStatus.Connected ->
            stringResource(R.string.status_online) to Color(0xFF34A853)
        TerminalStatus.Connecting ->
            stringResource(R.string.status_connecting) to Color(0xFFFBBC04)
        is TerminalStatus.Reconnecting ->
            stringResource(R.string.status_retry, status.attempt, status.of) to Color(0xFFFBBC04)
        is TerminalStatus.Ended ->
            stringResource(R.string.status_ended) to Color(0xFF5F6368)
        is TerminalStatus.Disconnected ->
            stringResource(R.string.status_disconnected) to Color(0xFFEA4335)
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AccessoryBar(
    hapticFeedback: Boolean,
    customKeys: List<CustomKey>,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onSequence: (String) -> Unit,
    onText: (String) -> Unit,
    onArrow: (Char) -> Unit,
    onHomeEnd: (end: Boolean) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleKeyboard: () -> Unit
) {
    var functionRow by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (customKeys.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(customKeys, key = { it.id }) { key ->
                        KeyCap(
                            label = key.label,
                            onClick = { onSequence(key.sequence) },
                            modifier = Modifier.widthIn(min = 40.dp)
                        )
                    }
                }
            }

            if (functionRow) {
                FunctionKeyRows(
                    onSequence = onSequence,
                    onExit = { functionRow = false }
                )
            } else {
                // Both rows carry the same number of equal-weight slots, so the columns line
                // up: ↑ sits directly above ↓ with ← and → flanking it, and Home/End, PgUp/PgDn
                // and the two toggles pair vertically.
                KeyRow {
                    KeyCap("ESC", Modifier.weight(1f)) { onSequence("\u001B") }
                    KeyCap("/", Modifier.weight(1f)) { onText("/") }
                    KeyCap("|", Modifier.weight(1f)) { onText("|") }
                    KeyCap("-", Modifier.weight(1f)) { onText("-") }
                    KeyCap("HOME", Modifier.weight(1f)) { onHomeEnd(false) }
                    RepeatingKeyCap(Modifier.weight(1f), hapticFeedback, 'A') { onArrow('A') }
                    KeyCap("END", Modifier.weight(1f)) { onHomeEnd(true) }
                    KeyCap("PG↑", Modifier.weight(1f)) { onSequence("\u001B[5~") }
                    KeyCap("FN", Modifier.weight(1f)) { functionRow = true }
                }
                KeyRow {
                    KeyCap("TAB", Modifier.weight(1f)) { onSequence("\t") }
                    KeyCap("CTRL", Modifier.weight(1f), active = ctrlArmed, onClick = onToggleCtrl)
                    KeyCap("ALT", Modifier.weight(1f), active = altArmed, onClick = onToggleAlt)
                    KeyCap("~", Modifier.weight(1f)) { onText("~") }
                    RepeatingKeyCap(Modifier.weight(1f), hapticFeedback, 'D') { onArrow('D') }
                    RepeatingKeyCap(Modifier.weight(1f), hapticFeedback, 'B') { onArrow('B') }
                    RepeatingKeyCap(Modifier.weight(1f), hapticFeedback, 'C') { onArrow('C') }
                    KeyCap("PG↓", Modifier.weight(1f)) { onSequence("\u001B[6~") }
                    IconKeyCap(
                        icon = Icons.Default.Keyboard,
                        description = stringResource(R.string.terminal_toggle_keyboard),
                        modifier = Modifier.weight(1f),
                        onClick = onToggleKeyboard
                    )
                }
            }
        }
    }
}

@Composable
private fun FunctionKeyRows(
    onSequence: (String) -> Unit,
    onExit: () -> Unit
) {
    // Seven slots per row so F1-F6 sit exactly above F7-F12.
    KeyRow {
        FUNCTION_KEYS.take(6).forEach { (label, sequence) ->
            KeyCap(label, Modifier.weight(1f)) { onSequence(sequence) }
        }
        Spacer(Modifier.weight(1f))
    }
    KeyRow {
        FUNCTION_KEYS.drop(6).forEach { (label, sequence) ->
            KeyCap(label, Modifier.weight(1f)) { onSequence(sequence) }
        }
        KeyCap("FN", Modifier.weight(1f), active = true, onClick = onExit)
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.height(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IconKeyCap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.height(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Arrow cap that fires once on press then auto-repeats while held, like a physical key. */
@Composable
private fun RepeatingKeyCap(
    modifier: Modifier,
    hapticFeedback: Boolean,
    direction: Char,
    onTrigger: () -> Unit
) {
    val currentTrigger by rememberUpdatedState(onTrigger)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val label = stringResource(ARROW_LABELS.getValue(direction))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
            .height(34.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (hapticFeedback) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    val repeater = scope.launch {
                        currentTrigger()
                        delay(REPEAT_INITIAL_DELAY_MS)
                        while (isActive) {
                            currentTrigger()
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                    waitForUpOrCancellation()
                    repeater.cancel()
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ARROW_ICONS.getValue(direction),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KeyChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.height(32.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HostKeyDialog(
    verdict: HostKeyVerdict,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val changed = verdict is HostKeyVerdict.Changed
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = if (changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(
                    if (changed) R.string.host_key_changed_title
                    else R.string.host_key_unknown_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (verdict) {
                    is HostKeyVerdict.Unknown -> {
                        Text(
                            stringResource(
                                R.string.host_key_unknown_message,
                                verdict.candidate.host,
                                verdict.candidate.port
                            )
                        )
                        FingerprintRow(
                            stringResource(R.string.host_key_type),
                            verdict.candidate.keyType
                        )
                        FingerprintRow(
                            stringResource(R.string.host_key_fingerprint),
                            verdict.candidate.fingerprintSha256
                        )
                    }
                    is HostKeyVerdict.Changed -> {
                        Text(
                            stringResource(
                                R.string.host_key_changed_message,
                                verdict.candidate.host,
                                verdict.candidate.port
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        FingerprintRow(
                            stringResource(R.string.host_key_expected),
                            verdict.stored.fingerprintSha256
                        )
                        FingerprintRow(
                            stringResource(R.string.host_key_received),
                            verdict.candidate.fingerprintSha256
                        )
                    }
                    HostKeyVerdict.Trusted -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(
                    stringResource(
                        if (changed) R.string.host_key_accept_anyway
                        else R.string.host_key_accept_and_store
                    ),
                    color = if (changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FingerprintRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
    }
}

@Composable
private fun SecretPromptDialog(
    prompt: SecretPrompt,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var secret by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (prompt.passphrase) R.string.secret_prompt_passphrase_title
                    else R.string.secret_prompt_password_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.secret_prompt_message, prompt.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onSubmit(secret) }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (visible) R.string.action_hide else R.string.action_show
                                )
                            )
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = secret.isNotEmpty(), onClick = { onSubmit(secret) }) {
                Text(stringResource(R.string.action_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Headroom on both sides of the hidden buffer so key-repeat can run without a re-centre. */
private val PAD = " ".repeat(256)
private const val PAD_MIN = 64
private const val PAD_MAX = 2048
/** More deletions than a person can produce in one event: treated as an IME reset. */
private const val MAX_DELETE_BURST = 16
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L
private const val BACK_CONFIRM_WINDOW_MS = 3_000L

private val ARROW_ICONS = mapOf(
    'A' to Icons.Default.ArrowUpward,
    'B' to Icons.Default.ArrowDownward,
    'D' to Icons.AutoMirrored.Filled.ArrowBack,
    'C' to Icons.AutoMirrored.Filled.ArrowForward
)

private val ARROW_LABELS = mapOf(
    'A' to R.string.arrow_up,
    'B' to R.string.arrow_down,
    'D' to R.string.arrow_left,
    'C' to R.string.arrow_right
)

/** F1-F4 use SS3, F5 upward use CSI with the xterm numbering. */
private val FUNCTION_KEYS = listOf(
    "F1" to "\u001BOP",
    "F2" to "\u001BOQ",
    "F3" to "\u001BOR",
    "F4" to "\u001BOS",
    "F5" to "\u001B[15~",
    "F6" to "\u001B[17~",
    "F7" to "\u001B[18~",
    "F8" to "\u001B[19~",
    "F9" to "\u001B[20~",
    "F10" to "\u001B[21~",
    "F11" to "\u001B[23~",
    "F12" to "\u001B[24~"
)
private const val DEL = "\u007F"
private const val SAMPLE_WIDTH = 64
private const val BLINK_MILLIS = 530
