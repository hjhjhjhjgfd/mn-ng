package com.v2ray.ang.ui

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils

/**
 * BaseActivity for every Activity in MahsaNG.
 *
 * Apart from the original locale-wrapping behavior, this base class
 * also configures the **edge-to-edge + translucent system bars** that
 * the Material 2027 Expressive glass theme relies on:
 *
 *  * The status bar and navigation bar become transparent so the
 *    floating glass toolbar / footer can sit on top of content.
 *  * On API 29+ the platform-enforced contrast tints are turned off
 *    so the glass stroke stays the only hairline visible.
 *  * The root view receives WindowInsets padding so content does not
 *    collide with the system bars (this is what
 *    `android:fitsSystemWindows="true"` already does on most layouts,
 *    but we set it explicitly here as a safety net).
 *
 * The whole helper is no-op on API < 21 (the project's minSdk), so
 * older devices fall back to the original opaque system bars without
 * crashing.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase,  Utils.getLocale(newBase))
        }
        super.attachBaseContext(context)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyGlassSystemBars()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyGlassSystemBars()
    }

    /**
     * Enable transparent, contrast-free system bars so the floating
     * glass toolbar / footer read on top of content. Idempotent.
     */
    private fun applyGlassSystemBars() {
        try {
            // Tell the platform we want to draw behind the system bars.
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Transparent status + nav bar colors (kept inside try in
            // case the window is not yet attached, e.g. during tests).
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Don't let the platform auto-add a contrast scrim.
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }

            // Light/dark icon appearance follows the current night mode.
            val isNight = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isNight
                isAppearanceLightNavigationBars = !isNight
            }

            // Padding safety net: only applied to plain content roots.
            // DrawerLayout has its own fitsSystemWindows handling and
            // would double-pad if we touched it here, so we skip it.
            val root = window.decorView.findViewById<View>(android.R.id.content) ?: return
            if (root is androidx.drawerlayout.widget.DrawerLayout) return
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
                )
                v.setPadding(
                    v.paddingLeft,
                    bars.top.coerceAtLeast(v.paddingTop),
                    v.paddingRight,
                    bars.bottom.coerceAtLeast(v.paddingBottom)
                )
                insets
            }
        } catch (_: Throwable) {
            // Defensive: window flags must never crash an activity.
        }
    }
}
