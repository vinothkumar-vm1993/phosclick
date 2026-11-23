package dev.zeppcamera.poc

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Icon-led connection help; short visible labels retain accessible action names. */
object ConnectionPanel {
    fun show(context:Context,connected:Boolean,onConnect:()->Unit,onForget:()->Unit,onHelp:(()->Unit)?=null){
        fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),dp(24),dp(16))}
        val foreground=MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurface,Color.WHITE)
        val muted=MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurfaceVariant,Color.LTGRAY)
        val dialog=MaterialAlertDialogBuilder(context).setView(root).create()
        val top=FrameLayout(context)
        top.addView(TextView(context).apply{text=if(connected)"Connected" else "Connect watch";textSize=22f;setTextColor(foreground);gravity=Gravity.CENTER_VERTICAL;typeface=android.graphics.Typeface.DEFAULT_BOLD},FrameLayout.LayoutParams(-1,dp(48)).apply{rightMargin=dp(48)})
        top.addView(ImageButton(context).apply{setImageResource(R.drawable.ic_action_close);imageTintList=android.content.res.ColorStateList.valueOf(muted);setBackgroundColor(Color.TRANSPARENT);setPadding(dp(14),dp(14),dp(14),dp(14));contentDescription="Close";setOnClickListener{dialog.dismiss()}},FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END))
        root.addView(top)
        val devices=LinearLayout(context).apply{gravity=Gravity.CENTER;setPadding(0,dp(16),0,dp(12));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
        fun device(icon:Int){devices.addView(ImageView(context).apply{setImageResource(icon);imageTintList=android.content.res.ColorStateList.valueOf(foreground);setPadding(dp(12),dp(12),dp(12),dp(12))},LinearLayout.LayoutParams(dp(80),dp(80)))}
        device(R.drawable.ic_action_phone)
        devices.addView(ImageView(context).apply{setImageResource(R.drawable.ic_action_link);imageTintList=android.content.res.ColorStateList.valueOf(muted);setPadding(dp(8),dp(8),dp(8),dp(8))},LinearLayout.LayoutParams(dp(48),dp(48)).apply{setMargins(dp(8),0,dp(8),0)})
        device(R.drawable.ic_action_watch);root.addView(devices)
        root.addView(TextView(context).apply{text=if(connected)"Via Zepp · Keep camera open" else "Open PhosClick on your watch";textSize=16f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,0,0,dp(20))},LinearLayout.LayoutParams(-1,-2))
        root.addView(MaterialButton(context).apply{text=if(connected)"Disconnect" else "Connect";isAllCaps=false;textSize=17f;cornerRadius=dp(20);setIconResource(R.drawable.ic_action_link);iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START;contentDescription=if(connected)"Disconnect watch" else "Connect watch";setOnClickListener{dialog.dismiss();onConnect()}},LinearLayout.LayoutParams(-1,dp(60)))
        val secondary=LinearLayout(context)
        if(onHelp!=null)secondary.addView(MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply{text="Help";strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(Color.TRANSPARENT);isAllCaps=false;setTextColor(muted);contentDescription="Connection help";setOnClickListener{dialog.dismiss();onHelp()}},LinearLayout.LayoutParams(0,dp(48),1f))
        secondary.addView(MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply{text="Forget access";strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(Color.TRANSPARENT);isAllCaps=false;textSize=14f;setTextColor(muted);contentDescription="Forget watch access";setOnClickListener{dialog.dismiss();onForget()}},LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(secondary,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(8)})
        dialog.show()
    }
}
