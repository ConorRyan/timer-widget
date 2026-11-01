package io.github.conorryan.timerwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

private val RowHeight = 48.dp

/** Wheel label for [value]: zero-padded for minutes and seconds. */
fun wheelLabel(value: Int, pad: Boolean): String = if (pad) value.toString().padStart(2, '0') else value.toString()

/**
 * Visual tier of wheel row [index] relative to the [center]ed row: 0 = the
 * highlighted value, 1 = its neighbors, 2 = everything else. Clamped so far
 * rows share one look, which lets each row derive its own tier while the
 * wheel scrolls instead of recomposing every visible row per crossing.
 */
fun wheelTier(index: Int, center: Int): Int = abs(index - center).coerceAtMost(2)

/**
 * Parses what the user typed into a wheel: digits only, clamped to [range].
 * Null (keep the current value) when nothing usable was typed.
 */
fun parseWheelEntry(raw: String, range: IntRange): Int? =
    raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(range)

/**
 * Scrollable number wheel for one part of a duration. It snaps one value into
 * the highlighted band, and tapping that value turns it into a text field for
 * typing. Values the user scrolls to are reported through [onValueChange];
 * values set from outside (presets, typing) animate the wheel there without
 * echoing the numbers it passes on the way.
 */
@Composable
fun TimeWheel(
    value: Int,
    range: IntRange,
    label: String,
    unit: String,
    pad: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = range.last - range.first + 1
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, count - 1),
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)
    var syncing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centered }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index -> if (!syncing) currentOnChange(range.first + index) }
    }
    LaunchedEffect(value) {
        val target = (value - range.first).coerceIn(0, count - 1)
        // Never fight the user's own drag or fling; their value is the one being reported.
        if (centered != null && centered != target && !state.isScrollInProgress) {
            syncing = true
            try {
                state.animateScrollToItem(target)
            } finally {
                syncing = false
            }
        }
    }

    val valueText = wheelLabel(value, pad)
    val bandShape = RoundedCornerShape(14.dp)
    val wheelShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight * 3)
                .clip(wheelShape)
                .background(Brand.Surface)
                .border(1.dp, Brand.Line, wheelShape)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(RowHeight)
                    .clip(bandShape)
                    .background(Brand.Raised)
                    .border(2.dp, Brand.Amber, bandShape),
            )
            LazyColumn(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(vertical = RowHeight),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count) { index ->
                    // Per-row tier: only the rows whose look changes recompose
                    // when the centered value moves, not the whole viewport.
                    val tier by remember {
                        derivedStateOf { wheelTier(index, centered ?: (currentValue - range.first)) }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .clickable {
                                if (tier == 0) {
                                    editing = true
                                } else {
                                    scope.launch { state.animateScrollToItem(index) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            wheelLabel(range.first + index, pad),
                            style = TabularNumbers,
                            fontSize = if (tier == 0) 32.sp else 22.sp,
                            fontWeight = if (tier == 0) FontWeight.Medium else FontWeight.Normal,
                            color = when (tier) {
                                0 -> Brand.Text
                                1 -> Brand.WheelNear
                                else -> Brand.WheelFar
                            },
                        )
                    }
                }
            }
            if (editing) {
                WheelEntry(
                    initial = valueText,
                    label = label,
                    onCommit = { raw ->
                        editing = false
                        parseWheelEntry(raw, range)?.let { currentOnChange(it) }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 6.dp)
                        .fillMaxWidth()
                        .height(RowHeight)
                        .clip(bandShape)
                        .background(Brand.Ground)
                        .border(2.dp, Brand.Amber, bandShape),
                )
            }
        }
        Text(unit, fontSize = 13.sp, color = Brand.TextMuted)
    }
}

@Composable
private fun WheelEntry(
    initial: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hadFocus by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    val commit = {
        if (!committed) {
            committed = true
            keyboard?.hide()
            onCommit(text.text)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { next -> text = next.copy(text = next.text.filter(Char::isDigit).take(2)) },
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged { state ->
                // The first callback reports "unfocused" before focus lands; only
                // a real loss of focus (tapping elsewhere) commits.
                if (state.isFocused) hadFocus = true else if (hadFocus) commit()
            }
            .semantics { contentDescription = label },
        singleLine = true,
        textStyle = TabularNumbers.copy(
            color = Brand.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Brand.Amber),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        },
    )
}

/** Tabular figures so countdowns and wheels don't jitter as digits change. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
