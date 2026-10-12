package com.backdropblur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Android counterpart of CSS `backdrop-filter: blur()`, for small overlays
 * that sit on top of a camera / video preview.
 *
 * The usual approach (draw the view tree into a Bitmap and blur it, which is
 * what Dimezis BlurView and most RN blur libraries do) can't see a
 * SurfaceView: its content is composited on a separate Surface and never
 * reaches the view tree's Canvas, so the result is empty.
 *
 * Instead this copies pixels straight from the SurfaceView behind it:
 * 1. Every [FRAME_INTERVAL_MS] ms, PixelCopy the whole preview into a
 *    downscaled Bitmap (the GPU does the scaling, so the copy is small), then
 *    crop the part behind this view by window position, plus a 2σ margin so
 *    the edges blur naturally.
 * 2. Run a three-pass box blur (≈ Gaussian) on that small crop. σ on the
 *    small image is only a few pixels, so it's cheap.
 * 3. In onDraw, stretch the small image back over the view; bilinear
 *    filtering adds a bit more smoothing on top.
 *
 * When there is no SurfaceView behind it (e.g. over a plain image) it falls
 * back to drawing the view tree behind it into a downscaled Bitmap instead
 * (see [captureViewTree]), then blurs that the same way.
 */
class BackdropBlurView(context: Context) : View(context) {

  private val density = resources.displayMetrics.density
  private var blurRadiusPx = 10f * density

  private val handler = Handler(Looper.getMainLooper())
  private var running = false
  private var copyInFlight = false

  private var sourceRef: WeakReference<SurfaceView>? = null

  // True while this view is drawing the window into its own capture bitmap,
  // so that it skips drawing itself (otherwise it would blur its own output).
  private var capturing = false
  private var viewCaptureBitmap: Bitmap? = null
  private val captureRect = Rect()

  // PixelCopy writes into captureBitmap (the downscaled full preview); the
  // blurred crop goes into displayBitmap. onDraw only ever reads
  // displayBitmap, so it never sees a half-written copy.
  private var captureBitmap: Bitmap? = null
  private var displayBitmap: Bitmap? = null
  private var pixels: IntArray = IntArray(0)
  private var scratch: IntArray = IntArray(0)

  // Window-coordinate rect that displayBitmap covers. onDraw maps it against
  // the view's current position, so a moving overlay doesn't drift much
  // between frames.
  private val displayWindowRect = Rect()
  private var pendingShownLeft = 0
  private var pendingShownTop = 0
  private var pendingDownsample = 1f

  private val drawDst = Rect()
  private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val tmpLoc = IntArray(2)

  private val tick =
    object : Runnable {
      override fun run() {
        if (!running) return
        requestCopy()
        handler.postDelayed(this, FRAME_INTERVAL_MS)
      }
    }

