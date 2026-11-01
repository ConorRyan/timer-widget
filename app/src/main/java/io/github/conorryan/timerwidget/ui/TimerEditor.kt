package io.github.conorryan.timerwidget.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.conorryan.timerwidget.R
import io.github.conorryan.timerwidget.data.TimerDef
import io.github.conorryan.timerwidget.util.AlarmSound
import io.github.conorryan.timerwidget.util.AlarmSounds
import io.github.conorryan.timerwidget.util.SoundPreviewPlayer
import io.github.conorryan.timerwidget.util.firstGrapheme
import java.util.UUID

/** One-tap durations under the wheels, in minutes. */
val PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 25)

/** The preset [hours]/[minutes]/[seconds] matches exactly, or null. */
fun matchingPreset(hours: Int, minutes: Int, seconds: Int): Int? =
    PRESET_MINUTES.firstOrNull { hours == 0 && seconds == 0 && minutes == it }

private const val EMOJI_COLUMNS = 7

/** Emoji shown before "Show all": two rows, the last cell being free entry. */
private const val QUICK_EMOJI = EMOJI_COLUMNS * 2 - 1

/**
 * Full-screen add/edit form: name, scroll-or-type time wheels with presets,
 * emoji, and alarm sound. [onDelete] is null when creating a timer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerEditor(
    initial: TimerDef?,
    sounds: List<AlarmSound>,
    onClose: () -> Unit,
    onSave: (TimerDef) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji.orEmpty()) }
    var customEmoji by remember {
        mutableStateOf(
            initial?.emoji?.takeIf { it.isNotBlank() && it !in TIMER_EMOJIS }.orEmpty(),
        )
    }
    var hours by remember { mutableStateOf(initial?.hours ?: 0) }
    var minutes by remember { mutableStateOf(initial?.minutes ?: 5) }
    var seconds by remember { mutableStateOf(initial?.seconds ?: 0) }
    var soundUri by remember { mutableStateOf(initial?.soundUri) }
    var soundName by remember { mutableStateOf(initial?.soundName) }
    var pickingSound by remember { mutableStateOf(false) }
    // Start expanded when the timer's emoji is one the short grid hides.
    var showAllEmoji by remember {
        mutableStateOf(emoji in TIMER_EMOJIS && emoji !in TIMER_EMOJIS.take(QUICK_EMOJI))
    }
    val titleRes = if (initial == null) R.string.new_timer else R.string.edit_timer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Ground)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Brand.Text)
            }
            Text(
                stringResource(titleRes),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Brand.Text,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Button(
                enabled = emoji.isNotBlank(),
                onClick = {
                    onSave(
                        TimerDef(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            hours = hours,
                            minutes = minutes,
                            seconds = seconds,
                            emoji = emoji,
                            soundUri = soundUri,
                            soundName = soundName,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (emoji.isBlank()) Brand.Raised else Brand.Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 30.sp)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(stringResource(R.string.timer_name))
                    NameField(value = name, onValueChange = { name = it.take(40) })
                }
            }

            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeWheel(
                    value = hours,
                    range = 0..99,
                    label = stringResource(R.string.wheel_hours),
                    unit = stringResource(R.string.hours),
                    pad = false,
                    onValueChange = { hours = it },
                    modifier = Modifier.weight(1f),
                )
                TimeWheel(
                    value = minutes,
                    range = 0..59,
                    label = stringResource(R.string.wheel_minutes),
                    unit = stringResource(R.string.minutes),
                    pad = true,
                    onValueChange = { minutes = it },
                    modifier = Modifier.weight(1f),
                )
                TimeWheel(
                    value = seconds,
                    range = 0..59,
                    label = stringResource(R.string.wheel_seconds),
                    unit = stringResource(R.string.seconds),
                    pad = true,
                    onValueChange = { seconds = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.wheel_hint),
                fontSize = 12.sp,
                color = Brand.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            val preset = matchingPreset(hours, minutes, seconds)
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PRESET_MINUTES.forEach { n ->
                    PresetChip(
                        label = stringResource(R.string.preset_minutes, n),
                        selected = preset == n,
                        onClick = {
                            hours = 0
                            minutes = n
                            seconds = 0
                        },
                    )
                }
            }

            EmojiSection(
                emoji = emoji,
                customEmoji = customEmoji,
                showAll = showAllEmoji,
                onEmoji = { emoji = it },
                onCustomEmoji = { input ->
                    customEmoji = firstGrapheme(input)
                    emoji = customEmoji
                },
                onToggleShowAll = { showAllEmoji = !showAllEmoji },
            )

            SoundRow(
                title = soundName ?: stringResource(R.string.timer_sound_default),
                onClick = { pickingSound = true },
                modifier = Modifier.padding(top = 4.dp),
            )

            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Brand.Danger),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.delete_timer), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (pickingSound) {
        SoundPickerSheet(
            sounds = sounds,
            selectedUri = soundUri,
            onSelect = { sound ->
                soundUri = sound?.uri
                soundName = sound?.title
                pickingSound = false
            },
            onDismiss = { pickingSound = false },
        )
    }
}

/**
 * The emoji picker, split out of [TimerEditor]'s body so it can be skipped
 * while the time wheels report every scrolled value change.
 */
