package com.backdropblur

import android.graphics.Color
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.BackdropBlurViewManagerInterface
import com.facebook.react.viewmanagers.BackdropBlurViewManagerDelegate

@ReactModule(name = BackdropBlurViewManager.NAME)
class BackdropBlurViewManager : SimpleViewManager<BackdropBlurView>(),
  BackdropBlurViewManagerInterface<BackdropBlurView> {
  private val mDelegate: ViewManagerDelegate<BackdropBlurView>

  init {
    mDelegate = BackdropBlurViewManagerDelegate(this)
  }

  override fun getDelegate(): ViewManagerDelegate<BackdropBlurView>? {
    return mDelegate
  }

  override fun getName(): String {
    return NAME
  }

  public override fun createViewInstance(context: ThemedReactContext): BackdropBlurView {
    return BackdropBlurView(context)
  }

  @ReactProp(name = "color")
  override fun setColor(view: BackdropBlurView?, color: Int?) {
    view?.setBackgroundColor(color ?: Color.TRANSPARENT)
  }

  companion object {
    const val NAME = "BackdropBlurView"
  }
}
