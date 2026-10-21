package io.github.conorryan.timerwidget.ui

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.timerwidget.data.TimerDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerViewModelTest {

    @Test
    fun editIsVisibleImmediatelyInUiState() {
        withCollectedStates { vm, states ->
            vm.add()
            awaitUntil { states.isNotEmpty() && states.last().isNotEmpty() }

            val timer = states.last().last()
            vm.update(timer.copy(name = "Tea"))

            // No idle after update: the edit must already be visible in UI
            // state, otherwise the name TextField recomposes with the stale
            // value and the typed characters disappear.
            assertEquals("Tea", states.last().last().name)
        }
    }

    @Test
    fun removalIsVisibleImmediatelyInUiState() {
        withCollectedStates { vm, states ->
            vm.add()
            awaitUntil { states.isNotEmpty() && states.last().isNotEmpty() }

            val timer = states.last().last()
            vm.remove(timer.id)

            assertEquals(0, states.last().count { it.id == timer.id })
        }
    }

    private fun withCollectedStates(block: (TimerViewModel, CopyOnWriteArrayList<List<TimerDef>>) -> Unit) {
        val vm = TimerViewModel(ApplicationProvider.getApplicationContext<Application>())
        val states = CopyOnWriteArrayList<List<TimerDef>>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { vm.timers.toList(states) }
        try {
            block(vm, states)
        } finally {
            collector.cancel()
        }
    }

    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("condition not met in time")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