@Composable
private fun EmojiSection(
    emoji: String,
    customEmoji: String,
    showAll: Boolean,
    onEmoji: (String) -> Unit,
    onCustomEmoji: (String) -> Unit,
    onToggleShowAll: () -> Unit,
) {
    FieldLabel(stringResource(R.string.timer_emoji), Modifier.padding(top = 24.dp, bottom = 10.dp))
    val shown = if (showAll) TIMER_EMOJIS else TIMER_EMOJIS.take(QUICK_EMOJI)
    val cells: List<String?> = shown + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(EMOJI_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { candidate ->
                    val cell = Modifier.weight(1f).aspectRatio(1f)
                    if (candidate == null) {
                        EmojiEntry(
                            value = customEmoji,
                            selected = customEmoji.isNotBlank() && emoji == customEmoji,
                            onValueChange = onCustomEmoji,
                            modifier = cell,
                        )
                    } else {
                        EmojiOption(
                            emoji = candidate,
                            selected = emoji == candidate,
                            onClick = { onEmoji(candidate) },
                            modifier = cell,
                        )
                    }
                }
                repeat(EMOJI_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    TextButton(onClick = onToggleShowAll) {
        Text(
            stringResource(if (showAll) R.string.emoji_show_fewer else R.string.emoji_show_all),
            color = Brand.Amber,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = Brand.TextMuted,
        modifier = modifier,
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Brand.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Brand.Amber),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(bottom = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.timer_fallback),
                            fontSize = 22.sp,
                            color = Brand.WheelFar,
                        )
                    }
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Brand.LineStrong))
            }
        },
    )
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) Brand.Amber else Brand.Ground)
            .border(1.dp, if (selected) Brand.Amber else Brand.LineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Brand.OnAmber else Brand.Text,
        )
    }
}

@Composable
private fun EmojiOption(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Brand.Amber else Brand.Surface)
            .border(1.dp, if (selected) Brand.Amber else Brand.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

@Composable
private fun EmojiEntry(
    value: String,
    selected: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val description = stringResource(R.string.emoji_custom)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Brand.Amber else Brand.Ground)
            .border(1.dp, if (selected || focused) Brand.Amber else Brand.LineStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = description },
            textStyle = TextStyle(
                color = Brand.Text,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            cursorBrush = SolidColor(if (focused) Brand.Amber else Brand.Ground),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isBlank() && !focused) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = Brand.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SoundRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand.Surface)
            .border(1.dp, Brand.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.Raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Brand.Amber, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.timer_sound), fontSize = 12.sp, color = Brand.TextMuted)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Brand.Text)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Brand.TextMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPickerSheet(
    sounds: List<AlarmSound>,
    selectedUri: String?,
    onSelect: (AlarmSound?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val previewPlayer = remember { SoundPreviewPlayer() }
    // Candidate "Use sound" applies; null means "Default". Seeded from the
    // timer's current sound so opening the sheet and confirming changes
    // nothing.
    var candidateUri by remember { mutableStateOf(selectedUri) }
    var previewing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { previewPlayer.stop() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Surface,
        contentColor = Brand.Text,
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.timer_sound), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.sound_hint),
                fontSize = 14.sp,
                color = Brand.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SoundOption(
                    title = stringResource(R.string.timer_sound_default),
                    selected = candidateUri == null,
                    playing = previewing && candidateUri == null,
                    onClick = {
                        candidateUri = null
                        previewing = true
                        previewPlayer.preview(context, AlarmSounds.defaultUri(context))
                    },
                )
            }
            items(sounds, key = { it.uri }) { sound ->
                SoundOption(
                    title = sound.title,
                    selected = candidateUri == sound.uri,
                    playing = previewing && candidateUri == sound.uri,
                    onClick = {
                        candidateUri = sound.uri
                        previewing = true
                        previewPlayer.preview(context, Uri.parse(sound.uri))
                    },
                )
            }
        }
        HorizontalDivider(color = Brand.Line)
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.dp, Brand.LineStrong),
            ) {
                Text(stringResource(R.string.cancel), color = Brand.Text, fontSize = 16.sp)
            }
            Button(
                onClick = { onSelect(sounds.firstOrNull { it.uri == candidateUri }) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(stringResource(R.string.use_sound), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SoundOption(title: String, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand.Amber else Brand.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = Brand.OnAmber,
                unselectedColor = Brand.WheelFar,
            ),
        )
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Brand.OnAmber else Brand.Text,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        if (playing) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = stringResource(R.string.sound_playing),
                tint = Brand.OnAmber,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
    }
}

val TIMER_EMOJIS = listOf(
    "⏰", "⏳", "⏱️", "🔔", "🍵", "☕",
    "🍜", "🍳", "🥚", "🍕", "🥗", "🍰",
    "🦷", "🛁", "🚿", "🧘", "🏃", "🏋️",
    "📚", "🎧", "🎮", "🧹", "🧺", "🌱",
    "💊", "🛒", "🚗", "✈️", "📞",
)
