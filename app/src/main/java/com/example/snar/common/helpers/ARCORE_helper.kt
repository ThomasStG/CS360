package com.example.snar.common.helpers

import com.google.ar.core.ArCoreApk
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException


fun isARCoreSupported(context: Context): Boolean {
    val availability = ArCoreApk.getInstance().checkAvailability(context)

    return when (availability) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
            // This case indicates ARCore is supported but requires an update or installation
            ArCoreApk.getInstance().requestInstall(context as Activity, true)
            false
        }
        else -> false // Not supported or unknown status
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
