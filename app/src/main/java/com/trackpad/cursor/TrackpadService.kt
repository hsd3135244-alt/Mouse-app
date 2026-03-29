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
    private var toggleBtnView: View? = null
    private var trackpadOn = false
    private var curX = 0f; private var curY = 0f
    private var screenW = 0; private var screenH = 0
    private var lastX = 0f; private var lastY = 0f
    private var startX = 0f; private var startY = 0f
    private var startTime = 0L; private var longPressed = false
    private var dragFromX = 0f; private var dragFromY = 0f
    private var lastTwoFingerTapTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressed = true; dragFromX = curX; dragFromY = curY; vibrate(70)
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshScreen(); curX = screenW / 2f; curY = screenH / 2f
        addCursor(); addToggleButton()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        cursorView?.let { try { wm.removeView(it) } catch(e:Exception){} }
        toggleBtnView?.let { try { wm.removeView(it) } catch(e:Exception){} }
        super.onDestroy()
    }
    override fun onInterrupt() { removeOverlay() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    private fun addToggleButton() {
        toggleBtnView = object : View(this@TrackpadService) {
            private val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220,20,90,255); style = Paint.Style.FILL }
            private val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            override fun onDraw(canvas: Canvas) {
                canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),20f,20f,bgP)
                bgP.color = if(trackpadOn) Color.argb(220,0,150,50) else Color.argb(220,20,90,255)
                canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),20f,20f,bgP)
                canvas.drawText(if(trackpadOn) "PAD ON" else "PAD OFF", width/2f, height/2f+tP.textSize/3f, tP)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if(e.action == MotionEvent.ACTION_UP) { toggleTrackpad(); invalidate() }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(210, 80,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END; x = 24; y = 120
        }
        wm.addView(toggleBtnView, lp)
    }
    private fun addCursor() {
        cursorView = object : View(this) {
            private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
            private val ap = Path().apply { moveTo(3f,3f); lineTo(3f,35f); lineTo(11f,27f); lineTo(15f,40f); lineTo(21f,37f); lineTo(17f,24f); lineTo(27f,24f); close() }
            override fun onDraw(canvas: Canvas) { canvas.drawPath(ap,fp); canvas.drawPath(ap,sp) }
        }
        val lp = WindowManager.LayoutParams(36,44,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = curX.toInt(); y = curY.toInt() }
        wm.addView(cursorView, lp)
    }
    private fun moveCursor(dx: Float, dy: Float) {
        curX = (curX+dx).coerceIn(0f, screenW-36f)
        curY = (curY+dy).coerceIn(0f, screenH-44f)
        val lp = cursorView?.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = curX.toInt(); lp.y = curY.toInt(); wm.updateViewLayout(cursorView, lp)
    }
    private fun toggleTrackpad() { if(trackpadOn) removeOverlay() else addOverlay() }
    private fun addOverlay() {
        if(trackpadOn) return; trackpadOn = true; refreshScreen()
        overlayView = object : View(this@TrackpadService) {
            private val bp = Paint().apply { color = Color.argb(20,30,140,255) }
            override fun onDraw(canvas: Canvas) { canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),bp) }
            override fun onTouchEvent(e: MotionEvent): Boolean { handleTouch(e); return true }
        }
        // Leave bottom 200px free for navigation gestures
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            screenH - 200,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(overlayView, lp)
    }
    private fun removeOverlay() {
        if(!trackpadOn) return; trackpadOn = false
        handler.removeCallbacks(longPressRunnable)
        overlayView?.let { try { wm.removeView(it) } catch(e:Exception){} }; overlayView = null
    }
    private fun handleTouch(e: MotionEvent) {
        val x = e.x; val y = e.y
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX=x; startY=y; lastX=x; lastY=y
                startTime=System.currentTimeMillis(); longPressed=false
                handler.postDelayed(longPressRunnable, 600L)
            }
            MotionEvent.ACTION_POINTER_DOWN -> handler.removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_MOVE -> {
                if(e.pointerCount==1) {
                    val dx=x-lastX; val dy=y-lastY
                    if(!longPressed && hypot(x-startX,y-startY)>20f) handler.removeCallbacks(longPressRunnable)
                    moveCursor(dx*2.8f, dy*2.8f)
                    lastX=x; lastY=y
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val dt=System.currentTimeMillis()-startTime
                val moved=hypot(x-startX,y-startY)
                when {
                    longPressed && moved>20f -> performDrag(dragFromX,dragFromY,curX,curY)
                    longPressed -> performLongPress()
                    dt<200L && moved<20f -> performTap()
                }
                longPressed=false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if(e.pointerCount==2) {
                    val now=System.currentTimeMillis(); val gap=now-lastTwoFingerTapTime
                    if(gap<300L && gap>40) { performLongPress(); lastTwoFingerTapTime=0 }
                    else { performTap(); lastTwoFingerTapTime=now }
                }
            }
        }
    }
    private fun performTap() {
        val path=Path().apply{moveTo(curX,curY)}
        val stroke=GestureDescription.StrokeDescription(path,0,100)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),null,null)
    }
    private fun performLongPress() {
        val path=Path().apply{moveTo(curX,curY)}
        val stroke=GestureDescription.StrokeDescription(path,0,1000)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),null,null)
    }
    private fun performDrag(fx:Float,fy:Float,tx:Float,ty:Float) {
        val path=Path().apply{moveTo(fx,fy);lineTo(tx,ty)}
        val stroke=GestureDescription.StrokeDescription(path,0,500)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),null,null)
    }
    private fun refreshScreen() {
        val d=Point(); wm.defaultDisplay.getRealSize(d); screenW=d.x; screenH=d.y
    }
    private fun vibrate(ms:Long) {
        val v=getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?:return
        if(Build.VERSION.SDK_INT>=26) v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }
}
