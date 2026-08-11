package com.thronie.inappupdate

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule

abstract class InAppUpdateSpec internal constructor(context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  abstract fun checkForUpdate(country: String, promise: Promise)
  abstract fun startUpdate(updateType: String, promise: Promise)
  abstract fun completeUpdate(promise: Promise)
  abstract fun addListener(eventName: String)
  abstract fun removeListeners(count: Double)
}
