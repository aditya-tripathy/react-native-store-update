package com.thronie.inappupdate

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class InAppUpdatePackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
    if (name == InAppUpdateModule.NAME) InAppUpdateModule(reactContext) else null

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      InAppUpdateModule.NAME to ReactModuleInfo(
        InAppUpdateModule.NAME,
        InAppUpdateModule.NAME,
        false, // canOverrideExistingModule
        false, // needsEagerInit
        false, // isCxxModule
        BuildConfig.IS_NEW_ARCHITECTURE_ENABLED // isTurboModule
      )
    )
  }
}
