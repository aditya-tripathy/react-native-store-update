package com.thronie.inappupdate

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class InAppUpdateModule(reactContext: ReactApplicationContext) :
  InAppUpdateSpec(reactContext), InstallStateUpdatedListener {

  private val updateManager by lazy { AppUpdateManagerFactory.create(reactApplicationContext) }
  private var updatePromise: Promise? = null
  private var isInstallListenerRegistered = false

  private val activityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(
      activity: Activity,
      requestCode: Int,
      resultCode: Int,
      data: Intent?
    ) {
      if (requestCode != UPDATE_REQUEST_CODE) return
      val promise = updatePromise ?: return
      updatePromise = null
      when (resultCode) {
        Activity.RESULT_OK -> promise.resolve("accepted")
        Activity.RESULT_CANCELED -> promise.resolve("canceled")
        ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> promise.resolve("failed")
        else -> promise.resolve("failed")
      }
    }
  }

  init {
    reactContext.addActivityEventListener(activityEventListener)
  }

  override fun getName() = NAME

  // `country` only applies to the App Store lookup on iOS.
  @ReactMethod
  override fun checkForUpdate(country: String, promise: Promise) {
    updateManager.appUpdateInfo
      .addOnSuccessListener { info -> promise.resolve(buildUpdateInfo(info)) }
      .addOnFailureListener { error ->
        promise.reject(E_UPDATE_CHECK_FAILED, "Failed to check for updates: ${error.message}", error)
      }
  }

  @ReactMethod
  override fun startUpdate(updateType: String, promise: Promise) {
    val type = when (updateType) {
      "flexible" -> AppUpdateType.FLEXIBLE
      "immediate" -> AppUpdateType.IMMEDIATE
      else -> {
        promise.reject(
          E_INVALID_UPDATE_TYPE,
          "Unknown update type '$updateType'; expected 'flexible' or 'immediate'."
        )
        return
      }
    }

    if (updatePromise != null) {
      promise.reject(E_UPDATE_IN_PROGRESS, "An update flow is already in progress.")
      return
    }

    // The update info carries a single-use PendingIntent, so it has to be fresh.
    updateManager.appUpdateInfo
      .addOnSuccessListener { info ->
        val availability = info.updateAvailability()
        if (availability != UpdateAvailability.UPDATE_AVAILABLE &&
          availability != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        ) {
          promise.reject(E_NO_UPDATE_AVAILABLE, "No update is available for this app.")
          return@addOnSuccessListener
        }
        if (!info.isUpdateTypeAllowed(type)) {
          promise.reject(
            E_UPDATE_TYPE_NOT_ALLOWED,
            "Play does not allow a '$updateType' update for this app right now."
          )
          return@addOnSuccessListener
        }
        val activity = reactApplicationContext.currentActivity
        if (activity == null) {
          promise.reject(E_NO_ACTIVITY, "The update flow needs a foreground activity.")
          return@addOnSuccessListener
        }

        registerInstallListener()
        updatePromise = promise
        try {
          updateManager.startUpdateFlowForResult(
            info,
            activity,
            AppUpdateOptions.defaultOptions(type),
            UPDATE_REQUEST_CODE
          )
        } catch (error: IntentSender.SendIntentException) {
          updatePromise = null
          promise.reject(E_UPDATE_FAILED, "Failed to start the update flow: ${error.message}", error)
        }
      }
      .addOnFailureListener { error ->
        promise.reject(E_UPDATE_CHECK_FAILED, "Failed to check for updates: ${error.message}", error)
      }
  }

  @ReactMethod
  override fun completeUpdate(promise: Promise) {
    updateManager.completeUpdate()
      .addOnSuccessListener { promise.resolve(null) }
      .addOnFailureListener { error ->
        promise.reject(
          E_COMPLETE_UPDATE_FAILED,
          "Failed to install the downloaded update: ${error.message}",
          error
        )
      }
  }

  // Install status is delivered through RCTDeviceEventEmitter, so these are only
  // here to satisfy the NativeEventEmitter contract on the JS side.
  @ReactMethod
  override fun addListener(eventName: String) = Unit

  @ReactMethod
  override fun removeListeners(count: Double) = Unit

  override fun onStateUpdate(state: InstallState) {
    val event = Arguments.createMap().apply {
      putString("status", installStatusToString(state.installStatus()))
      putDouble("bytesDownloaded", state.bytesDownloaded().toDouble())
      putDouble("totalBytesToDownload", state.totalBytesToDownload().toDouble())
      putDouble("errorCode", state.installErrorCode().toDouble())
    }
    reactApplicationContext.emitDeviceEvent(INSTALL_STATUS_EVENT, event)
  }

  override fun invalidate() {
    if (isInstallListenerRegistered) {
      updateManager.unregisterListener(this)
      isInstallListenerRegistered = false
    }
    reactApplicationContext.removeActivityEventListener(activityEventListener)
    updatePromise = null
    super.invalidate()
  }

  private fun registerInstallListener() {
    if (!isInstallListenerRegistered) {
      updateManager.registerListener(this)
      isInstallListenerRegistered = true
    }
  }

  private fun buildUpdateInfo(info: AppUpdateInfo): WritableMap {
    val availability = info.updateAvailability()
    val packageInfo = currentPackageInfo()
    return Arguments.createMap().apply {
      putBoolean("updateAvailable", availability == UpdateAvailability.UPDATE_AVAILABLE)
      putString("currentVersion", packageInfo?.versionName ?: "")
      // Play only exposes the version code of the pending update, never its version name.
      putString("storeVersion", "")
      putDouble("currentVersionCode", (packageInfo?.let { versionCodeOf(it) } ?: -1L).toDouble())
      putDouble("availableVersionCode", info.availableVersionCode().toDouble())
      putDouble("updatePriority", info.updatePriority().toDouble())
      putDouble(
        "clientVersionStalenessDays",
        (info.clientVersionStalenessDays() ?: -1).toDouble()
      )
      putBoolean("isFlexibleUpdateAllowed", info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
      putBoolean("isImmediateUpdateAllowed", info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
      putBoolean(
        "isUpdateInProgress",
        availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
      )
      putString("installStatus", installStatusToString(info.installStatus()))
    }
  }

  private fun currentPackageInfo(): PackageInfo? = try {
    reactApplicationContext.packageManager
      .getPackageInfo(reactApplicationContext.packageName, 0)
  } catch (error: PackageManager.NameNotFoundException) {
    null
  }

  @Suppress("DEPRECATION")
  private fun versionCodeOf(packageInfo: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      packageInfo.longVersionCode
    } else {
      packageInfo.versionCode.toLong()
    }

  private fun installStatusToString(status: Int) = when (status) {
    InstallStatus.PENDING -> "pending"
    InstallStatus.DOWNLOADING -> "downloading"
    InstallStatus.DOWNLOADED -> "downloaded"
    InstallStatus.INSTALLING -> "installing"
    InstallStatus.INSTALLED -> "installed"
    InstallStatus.FAILED -> "failed"
    InstallStatus.CANCELED -> "canceled"
    InstallStatus.REQUIRES_UI_INTENT -> "requiresUiIntent"
    else -> "unknown"
  }

  companion object {
    const val NAME = "InAppUpdate"
    private const val INSTALL_STATUS_EVENT = "InAppUpdate:installStatusChanged"
    private const val UPDATE_REQUEST_CODE = 47820
    private const val E_UPDATE_CHECK_FAILED = "E_UPDATE_CHECK_FAILED"
    private const val E_INVALID_UPDATE_TYPE = "E_INVALID_UPDATE_TYPE"
    private const val E_UPDATE_IN_PROGRESS = "E_UPDATE_IN_PROGRESS"
    private const val E_NO_UPDATE_AVAILABLE = "E_NO_UPDATE_AVAILABLE"
    private const val E_UPDATE_TYPE_NOT_ALLOWED = "E_UPDATE_TYPE_NOT_ALLOWED"
    private const val E_NO_ACTIVITY = "E_NO_ACTIVITY"
    private const val E_UPDATE_FAILED = "E_UPDATE_FAILED"
    private const val E_COMPLETE_UPDATE_FAILED = "E_COMPLETE_UPDATE_FAILED"
  }
}
