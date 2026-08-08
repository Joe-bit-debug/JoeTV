package com.joeshannon.joetv.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.joeshannon.joetv.IJoeTvSystemService
import rikka.shizuku.Shizuku

object ShizukuManager {

    const val PERMISSION_REQUEST_CODE = 1001
    private const val TAG = "JoeTV-Shizuku"

    private var systemService: IJoeTvSystemService? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null

    private var onConnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            Log.d(TAG, "UserService connected: $name")

            if (binder == null) {
                onErrorCallback?.invoke(
                    "Shizuku connected, but the UserService Binder was null."
                )
                return
            }

            systemService =
                IJoeTvSystemService.Stub.asInterface(binder)

            onConnectedCallback?.invoke()
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            Log.w(TAG, "UserService disconnected: $name")
            systemService = null
        }

        override fun onBindingDied(
            name: ComponentName?
        ) {
            Log.e(TAG, "UserService binding died: $name")
            systemService = null

            onErrorCallback?.invoke(
                "JoeTV's Shizuku UserService binding died."
            )
        }

        override fun onNullBinding(
            name: ComponentName?
        ) {
            Log.e(TAG, "UserService returned a null binding: $name")

            onErrorCallback?.invoke(
                "JoeTV's Shizuku UserService returned a null binding."
            )
        }
    }

    fun isShizukuRunning(): Boolean {
        return runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)
    }

    fun hasPermission(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() ==
                    PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission() {
        if (!isShizukuRunning()) {
            onErrorCallback?.invoke("Shizuku is not running.")
            return
        }

        if (!hasPermission()) {
            Shizuku.requestPermission(
                PERMISSION_REQUEST_CODE
            )
        }
    }

    fun bind(
        context: Context,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        onConnectedCallback = onConnected
        onErrorCallback = onError

        Log.d(
            TAG,
            "bind() called. " +
                    "running=${isShizukuRunning()}, " +
                    "permission=${hasPermission()}"
        )

        if (!isShizukuRunning()) {
            onError("Shizuku is not running.")
            return
        }

        if (!hasPermission()) {
            Log.d(TAG, "Requesting Shizuku permission.")
            onError("JoeTV needs Shizuku permission.")
            requestPermission()
            return
        }

        if (systemService != null) {
            Log.d(TAG, "UserService is already connected.")
            onConnected()
            return
        }

        val componentName = ComponentName(
            context,
            JoeTvSystemService::class.java
        )

        Log.d(
            TAG,
            "Binding UserService: ${componentName.flattenToString()}"
        )

        val args = Shizuku.UserServiceArgs(componentName)
            .tag("joetv_system_service")
            .processNameSuffix("joetv_system")
            .daemon(false)
            .debuggable(true)
            .version(2)

        serviceArgs = args

        try {
            Shizuku.bindUserService(
                args,
                serviceConnection
            )

            Log.d(TAG, "bindUserService() submitted.")
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "bindUserService() failed.",
                exception
            )

            onError(
                exception.message
                    ?: "Unable to connect to JoeTV system service."
            )
        }
    }

    fun setTvMode(): Result<String> {
        return runCommand(
            "wm size 1920x1080"
        )
    }

    fun setTabletMode(): Result<String> {
        return runCommand(
            "wm size reset"
        )
    }

    fun runCommand(
        command: String
    ): Result<String> {
        return runCatching {
            Log.d(TAG, "Running command: $command")

            val service = systemService
                ?: error(
                    "JoeTV system service is not connected."
                )

            val result = service.runCommand(command)

            Log.d(TAG, "Command result: $result")
            result
        }
    }

    fun unbind() {
        val args = serviceArgs ?: return

        runCatching {
            Shizuku.unbindUserService(
                args,
                serviceConnection,
                true
            )
        }.onFailure {
            Log.e(TAG, "Unable to unbind UserService.", it)
        }

        systemService = null
        serviceArgs = null
    }
}