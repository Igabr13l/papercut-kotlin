package com.igabr13l.papercut

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.igabr13l.papercut.game.GLView
import com.igabr13l.papercut.game.Game
import com.igabr13l.papercut.game.HudView
import com.igabr13l.papercut.game.Sfx

class MainActivity : Activity() {
    lateinit var game: Game
    lateinit var glView: GLView
    lateinit var hud: HudView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sfx = Sfx()
        game = Game()
        game.sfx = sfx

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        game.onHaptic = { ms, amp ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ms)
                }
            } catch (_: Throwable) {}
        }

        val view = GLView(this, game) { hud.redraw() }
        glView = view
        val hudView = HudView(this, game, sfx)
        hud = hudView

        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(hudView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        hideBars()
    }

    override fun onPause() {
        game.pause()
        glView.onPause()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { game.pause(); return true }
        return super.onKeyDown(keyCode, e)
    }

    fun hideBars() {
        val flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }
}
