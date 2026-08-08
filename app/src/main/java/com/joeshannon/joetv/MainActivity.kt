package com.joeshannon.joetv

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joeshannon.joetv.screens.HomeScreen
import com.joeshannon.joetv.system.ShizukuManager
import com.joeshannon.joetv.ui.theme.JoeTVTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val hideBarsRunnable = Runnable {
        hideSystemBars()
    }

    private val shizukuBinderListener =
        Shizuku.OnBinderReceivedListener {
            Log.d(
                "JoeTV",
                "Shizuku binder received"
            )

            connectAndTestShizuku()
        }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener {
                requestCode,
                grantResult ->

            if (
                requestCode ==
                ShizukuManager.PERMISSION_REQUEST_CODE
            ) {
                if (
                    grantResult ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(
                        "JoeTV",
                        "Shizuku permission granted"
                    )

                    connectAndTestShizuku()
                } else {
                    Log.e(
                        "JoeTV",
                        "Shizuku permission denied"
                    )
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        hideSystemBars()

        Shizuku.addBinderReceivedListenerSticky(
            shizukuBinderListener
        )

        Shizuku.addRequestPermissionResultListener(
            shizukuPermissionListener
        )

        setContent {
            JoeTVTheme {
                HomeScreen(
                    context = this@MainActivity
                )
            }
        }
    }

    private fun connectAndTestShizuku() {
        ShizukuManager.bind(
            context = this,
            onConnected = {
                Log.d(
                    "JoeTV",
                    "CONNECTED TO SHIZUKU USER SERVICE"
                )

                val result =
                    ShizukuManager.setTvMode()

                Log.d(
                    "JoeTV",
                    "TV mode result: ${
                        result.getOrElse {
                            it.message ?: "Unknown error"
                        }
                    }"
                )
            },
            onError = { message ->
                Log.e(
                    "JoeTV",
                    "Shizuku error: $message"
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            300
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            1_000
        )
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onConfigurationChanged(
        newConfig: Configuration
    ) {
        super.onConfigurationChanged(newConfig)

        hideSystemBars()

        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            500
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            1_500
        )
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(
            shizukuBinderListener
        )

        Shizuku.removeRequestPermissionResultListener(
            shizukuPermissionListener
        )

        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(
                WindowInsetsCompat.Type.statusBars() or
                        WindowInsetsCompat.Type.navigationBars()
            )

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}