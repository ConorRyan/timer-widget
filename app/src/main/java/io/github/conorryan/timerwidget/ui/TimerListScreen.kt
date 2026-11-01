package io.github.conorryan.timerwidget.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.conorryan.timerwidget.R
import io.github.conorryan.timerwidget.data.TimerDef
import io.github.conorryan.timerwidget.util.formatHms
import java.util.Date

@Composable
fun TimerListScreen(
    timers: List<TimerDef>,
    runs: List<RunUi>,
    onToggle: (timerId: String) -> Unit,
    onStopRun: (key: Int) -> Unit,
    onEdit: (timerId: String) -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        containerColor = Brand.Ground,
        floatingActionButton = {
            if (timers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_timer), fontWeight = FontWeight.SemiBold) },
                    containerColor = Brand.Amber,
                    contentColor = Brand.OnAmber,
                    shape = RoundedCornerShape(18.dp),
                )
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            EmptyState(onCreate = onCreate, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                // Room for the floating button over the last row.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle(stringResource(R.string.timers_title)) }
            if (runs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(stringResource(R.string.section_running))
                }
                items(runs, key = { "run-${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { run ->
                    RunCard(run = run, onStop = { onStopRun(run.key) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    stringResource(R.string.section_all),
                    modifier = Modifier.padding(top = if (runs.isEmpty()) 0.dp else 12.dp),
                )
            }
            items(timers, key = { it.id }) { timer ->
                TimerTile(
                    timer = timer,
                    run = runs.firstOrNull { it.timerId == timer.id },
                    onToggle = { onToggle(timer.id) },
                    onEdit = { onEdit(timer.id) },
                )
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        color = Brand.Text,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = Brand.TextMuted,
        modifier = modifier.padding(start = 8.dp),
    )
}

@Composable
private fun RunCard(run: RunUi, onStop: () -> Unit) {
    val context = LocalContext.current
    val sub = if (run.finished) {
        stringResource(R.string.run_finished)
    } else {
        stringResource(
            R.string.run_progress,
            formatHms(run.totalSeconds),
            DateFormat.getTimeFormat(context).format(Date(run.endAtMs)),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brand.Amber)
            .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = Brand.OnAmber.copy(alpha = 0.18f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = Brand.OnAmber,
                    startAngle = -90f,
                    sweepAngle = 360f * run.fractionLeft,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text(run.emoji, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(run.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.OnAmber)
            Text(
                formatHms(run.displaySeconds),
                style = TabularNumbers,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = Brand.OnAmber,
            )
            Text(sub, fontSize = 13.sp, color = Brand.OnAmberMuted)
        }
        FilledIconButton(
            onClick = onStop,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Brand.OnAmber,
                contentColor = Brand.Amber,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_timer_named, run.name))
        }
    }
}

@Composable
private fun TimerTile(
    timer: TimerDef,
    run: RunUi?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val running = run != null
    val name = timer.name.ifBlank { stringResource(R.string.timer_fallback) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand.Surface)
            .border(if (running) 2.dp else 1.dp, if (running) Brand.Amber else Brand.Line, shape)
            .clickable(
                onClickLabel = stringResource(if (running) R.string.stop_timer else R.string.start_timer),
                onClick = onToggle,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.Raised),
                contentAlignment = Alignment.Center,
            ) {
                Text(timer.emoji, fontSize = 24.sp)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatHms(run?.displaySeconds ?: timer.totalSeconds),
                        style = TabularNumbers,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (running) Brand.Amber else Brand.Text,
                    )
                    Text(
                        name,
                        fontSize = 14.sp,
                        color = Brand.TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (running) Brand.Amber else Brand.Raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (running) Brand.OnAmber else Brand.Amber,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.edit_timer_named, name),
                tint = Brand.TextSoft,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenTitle(stringResource(R.string.timers_title))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WedgeIllustration(Modifier.size(148.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.empty_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Brand.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.empty_body),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Brand.TextSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onCreate,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.OnAmber),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.empty_create), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            // Keep the group optically centred above the gesture bar.
            Spacer(Modifier.height(80.dp))
        }
    }
}

/** The launcher icon's motif: a widget tile with a draining countdown wedge. */
@Composable
private fun WedgeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.width / 148f
        drawRoundRect(
            color = Brand.Surface,
            cornerRadius = CornerRadius(36 * unit),
        )
        drawRoundRect(
            color = Brand.Line,
            cornerRadius = CornerRadius(36 * unit),
            style = Stroke(2 * unit),
        )
        val radius = 42 * unit
        drawCircle(Brand.Raised, radius = radius, center = center)
        drawArc(
            color = Brand.Amber,
            startAngle = -90f,
            sweepAngle = 72f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
        )
        drawCircle(Brand.Ground, radius = 5 * unit, center = center)
    }
}
