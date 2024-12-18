package com.example.snar.common.helpers

import com.google.ar.core.ArCoreApk
import android.content.Context
import android.app.Activity
import android.util.Log
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Manages an ARCore Session using the Android Lifecycle API. Before starting a Session, this class
 * requests installation of Google Play Services for AR if it's not installed or not up to date and
 * asks the user for required permissions if necessary.
 */
class ARCoreSessionLifecycleHelper(
    val activity: Activity,
    val features: Set<Session.Feature> = setOf(Session.Feature.SHARED_CAMERA)
) : DefaultLifecycleObserver {
    var installRequested = false
    var session: Session? = null
        private set
    var isSessionPaused: Boolean = true

    /**
     * Creating a session may fail. In this case, session will remain null, and this function will be
     * called with an exception.
     *
     * See
     * [the `Session` constructor](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#Session(android.content.Context)
     * ) for more details.
     */
    var exceptionCallback: ((Exception) -> Unit)? = null
    var sessionStateCallback: ((isPaused: Boolean) -> Unit)? = null

    /**
     * Before `Session.resume()` is called, a session must be configured. Use
     * [`Session.configure`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#configure-config)
     * or
     * [`setCameraConfig`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#setCameraConfig-cameraConfig)
     */
    var beforeSessionResume: ((Session) -> Unit)? = null

    /**
     * Attempts to create a session. If Google Play Services for AR is not installed or not up to
     * date, request installation.
     *
     * @return null when the session cannot be created due to a lack of the CAMERA permission or when
     * Google Play Services for AR is not installed or up to date, or when session creation fails for
     * any reason. In the case of a failure, [exceptionCallback] is invoked with the failure
     * exception.
     */
    private fun tryCreateSession(): Session? {
        // The app must have been given the CAMERA permission. If we don't have it yet, request it.
        if (!GeoPermissionsHelper.hasGeoPermissions(activity)) {
            GeoPermissionsHelper.requestPermissions(activity)
            return null
        }

        return try {
            // Request installation if necessary.
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    // tryCreateSession will be called again, so we return null for now.
                    return null
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // Left empty; nothing needs to be done.
                }
            }

            Log.d("ARCore", "Session created successfully")
            // Create a session if Google Play Services for AR is installed and up to date.
            Session(activity, features)

        } catch (e: Exception) {
            exceptionCallback?.invoke(e)
            null
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        val session = this.session ?: tryCreateSession() ?: return
        Log.d("ARCore", "onResume called - AR session will resume.")
        try {
            beforeSessionResume?.invoke(session)
            session.resume()
            this.session = session
            isSessionPaused = false
            super.onResume(owner)
        } catch (e: CameraNotAvailableException) {
            Log.e("ARCoreError", "error resuming")
            exceptionCallback?.invoke(e)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        Log.d("ARCore", "onPause called - AR session will pause.")
        session?.pause()
        isSessionPaused = true
        super.onPause(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Explicitly close the ARCore session to release native resources.
        // Review the API reference for important considerations before calling close() in apps with
        // more complicated lifecycle requirements:
        // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
        session?.close()
        session = null
    }
}

fun isARCoreSessionAvailable(context: Context): Boolean {
    return try {
        // Try creating an ARCore session; if it fails, ARCore is not supported
        val session = Session(context)
        session.close()  // Close the session after successful creation
        true
    } catch (e: UnavailableArcoreNotInstalledException) {
        // ARCore is not installed
        ArCoreApk.getInstance().requestInstall(context as Activity, true)
        false
    } catch (e: UnavailableDeviceNotCompatibleException) {
        // Device is not compatible with ARCore
        false
    } catch (e: Exception) {
        // Other errors, assume ARCore isn't supported
        false
    }
}