package dev.zeppcamera.poc

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object QuickStart {
    fun seen(context:Context)=context.getSharedPreferences("guide",Context.MODE_PRIVATE).getBoolean("seen_v1",false)
    fun show(context:Context,finished:()->Unit){
        fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(24),dp(24),dp(16))}
        val color=MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurface,Color.WHITE)
        root.addView(TextView(context).apply{text="Quick start";textSize=24f;setTextColor(color);typeface=android.graphics.Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(-1,dp(48)))
        listOf(R.drawable.ic_action_phone to "Open phone app",R.drawable.ic_action_watch to "Open watch app",R.drawable.ic_action_permissions to "Allow connection").forEach{(icon,label)->
            val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
            row.addView(ImageView(context).apply{setImageResource(icon);imageTintList=android.content.res.ColorStateList.valueOf(color);setPadding(dp(12),dp(12),dp(12),dp(12));importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(56),dp(56)))
            row.addView(TextView(context).apply{text=label;textSize=17f;setTextColor(color);setPadding(dp(12),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
            root.addView(row,LinearLayout.LayoutParams(-1,dp(64)))
        }
        root.addView(TextView(context).apply{text="Via Zepp · No extra pairing";textSize=14f;gravity=Gravity.CENTER;setTextColor(MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurfaceVariant,Color.LTGRAY));setPadding(0,dp(8),0,dp(16))},LinearLayout.LayoutParams(-1,-2))
        val dialog=MaterialAlertDialogBuilder(context).setView(root).create()
        val actions=LinearLayout(context)
        actions.addView(MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply{text="Skip";isAllCaps=false;contentDescription="Skip quick start";setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(0,dp(56),1f).apply{marginEnd=dp(8)})
        actions.addView(MaterialButton(context).apply{text="Continue";isAllCaps=false;contentDescription="Continue quick start";setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(0,dp(56),1f))
        root.addView(actions);dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener{context.getSharedPreferences("guide",Context.MODE_PRIVATE).edit().putBoolean("seen_v1",true).apply();finished()};dialog.show()
    }
}
