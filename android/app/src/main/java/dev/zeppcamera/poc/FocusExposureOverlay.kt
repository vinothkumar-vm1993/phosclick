package dev.zeppcamera.poc

import android.content.Context
import android.graphics.*
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/** Contextual focus ring and sun handle, never a permanent camera control. */
class FocusExposureOverlay(context:Context):View(context){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val handler=Handler(Looper.getMainLooper())
 private var cx=0f;private var cy=0f;private var barX=0f;private var trackTop=0f
 private var minimum=0;private var maximum=0;private var index=0;private var step=0f
 private var dragging=false;private var downY=0f;private var downIndex=0;private var focused:Boolean?=null
 var onExposure:((Int)->Unit)?=null
 private val hide=Runnable{animate().alpha(0f).setDuration(160).withEndAction{visibility=GONE}.start()}
 private fun dp(n:Float)=n*resources.displayMetrics.density
 init{visibility=GONE;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
 fun showAt(x:Float,y:Float,min:Int,max:Int,current:Int,evStep:Float){
  animate().cancel();handler.removeCallbacks(hide);minimum=min;maximum=max;index=current.coerceIn(min,max);step=evStep;focused=null
  val host=parent as? View;val availableWidth=maxOf(width,host?.width?:0);val availableHeight=maxOf(height,host?.height?:0)
  cx=x.coerceIn(dp(25f),maxOf(dp(25f),availableWidth-dp(25f)));cy=y.coerceIn(dp(25f),maxOf(dp(25f),availableHeight-dp(25f)))
  barX=if(cx+dp(78f)<availableWidth)cx+dp(48f)else cx-dp(48f);trackTop=(cy-dp(60f)).coerceIn(dp(16f),maxOf(dp(16f),availableHeight-dp(136f)))
  alpha=0f;visibility=VISIBLE;animate().alpha(1f).setDuration(120).start();describe();invalidate();handler.postDelayed(hide,4000)
 }
 fun focusResult(success:Boolean){if(focused==success)return;focused=success;describe();invalidate()}
 fun updateExposure(current:Int){if(!dragging){val value=current.coerceIn(minimum,maximum);if(value!=index){index=value;describe();invalidate()}}}
 fun hideNow(){handler.removeCallbacks(hide);animate().cancel();dragging=false;visibility=GONE}
 private fun describe(){contentDescription=if(maximum>minimum)"Focus and exposure, %+.1f EV. Swipe sun handle up or down to adjust brightness".format(index*step)else if(focused==true)"Metering set"else "Focus point"}
 override fun onDraw(canvas:Canvas){super.onDraw(canvas);paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1.5f);paint.color=if(focused==true)0xffffd65a.toInt()else Color.WHITE;paint.setShadowLayer(dp(2f),0f,0f,Color.BLACK)
  val r=dp(23f);canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,dp(4f),dp(4f),paint)
  if(maximum>minimum){val bottom=trackTop+dp(120f);paint.strokeWidth=dp(1f);paint.color=0xaaffffff.toInt();canvas.drawLine(barX,trackTop,barX,bottom,paint)
   val y=bottom-(index-minimum).toFloat()/(maximum-minimum)*dp(120f);paint.color=0xffffd65a.toInt();paint.style=Paint.Style.FILL;canvas.drawCircle(barX,y,dp(8f),paint);paint.style=Paint.Style.STROKE
   for(i in 0..7){val angle=Math.PI*i/4;canvas.drawLine(barX+kotlin.math.cos(angle).toFloat()*dp(11f),y+kotlin.math.sin(angle).toFloat()*dp(11f),barX+kotlin.math.cos(angle).toFloat()*dp(15f),y+kotlin.math.sin(angle).toFloat()*dp(15f),paint)}
   val zero=bottom-(0-minimum).toFloat()/(maximum-minimum)*dp(120f);canvas.drawLine(barX-dp(4f),zero,barX+dp(4f),zero,paint)
  };paint.clearShadowLayer()
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{
  if(visibility!=VISIBLE||maximum<=minimum)return false
  if(e.actionMasked==MotionEvent.ACTION_DOWN){if(kotlin.math.abs(e.x-barX)>dp(24f)||e.y<trackTop-dp(24f)||e.y>trackTop+dp(144f))return false;dragging=true;downY=e.y;downIndex=index;handler.removeCallbacks(hide);parent?.requestDisallowInterceptTouchEvent(true);return true}
  if(!dragging)return false
  if(e.pointerCount>1){dragging=false;parent?.requestDisallowInterceptTouchEvent(false);handler.postDelayed(hide,2000);return true}
  when(e.actionMasked){MotionEvent.ACTION_MOVE->{val next=(downIndex+(downY-e.y)/dp(120f)*(maximum-minimum)).roundToInt().coerceIn(minimum,maximum);if(next!=index){index=next;onExposure?.invoke(index);describe();invalidate()}}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{dragging=false;parent?.requestDisallowInterceptTouchEvent(false);handler.postDelayed(hide,3000);performClick()}}
  return true
 }
 override fun performClick():Boolean{super.performClick();return true}
 override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);if(maximum>minimum){info.className="android.widget.SeekBar";info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,minimum.toFloat(),maximum.toFloat(),index.toFloat());info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}}
 override fun performAccessibilityAction(action:Int,args:Bundle?):Boolean{if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){index=(index+if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1).coerceIn(minimum,maximum);onExposure?.invoke(index);describe();invalidate();return true};return super.performAccessibilityAction(action,args)}
 override fun onDetachedFromWindow(){hideNow();super.onDetachedFromWindow()}
}
