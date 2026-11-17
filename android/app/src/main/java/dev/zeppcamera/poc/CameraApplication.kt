package dev.zeppcamera.poc
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
class CameraApplication:Application(){override fun onCreate(){super.onCreate();AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)}}
