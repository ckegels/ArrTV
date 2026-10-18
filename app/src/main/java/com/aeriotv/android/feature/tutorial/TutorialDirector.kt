package com.aeriotv.android.feature.tutorial

import android.os.SystemClock
import android.view.View
import android.view.KeyEvent
import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText
import com.aeriotv.android.core.remote.GuideRemoteAction
import com.aeriotv.android.core.remote.PlayerRemoteAction
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.core.remote.RemoteSlot
import com.aeriotv.android.feature.settings.labelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The live remote tour: the app drives itself through the guide and the
 * player with real key presses, while a remote drawn at the side lights up
 * each key and says what it does. Each press is handed to whichever of the
 * app's windows has focus (the activity, or a pop-up menu over it), the way
 * the system delivers remote keys, so the guide, the player and their menus
 * react exactly as to the remote. (Injecting through the input system needs a
 * permission apps cannot hold.)
 *
 * While it runs, keys from the real remote are swallowed by MainActivity
 * ([interceptRealKey]); Back stops the tour.
 */
object TutorialDirector {

    /** What the side panel shows. */
    data class Shown(
        val section: String,
        val caption: String,
        val keys: Set<TutorialKey>,
        val hold: Boolean = false,
        val end: Boolean = false,
        /** A smaller line under the caption, with the guide's replay symbol. */
        val catchupNote: Boolean = false,
    )

    private val _shown = MutableStateFlow<Shown?>(null)
    val shown: StateFlow<Shown?> = _shown.asStateFlow()

    private val _startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Collected by the host in the nav graph, which knows the channels. */
    val startRequests: SharedFlow<Unit> = _startRequests.asSharedFlow()

    /** True while a playlist with channels is loaded: the tour needs a guide. */
    val demoAvailable = MutableStateFlow(false)

    @Volatile var running = false
        private set
    private var job: Job? = null

    fun requestStart() { _startRequests.tryEmit(Unit) }

    fun stop() {
        job?.cancel()
        job = null
        running = false
        _shown.value = null
    }

    /** Synthetic presses carry this scan code so they are told apart from the remote. */
    private const val SYNTHETIC_SCAN_CODE = 0x7E57

