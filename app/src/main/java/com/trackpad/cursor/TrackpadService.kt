package com.trackpad.cursor
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot
class TrackpadService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var cursorView: View? = null
    private var overlayView: View? = null
    private var trackpadOn = false
    private var curX = 0f; private var curY = 0f
    private var screenW = 0; private var screenH = 0
    private var lastX = 0f; private var lastY = 0f
    private var startX = 0f; private var startY = 0f
    private var startTime = 0L; private var longPressed = false
    private var dragFromX = 0f; private var dragFromY = 0f
    private var lastTwoFingerTapTime = 0L
    private var toggleZonePx = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressed = true; dragFromX = curX; dragFromY = curY; vibrate(70)
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshScreen(); curX = screenW / 2f; curY = screenH / 2f; addCursor()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); removeOverlay(); cursorView?.let { wm.removeView(it) }; super.onDestroy() }
    override fun onInterrupt() { removeOverlay() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 8) { toggleTrackpad(); vibrate(120); return true }
        return false
    }
    private fun addCursor() {
        cursorView = object : View(this) {
            private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.2f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
            private val ap = Path().apply { moveTo(3f,3f); lineTo(3f,35f); lineTo(11f,27f); lineTo(15f,40f); lineTo(21f,37f); lineTo(17f,24f); lineTo(27f,24f); close() }
            override fun onDraw(canvas: Canvas) { canvas.drawPath(ap, fp); canvas.drawPath(ap, sp) }
        }
        val p = overlayParams(36, 44).apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; x = curX.toInt(); y = curY.toInt() }
        wm.addView(cursorView, p)
    }
    private fun moveCursor(dx: Float, dy: Float) {
        curX = (curX + dx).coerceIn(0f, screenW - 36f); curY = (curY + dy).coerceIn(0f, screenH - 44f)
        val p = (cursorView?.layoutParams as? WindowManager.LayoutParams) ?: return
        p.x = curX.toInt(); p.y = curY.toInt(); wm.updateViewLayout(cursorView, p)
    }
    private fun toggleTrackpad() { if (trackpadOn) removeOverlay() else addOverlay() }
    private fun addOverlay() {
        if (trackpadOn) return; trackpadOn = true; refreshScreen()
        overlayView = object : View(this@TrackpadService) {
            private val bp = Paint().apply { color = Color.argb(25,30,140,255); style = Paint.Style.FILL }
            private val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180,30,30,30); style = Paint.Style.FILL }
            private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            override fun onDraw(canvas: Canvas) {
                canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),bp)
                val cx = width - toggleZonePx/2f; val cy = height - toggleZonePx/2f
                canvas.drawCircle(cx,cy,toggleZonePx/2f-6f,cp); canvas.drawText("X",cx,cy+tp.textSize/3f,tp)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean { handleTouch(event); return true }
        }
        wm.addView(overlayView, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
    }
    private fun removeOverlay() { if (!trackpadOn) return; trackpadOn = false; handler.removeCallbacks(longPressRunnable); overlayView?.let { wm.removeView(it) }; overlayView = null }
    private fun handleTouch(event: MotionEvent) {
        val x = event.x; val y = event.y
        val w = overlayView?.width?.toFloat() ?: screenW.toFloat(); val h = overlayView?.height?.toFloat() ?: screenH.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=x; startY=y; lastX=x; lastY=y; startTime=System.currentTimeMillis(); longPressed=false; handler.postDelayed(longPressRunnable,580L) }
            MotionEvent.ACTION_POINTER_DOWN -> { handler.removeCallbacks(longPressRunnable) }
            MotionEvent.ACTION_MOVE -> { if (event.pointerCount==1) { val dx=x-lastX; val dy=y-lastY; if (!longPressed && hypot(x-startX,y-startY)>18f) handler.removeCallbacks(longPressRunnable); moveCursor(dx*2.8f,dy*2.8f); lastX=x; lastY=y } }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val dt=System.currentTimeMillis()-startTime; val moved=hypot(x-startX,y-startY)
                if (x>w-toggleZonePx && y>h-toggleZonePx && moved<18f) { removeOverlay(); return }
                when { longPressed && moved>18f -> performDrag(dragFromX,dragFromY,curX,curY); longPressed -> performLongPress(); dt<220L && moved<18f -> performTap() }
                longPressed=false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount==2) { val now=System.currentTimeMillis(); val gap=now-lastTwoFingerTapTime
                    if (gap<320L && gap>40) { performLongPress(); lastTwoFingerTapTime=0 } else { performTap(); lastTwoFingerTapTime=now } }
            }
        }
    }
    private fun performTap() { val p=Path().apply{moveTo(curX,curY)}; dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,60)).build(),null,null) }
    private fun performLongPress() { val p=Path().apply{moveTo(curX,curY)}; dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,950)).build(),null,null) }
    private fun performDrag(fx:Float,fy:Float,tx:Float,ty:Float) { val p=Path().apply{moveTo(fx,fy);lineTo(tx,ty)}; dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,100,600)).build(),null,null) }
    private fun overlayParams(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START}
    private fun refreshScreen() { val d=Point(); wm.defaultDisplay.getRealSize(d); screenW=d.x; screenH=d.y; toggleZonePx=72*resources.displayMetrics.density }
    private fun vibrate(ms:Long) { val v=getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?:return; if(Build.VERSION.SDK_INT>=26) v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(ms) }
}
