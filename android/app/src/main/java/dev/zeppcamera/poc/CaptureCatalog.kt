package dev.zeppcamera.poc

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

data class SavedCapture(val uri:Uri,val name:String,val mime:String,val kind:String,val date:Long,val duration:Long,val modified:Long=0){val video get()=mime.startsWith("video/")}
class CaptureCatalog(private val context:Context){
    // Preserve existing captures through the WristSnap rename without moving/deleting user media.
    private val mediaPaths=arrayOf("Pictures/WristSnap/","Movies/WristSnap/","Pictures/ActionCamera/","Movies/ActionCamera/")
    private val prefs=context.getSharedPreferences("media",Context.MODE_PRIVATE)
    fun record(uri:Uri,kind:String){
        if(uri==Uri.EMPTY)return
        prefs.edit().putString("lastMedia",uri.toString()).putString("kind:${uri.lastPathSegment}",kind).apply()
    }
    fun lastUri():Uri?=prefs.getString("lastMedia",prefs.getString("lastPhoto",null))?.let(Uri::parse)
    fun latest(photoOnly:Boolean=false):Uri?{
        val type=MediaStore.Files.FileColumns.MEDIA_TYPE
        val selection="($type=? OR $type=?) AND ${MediaStore.MediaColumns.RELATIVE_PATH} IN (?,?,?,?) AND ${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.IS_TRASHED}=0"
        val args=arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),(if(photoOnly)MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE else MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO).toString())+mediaPaths
        return context.contentResolver.query(MediaStore.Files.getContentUri("external"),arrayOf(MediaStore.MediaColumns._ID,type),selection,args,"${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC")?.use{c->
            if(!c.moveToFirst())null else ContentUris.withAppendedId(if(c.getInt(1)==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0))
        }
    }
    fun reconcile():Pair<Uri?,Uri?>{
        val media=latest();val photo=latest(true)
        prefs.edit().apply{if(media==null)remove("lastMedia")else putString("lastMedia",media.toString());if(photo==null)remove("lastPhoto")else putString("lastPhoto",photo.toString())}.apply()
        return media to photo
    }
    fun exists(uri:Uri):Boolean=try{
        context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns._ID),"${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.IS_TRASHED}=0",null,null)?.use{it.moveToFirst()}?:false
    }catch(_:Exception){false}
    fun thumbnail(uri:Uri,cancel:CancellationSignal?=null):Bitmap=context.contentResolver.loadThumbnail(uri,Size(360,360),cancel)
    fun capture(uri:Uri):SavedCapture?{
        if(!exists(uri))return null
        return context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.DATE_ADDED,MediaStore.Video.Media.DURATION,MediaStore.MediaColumns.DATE_MODIFIED),null,null,null)?.use{c->
            if(!c.moveToFirst())null else {val name=c.getString(0)?:"Capture";val mime=c.getString(1)?:return@use null;SavedCapture(uri,name,mime,MediaKinds.classify(mime,name,prefs.getString("kind:${uri.lastPathSegment}",null)),c.getLong(2)*1000,c.getLong(3),c.getLong(4))}
        }
    }
    fun all(limit:Int=Int.MAX_VALUE):List<SavedCapture>{
        require(limit>0)
        val out=mutableListOf<SavedCapture>()
        val projection=arrayOf(MediaStore.Files.FileColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.DATE_ADDED,MediaStore.Video.Media.DURATION,MediaStore.Files.FileColumns.MEDIA_TYPE,MediaStore.MediaColumns.DATE_MODIFIED)
        val where="(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?) AND ${MediaStore.MediaColumns.RELATIVE_PATH} IN (?,?,?,?) AND ${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.IS_TRASHED}=0"
        val args=arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())+mediaPaths
        context.contentResolver.query(MediaStore.Files.getContentUri("external"),projection,where,args,"${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC")?.use{cursor->
            while(out.size<limit&&cursor.moveToNext()){
                val id=cursor.getLong(0);val name=cursor.getString(1)?:"Capture";val mime=cursor.getString(2)?:continue;val video=cursor.getInt(5)==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val uri=ContentUris.withAppendedId(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id)
                var stored=prefs.getString("kind:$id",null)
                if(video&&stored==null&&!name.removePrefix("WristSnap-").startsWith("timelapse-",true)&&!name.removePrefix("WristSnap-").startsWith("SlowMotion-",true)){
                    var retriever:MediaMetadataRetriever?=null
                    try{retriever=MediaMetadataRetriever();retriever.setDataSource(context,uri);val captureFps=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull();if(captureFps!=null){stored=MediaKinds.classify(mime,name,null,captureFps);prefs.edit().putString("kind:$id",stored).apply()}}catch(_:Exception){}finally{retriever?.release()}
                }
                out+=SavedCapture(uri,name,mime,MediaKinds.classify(mime,name,stored),cursor.getLong(3)*1000,cursor.getLong(4),cursor.getLong(6))
            }
        }
        return out
    }
}