    /**
     * Called first in MainActivity.dispatchKeyEvent. While the tour runs, a
     * key from the real remote is swallowed; Back (or OK on the last card)
     * ends the tour.
     */
    fun interceptRealKey(event: KeyEvent): Boolean {
        if (!running || event.scanCode == SYNTHETIC_SCAN_CODE) return false
        if (event.action == KeyEvent.ACTION_UP) {
            val end = _shown.value?.end == true
            if (event.keyCode == KeyEvent.KEYCODE_BACK ||
                (end && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER))
            ) stop()
        }
        return true
    }

    private data class Step(
        val key: TutorialKey,
        val section: String,
        val caption: String,
        val hold: Boolean = false,
        /** How long to let the app react before the next step. */
        val after: Long = 1_400,
        /** Skip the rest of the tour if the player is not up (the channel did not start). */
        val needsPlayer: Boolean = false,
        val catchupNote: Boolean = false,
    )

    /**
     * Starts the tour. [isPlayerUp] tells whether the fullscreen player is the
     * top screen; the guide must already be on screen with a channel focused.
     */
    fun start(scope: CoroutineScope, map: RemoteControlMap, sidebarGroups: Boolean, isPlayerUp: () -> Boolean) {
        stop()
        val steps = script(map, sidebarGroups)
        running = true
        job = scope.launch {
            try {
                for (step in steps) {
                    if (step.needsPlayer && !isPlayerUp()) break
                    // Show the key and what it does, then press it.
                    _shown.value = Shown(step.section, step.caption, setOf(step.key), step.hold, catchupNote = step.catchupNote)
                    delay(1_600)
                    press(step.key, step.hold)
                    delay(step.after)
                }
                _shown.value = Shown(
                    AppText.get(R.string.tutorial_end_title),
                    AppText.get(R.string.tutorial_end_text) + "\n\n" + AppText.get(R.string.tutorial_demo_your_turn),
                    emptySet(),
                    end = true,
                )
                delay(30_000)
            } finally {
                if (job == coroutineContext[Job]) {
                    running = false
                    _shown.value = null
                }
            }
        }
    }

    private fun script(map: RemoteControlMap, sidebarGroups: Boolean): List<Step> {
        val guide = AppText.get(R.string.tutorial_guide_title)
        val watch = AppText.get(R.string.tutorial_watch_title)
        fun g(slot: RemoteSlot) = map.guideAction(slot, sidebarGroups)
        fun p(slot: RemoteSlot) = map.playerAction(slot)
        fun gLabel(slot: RemoteSlot) = AppText.get(g(slot).labelRes(sidebarGroups))
        fun pLabel(slot: RemoteSlot) = AppText.get(p(slot).labelRes)
        val close = AppText.get(R.string.tutorial_demo_close)

        // Each part only runs when the button still does what the tour shows
        // (Settings › Remote Control can remap them).
        return buildList {
            val updown = AppText.get(R.string.tutorial_guide_updown)
            add(Step(TutorialKey.DOWN, guide, updown, after = 900))
            add(Step(TutorialKey.DOWN, guide, updown, after = 900))
            if (g(RemoteSlot.RIGHT_SHORT) == GuideRemoteAction.NAVIGATE) {
                val time = AppText.get(R.string.tutorial_guide_leftright_time)
                add(Step(TutorialKey.RIGHT, guide, time, after = 900))
                add(Step(TutorialKey.RIGHT, guide, time, after = 900))
                add(Step(TutorialKey.LEFT, guide, AppText.get(R.string.tutorial_demo_now), after = 900))
                add(Step(TutorialKey.LEFT, guide, AppText.get(R.string.tutorial_demo_now), after = 900))
            }
            if (g(RemoteSlot.LEFT_LONG) == GuideRemoteAction.TIMELINE_BACK) {
                // Earlier programs, with what the replay symbol means; Back
                // returns to now (and the top channel).
                add(Step(TutorialKey.LEFT, guide, gLabel(RemoteSlot.LEFT_LONG), hold = true, after = 4_500, catchupNote = true))
                add(Step(TutorialKey.BACK, guide, AppText.get(R.string.tutorial_guide_back), after = 1_500))
            }
            if (g(RemoteSlot.OK_LONG) == GuideRemoteAction.PROGRAM_MENU) {
                add(Step(TutorialKey.OK, guide, gLabel(RemoteSlot.OK_LONG), hold = true, after = 2_600))
                add(Step(TutorialKey.BACK, guide, close))
            }
            if (g(RemoteSlot.OK_SHORT) != GuideRemoteAction.PLAY) return@buildList
            add(Step(TutorialKey.OK, guide, gLabel(RemoteSlot.OK_SHORT), after = 6_000))

            if (p(RemoteSlot.UP_SHORT) == PlayerRemoteAction.CHANNEL_UP) {
                add(Step(TutorialKey.UP, watch, pLabel(RemoteSlot.UP_SHORT), after = 4_500, needsPlayer = true))
            }
            if (p(RemoteSlot.DOWN_SHORT) == PlayerRemoteAction.CHANNEL_DOWN) {
                add(Step(TutorialKey.DOWN, watch, pLabel(RemoteSlot.DOWN_SHORT), after = 4_500, needsPlayer = true))
            }
            if (p(RemoteSlot.OK_SHORT) == PlayerRemoteAction.TOGGLE_CONTROLS) {
                add(Step(TutorialKey.OK, watch, pLabel(RemoteSlot.OK_SHORT), after = 2_000, needsPlayer = true))
                // Held with the controls up: the options menu.
                add(Step(TutorialKey.OK, watch, AppText.get(R.string.tutorial_controls_ok_hold), hold = true, after = 3_000, needsPlayer = true))
                add(Step(TutorialKey.BACK, watch, close, after = 1_500, needsPlayer = true))
                val buttons = AppText.get(R.string.tutorial_controls_leftright)
                add(Step(TutorialKey.RIGHT, watch, buttons, after = 900, needsPlayer = true))
                add(Step(TutorialKey.RIGHT, watch, buttons, after = 900, needsPlayer = true))
                add(Step(TutorialKey.BACK, watch, AppText.get(R.string.tutorial_controls_back), after = 1_500, needsPlayer = true))
            }
            if (p(RemoteSlot.RIGHT_LONG) == PlayerRemoteAction.SHOW_PROGRAM_INFO) {
                add(Step(TutorialKey.RIGHT, watch, pLabel(RemoteSlot.RIGHT_LONG), hold = true, after = 3_000, needsPlayer = true))
                add(Step(TutorialKey.BACK, watch, close, after = 1_500, needsPlayer = true))
            }
            // Back shrinks the channel into a corner of the guide; Back again
            // brings it back full screen.
            add(Step(TutorialKey.BACK, watch, AppText.get(R.string.tutorial_demo_guide_back), after = 3_000, needsPlayer = true))
            add(Step(TutorialKey.BACK, guide, AppText.get(R.string.tutorial_demo_mini_back), after = 3_500))
            if (p(RemoteSlot.LEFT_SHORT) == PlayerRemoteAction.CHANNEL_LIST) {
                // Opens the guide on the groups list, the channel in the corner.
                add(Step(TutorialKey.LEFT, watch, pLabel(RemoteSlot.LEFT_SHORT), after = 3_000, needsPlayer = true))
                add(Step(TutorialKey.BACK, guide, close, after = 1_500))
            }
            if (g(RemoteSlot.RIGHT_LONG) == GuideRemoteAction.CLOSE_MINI_PLAYER) {
                add(Step(TutorialKey.RIGHT, guide, gLabel(RemoteSlot.RIGHT_LONG), hold = true, after = 1_500))
            }
        }
    }

    /**
     * The app's window that has focus right now: the activity's, or a dialog
     * over it. Read from WindowManagerGlobal's view list, as Espresso does.
     */
    @Suppress("UNCHECKED_CAST")
    private fun focusedRoot(): View? = runCatching {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val instance = wmg.getMethod("getInstance").invoke(null)
        val field = wmg.getDeclaredField("mViews").apply { isAccessible = true }
        val views = (field.get(instance) as List<View>).toList()
        views.lastOrNull { it.hasWindowFocus() } ?: views.lastOrNull()
    }.getOrNull()

    private fun send(event: KeyEvent) {
        focusedRoot()?.dispatchKeyEvent(event)
    }

    private fun keyCode(key: TutorialKey): Int = when (key) {
        TutorialKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        TutorialKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        TutorialKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        TutorialKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        TutorialKey.OK -> KeyEvent.KEYCODE_DPAD_CENTER
        TutorialKey.BACK -> KeyEvent.KEYCODE_BACK
        TutorialKey.HOME -> KeyEvent.KEYCODE_HOME
        TutorialKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        TutorialKey.CH_UP -> KeyEvent.KEYCODE_CHANNEL_UP
        TutorialKey.CH_DOWN -> KeyEvent.KEYCODE_CHANNEL_DOWN
    }

    /**
     * One press, or a hold: a down, auto-repeats every 50 ms for about 0.7 s
     * (the long-press flag from the 10th on, like the system does), then the
     * release. Delivered on the main thread, like real input.
     */
    private suspend fun press(key: TutorialKey, hold: Boolean) = withContext(Dispatchers.Main) {
        val code = keyCode(key)
        val down = SystemClock.uptimeMillis()
        fun ev(action: Int, repeat: Int, flags: Int = 0) = KeyEvent(
            down, SystemClock.uptimeMillis(), action, code, repeat, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, SYNTHETIC_SCAN_CODE,
            flags or KeyEvent.FLAG_FROM_SYSTEM, android.view.InputDevice.SOURCE_DPAD,
        )
        runCatching {
            send(ev(KeyEvent.ACTION_DOWN, 0))
            if (hold) {
                for (r in 1..14) {
                    delay(50)
                    send(ev(KeyEvent.ACTION_DOWN, r, if (r >= 10) KeyEvent.FLAG_LONG_PRESS else 0))
                }
            } else {
                delay(60)
            }
            send(ev(KeyEvent.ACTION_UP, 0))
        }.onFailure { android.util.Log.w("TutorialDirector", "key injection failed", it) }
    }
}