  init {
    // Background only; never takes touches.
    isClickable = false
    isFocusable = false
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  fun setBlurRadiusDp(value: Float) {
    blurRadiusPx = max(0f, value) * density
  }

  fun release() {
    stop()
    captureBitmap?.recycle()
    displayBitmap?.recycle()
    viewCaptureBitmap?.recycle()
    captureBitmap = null
    displayBitmap = null
    viewCaptureBitmap = null
    sourceRef = null
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    start()
  }

  override fun onDetachedFromWindow() {
    stop()
    super.onDetachedFromWindow()
  }

  override fun onVisibilityAggregated(isVisible: Boolean) {
    super.onVisibilityAggregated(isVisible)
    if (isVisible) start() else stop()
  }

  private fun start() {
    if (running || !isAttachedToWindow) return
    running = true
    handler.post(tick)
  }

  private fun stop() {
    running = false
    handler.removeCallbacks(tick)
  }

  private fun requestCopy() {
    if (copyInFlight || width == 0 || height == 0 || !isShown) return
    val source = findSource()
    if (source == null) {
      captureViewTree()
      return
    }
    val surface = source.holder.surface
    if (surface == null || !surface.isValid) return
    if (source.width <= 0 || source.height <= 0) return

    // Where the SurfaceView actually shows up in the window. CameraX's
    // PreviewView fills the screen with pivot(0,0) + scaleX/scaleY +
    // translation; getLocationInWindow returns the transformed origin, so the
    // shown size has to include the scale.
    val scaleX = source.scaleX
    val scaleY = source.scaleY
    if (scaleX <= 0f || scaleY <= 0f) return
    source.getLocationInWindow(tmpLoc)
    val shownLeft = tmpLoc[0]
    val shownTop = tmpLoc[1]
    val shownW = source.width * scaleX
    val shownH = source.height * scaleY

    // Downscale so that σ on the small image lands around TARGET_SMALL_SIGMA.
    val downsample = max(1f, blurRadiusPx / TARGET_SMALL_SIGMA)
    val fullW = max(1, ceil(shownW / downsample).toInt())
    val fullH = max(1, ceil(shownH / downsample).toInt())

    // Don't use PixelCopy's srcRect to copy just our part: srcRect is in the
    // Surface buffer's own coordinates, and a camera buffer is in sensor
    // orientation (sideways) until the system rotates it for display. A
    // srcRect computed from screen position lands somewhere else in the frame.
    // Like CameraX's own PreviewView.getBitmap(), copy the whole thing into a
    // Bitmap with the display aspect ratio (PixelCopy rotates it upright and
    // the GPU scales it down), then crop by screen position.
    val bitmap = ensureBitmap(captureBitmap, fullW, fullH).also { captureBitmap = it }

    pendingShownLeft = shownLeft
    pendingShownTop = shownTop
    pendingDownsample = downsample
    copyInFlight = true
    try {
      PixelCopy.request(
        source,
        bitmap,
        { result ->
          copyInFlight = false
          if (result == PixelCopy.SUCCESS && running) onCopied(bitmap)
        },
        handler,
      )
    } catch (e: IllegalArgumentException) {
      // e.g. the Surface went invalid right at this moment; try next frame.
      copyInFlight = false
    }
  }

  private fun onCopied(full: Bitmap) {
    if (full.isRecycled || width == 0 || height == 0) return
    val ds = pendingDownsample

    // This view (plus a 2σ margin for natural edges) in small-image coords.
    getLocationInWindow(tmpLoc)
    val margin = blurRadiusPx * 2
    val left = max(0, ((tmpLoc[0] - margin - pendingShownLeft) / ds).toInt())
    val top = max(0, ((tmpLoc[1] - margin - pendingShownTop) / ds).toInt())
    val right = min(full.width, ceil((tmpLoc[0] + width + margin - pendingShownLeft) / ds).toInt())
    val bottom = min(full.height, ceil((tmpLoc[1] + height + margin - pendingShownTop) / ds).toInt())
    val w = right - left
    val h = bottom - top
    if (w <= 0 || h <= 0) return

    // Window-coordinate rect this crop covers; onDraw stretches it back.
    displayWindowRect.set(
      (pendingShownLeft + left * ds).roundToInt(),
      (pendingShownTop + top * ds).roundToInt(),
      (pendingShownLeft + right * ds).roundToInt(),
      (pendingShownTop + bottom * ds).roundToInt(),
    )
    blurAndShow(full, left, top, w, h, ds)
  }

  /**
   * Fallback for when nothing behind this view is a SurfaceView: draw the
   * window (minus this view and everything drawn after it) into a small
   * Bitmap, the way Dimezis BlurView does, then blur it.
   *
   * Only what is painted *before* this view is backdrop, as in CSS. That is
   * this view's own earlier siblings, and at every ancestor level the
   * earlier siblings. The later ones (the tint and content laid over the
   * blur, overlays elsewhere in the tree) are hidden for the duration of the
   * draw with alpha 0, and this view skips itself via [capturing].
   *
   * Uses child index as drawing order, so a zIndex that reorders siblings
   * isn't taken into account.
   */
  private fun captureViewTree() {
    val root = rootView ?: return
    if (root.width <= 0 || root.height <= 0) return

    val downsample = max(1f, blurRadiusPx / TARGET_SMALL_SIGMA)
    val margin = (blurRadiusPx * 2).roundToInt()
    root.getLocationInWindow(tmpLoc)
    val rootLeft = tmpLoc[0]
    val rootTop = tmpLoc[1]
    getLocationInWindow(tmpLoc)
    captureRect.set(
      max(rootLeft, tmpLoc[0] - margin),
      max(rootTop, tmpLoc[1] - margin),
      min(rootLeft + root.width, tmpLoc[0] + width + margin),
      min(rootTop + root.height, tmpLoc[1] + height + margin),
    )
    if (captureRect.isEmpty) return

    val w = max(1, ceil(captureRect.width() / downsample).toInt())
    val h = max(1, ceil(captureRect.height() / downsample).toInt())
    val bitmap = ensureBitmap(viewCaptureBitmap, w, h).also { viewCaptureBitmap = it }
    bitmap.eraseColor(0)
    val canvas = Canvas(bitmap)
    canvas.scale(1f / downsample, 1f / downsample)
    canvas.translate((rootLeft - captureRect.left).toFloat(), (rootTop - captureRect.top).toFloat())

    val hidden = hideViewsDrawnAfterThis()
    capturing = true
    try {
      root.draw(canvas)
    } catch (e: RuntimeException) {
      // e.g. a hardware Bitmap somewhere in the tree, which a software Canvas
      // can't draw. Leave the last good frame (or nothing) in place.
      return
    } finally {
      capturing = false
      for ((view, alpha) in hidden) view.alpha = alpha
    }

    displayWindowRect.set(captureRect)
    blurAndShow(bitmap, 0, 0, w, h, downsample)
  }

  /** Sets alpha 0 on every view drawn after this one; returns what to restore. */
  private fun hideViewsDrawnAfterThis(): List<Pair<View, Float>> {
    val hidden = ArrayList<Pair<View, Float>>()
    var child: View = this
    var parent = child.parent
    while (parent is ViewGroup) {
      val index = parent.indexOfChild(child)
      for (i in index + 1 until parent.childCount) {
        val sibling = parent.getChildAt(i)
        if (sibling.visibility == VISIBLE && sibling.alpha > 0f) {
          hidden.add(sibling to sibling.alpha)
          sibling.alpha = 0f
        }
      }
      child = parent
      parent = child.parent
    }
    return hidden
  }

  /**
   * Blurs the (left, top, w, h) part of [src], which was captured at 1/[ds]
   * scale, into displayBitmap. displayWindowRect must already be set.
   */
  private fun blurAndShow(src: Bitmap, left: Int, top: Int, w: Int, h: Int, ds: Float) {
    val size = w * h
    if (pixels.size < size) pixels = IntArray(size)
    if (scratch.size < size) scratch = IntArray(size)
    src.getPixels(pixels, 0, w, left, top, w, h)
    val smallSigma = blurRadiusPx / ds
    if (smallSigma > 0.5f) {
      boxBlur3(pixels, scratch, w, h, boxRadiusFor(smallSigma))
    }
    // On a size change allocate a new bitmap and don't recycle the old one:
    // RenderThread may still be drawing it from the previous frame. Let GC
    // collect it.
    val region =
      displayBitmap?.takeIf { !it.isRecycled && it.width == w && it.height == h }
        ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { displayBitmap = it }
    region.setPixels(pixels, 0, w, 0, 0, w, h)
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    if (capturing) return
    super.draw(canvas)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val bitmap = displayBitmap ?: return
    if (bitmap.isRecycled) return
    getLocationInWindow(tmpLoc)
    drawDst.set(
      displayWindowRect.left - tmpLoc[0],
      displayWindowRect.top - tmpLoc[1],
      displayWindowRect.right - tmpLoc[0],
      displayWindowRect.bottom - tmpLoc[1],
    )
    canvas.drawBitmap(bitmap, null, drawDst, paint)
  }

  /** The first visible SurfaceView in the window that overlaps this view. */
  private fun findSource(): SurfaceView? {
    sourceRef?.get()?.let { cached ->
      if (cached.isAttachedToWindow && cached.isShown) return cached
    }
    getLocationInWindow(tmpLoc)
    val mine = Rect(tmpLoc[0], tmpLoc[1], tmpLoc[0] + width, tmpLoc[1] + height)
    val found = searchSurfaceView(rootView, mine)
    sourceRef = found?.let { WeakReference(it) }
    return found
  }

  private fun searchSurfaceView(view: View, mine: Rect): SurfaceView? {
    if (!view.isShown) return null
    if (view is SurfaceView && view.width > 0 && view.height > 0) {
      view.getLocationInWindow(tmpLoc)
      val r =
        Rect(
          tmpLoc[0],
          tmpLoc[1],
          tmpLoc[0] + (view.width * view.scaleX).roundToInt(),
          tmpLoc[1] + (view.height * view.scaleY).roundToInt(),
        )
      if (Rect.intersects(r, mine)) return view
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        searchSurfaceView(view.getChildAt(i), mine)?.let { return it }
      }
    }
    return null
  }

