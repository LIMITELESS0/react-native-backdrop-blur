package com.backdropblur

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.BackdropBlurViewManagerDelegate
import com.facebook.react.viewmanagers.BackdropBlurViewManagerInterface

@ReactModule(name = BackdropBlurViewManager.NAME)
class BackdropBlurViewManager :
  SimpleViewManager<BackdropBlurView>(),
  BackdropBlurViewManagerInterface<BackdropBlurView> {

  private val delegate =
    BackdropBlurViewManagerDelegate<BackdropBlurView, BackdropBlurViewManager>(this)

  override fun getDelegate(): ViewManagerDelegate<BackdropBlurView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): BackdropBlurView =
    BackdropBlurView(context)

  @ReactProp(name = "blurRadius", defaultFloat = 10f)
  override fun setBlurRadius(view: BackdropBlurView, value: Float) {
    view.setBlurRadiusDp(value)
  }

  override fun onDropViewInstance(view: BackdropBlurView) {
    super.onDropViewInstance(view)
    view.release()
  }

  companion object {
    const val NAME = "BackdropBlurView"
  }
}