  private fun ensureBitmap(current: Bitmap?, w: Int, h: Int): Bitmap {
    if (current != null && !current.isRecycled && current.width == w && current.height == h) {
      return current
    }
    current?.recycle()
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  }

  /**
   * Three box blurs approximate a Gaussian: a radius-r box has variance
   * ((2r+1)^2 - 1) / 12, three passes give σ^2 = r(r+1); solve for r.
   */
  private fun boxRadiusFor(sigma: Float): Int {
    val r = (-1.0 + sqrt(1.0 + 4.0 * sigma * sigma)) / 2.0
    return max(1, r.roundToInt())
  }

  private fun boxBlur3(buf: IntArray, tmp: IntArray, w: Int, h: Int, radius: Int) {
    repeat(3) {
      boxBlurHorizontal(buf, tmp, w, h, radius)
      boxBlurVertical(tmp, buf, w, h, radius)
    }
  }

  // Sliding-window sum; edges clamp to the outermost pixel (no dark fringe).
  private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = 2 * r + 1
    for (y in 0 until h) {
      val row = y * w
      var sa = 0
      var sr = 0
      var sg = 0
      var sb = 0
      for (i in -r..r) {
        val c = src[row + min(w - 1, max(0, i))]
        sa += c ushr 24
        sr += (c shr 16) and 0xFF
        sg += (c shr 8) and 0xFF
        sb += c and 0xFF
      }
      for (x in 0 until w) {
        dst[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
        val outC = src[row + max(0, x - r)]
        val inC = src[row + min(w - 1, x + r + 1)]
        sa += (inC ushr 24) - (outC ushr 24)
        sr += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
        sg += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
        sb += (inC and 0xFF) - (outC and 0xFF)
      }
    }
  }

  private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = 2 * r + 1
    for (x in 0 until w) {
      var sa = 0
      var sr = 0
      var sg = 0
      var sb = 0
      for (i in -r..r) {
        val c = src[min(h - 1, max(0, i)) * w + x]
        sa += c ushr 24
        sr += (c shr 16) and 0xFF
        sg += (c shr 8) and 0xFF
        sb += c and 0xFF
      }
      for (y in 0 until h) {
        dst[y * w + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
        val outC = src[max(0, y - r) * w + x]
        val inC = src[min(h - 1, y + r + 1) * w + x]
        sa += (inC ushr 24) - (outC ushr 24)
        sr += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
        sg += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
        sb += (inC and 0xFF) - (outC and 0xFF)
      }
    }
  }

  private companion object {
    // ~20 fps. The camera preview runs at 30 fps, but once blurred 20 vs 30
    // is indistinguishable, and it saves GPU copies.
    const val FRAME_INTERVAL_MS = 50L
    // Target σ (px) on the small image. Too small and the image is shrunk so
    // far that blocks show when stretched back; too large wastes work.
    const val TARGET_SMALL_SIGMA = 3f
  }
}
