package dev.zeppcamera.poc

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

/** Stable camera input, with a detachable GPU display. Locking destroys only the display. */
class CameraPreviewRenderer(private val main:Executor,private val firstFrame:()->Unit):Preview.SurfaceProvider {
    private val thread=HandlerThread("CameraPreviewGL").apply{start()}
    private val gl=Handler(thread.looper)
    private var display=EGL14.EGL_NO_DISPLAY
    private var context=EGL14.EGL_NO_CONTEXT
    private var config:EGLConfig?=null
    private var pbuffer=EGL14.EGL_NO_SURFACE
    private var window=EGL14.EGL_NO_SURFACE
    private var windowSurface:Surface?=null
    private var outputWidth=1;private var outputHeight=1
    @Volatile private var inputWidth=1;@Volatile private var inputHeight=1
    private var input:SurfaceTexture?=null;private var cameraSurface:Surface?=null;private var tex=0
    private var program=0;private var positionHandle=0;private var uvHandle=0;private var transformHandle=0;private var samplerHandle=0
    private val drawCoords=FloatArray(8)
    private var coordinateWidth=0;private var coordinateHeight=0;private var coordinatePolicy:PreviewOrientation?=null
    @Volatile var orientationState=PreviewOrientation.resolve(90,false,true);private set
    private var frameSeen=false;private var closed=false
    private val matrix=FloatArray(16)
    private val vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
    private val coordinates=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    init{gl.post{initGL()}}
    private fun initGL(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(display!=EGL14.EGL_NO_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),1))
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0));config=configs[0]!!
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        pbuffer=EGL14.eglCreatePbufferSurface(display,config,intArrayOf(EGL14.EGL_WIDTH,320,EGL14.EGL_HEIGHT,240,EGL14.EGL_NONE),0)
        current(pbuffer)
        fun shader(type:Int,source:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)};return s}
        val vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos;attribute vec2 uv;uniform mat4 transform;varying vec2 coord;void main(){gl_Position=vec4(pos,0.,1.);coord=(transform*vec4(uv,0.,1.)).xy;}")
        val fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 coord;void main(){gl_FragColor=texture2D(camera,coord);}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
        positionHandle=GLES20.glGetAttribLocation(program,"pos");uvHandle=GLES20.glGetAttribLocation(program,"uv");transformHandle=GLES20.glGetUniformLocation(program,"transform");samplerHandle=GLES20.glGetUniformLocation(program,"camera")
    }
    private fun current(surface:EGLSurface){check(EGL14.eglMakeCurrent(display,surface,surface,context))}
    fun uprightInputAspect():Float=orientationState.uprightAspect(inputWidth,inputHeight)
    fun orientation(degrees:Int,front:Boolean,hasCameraTransform:Boolean=true){gl.post{orientationState=PreviewOrientation.resolve(degrees,front,hasCameraTransform)}}
    override fun onSurfaceRequested(request:SurfaceRequest){
        request.setTransformationInfoListener(main){info->orientation(info.rotationDegrees,info.isMirroring,info.hasCameraTransform())}
        input(request.resolution.width,request.resolution.height){surface->request.provideSurface(surface,main){releaseInput(surface)}}
    }
    fun input(width:Int,height:Int,done:(Surface)->Unit){gl.post{
        if(closed)return@post
        current(pbuffer);releaseInputNow();coordinatePolicy=null;frameSeen=false;inputWidth=width;inputHeight=height
        val names=IntArray(1);GLES20.glGenTextures(1,names,0);tex=names[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        val st=SurfaceTexture(tex);input=st;st.setDefaultBufferSize(width,height)
        st.setOnFrameAvailableListener({if(input===st&&!closed){current(if(window!=EGL14.EGL_NO_SURFACE)window else pbuffer);st.updateTexImage();st.getTransformMatrix(matrix);if(!frameSeen){frameSeen=true;main.execute(firstFrame)};if(window!=EGL14.EGL_NO_SURFACE){draw(outputWidth,outputHeight);EGL14.eglSwapBuffers(display,window)}}},gl)
        val surface=Surface(st);cameraSurface=surface;main.execute{done(surface)}
    }}
    private fun releaseInput(surface:Surface){gl.post{if(cameraSurface===surface){current(pbuffer);releaseInputNow()}else surface.release()}}
    private fun releaseInputNow(){input?.setOnFrameAvailableListener(null);cameraSurface?.release();cameraSurface=null;input?.release();input=null;if(tex!=0){GLES20.glDeleteTextures(1,intArrayOf(tex),0);tex=0}}
    fun attach(target:SurfaceTexture,width:Int,height:Int){gl.post{
        if(closed)return@post
        detachNow();windowSurface=Surface(target);window=EGL14.eglCreateWindowSurface(display,config,windowSurface,intArrayOf(EGL14.EGL_NONE),0);outputWidth=width;outputHeight=height
        if(window==EGL14.EGL_NO_SURFACE){windowSurface?.release();windowSurface=null}
    }}
    fun detach(released:(()->Unit)?=null){gl.post{if(!closed)detachNow();released?.invoke()}}
    private fun detachNow(){current(pbuffer);if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);window=EGL14.EGL_NO_SURFACE;windowSurface?.release();windowSurface=null}
    private fun draw(width:Int,height:Int){
        GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if(!frameSeen)return
        val policy=orientationState
        if(coordinateWidth!=width||coordinateHeight!=height||coordinatePolicy!=policy){
        val coords=drawCoords;coords[0]=0f;coords[1]=0f;coords[2]=1f;coords[3]=0f;coords[4]=0f;coords[5]=1f;coords[6]=1f;coords[7]=1f
        for(i in 0..3){var x=coords[i*2]-.5f;var y=coords[i*2+1]-.5f;val imageAspect=policy.uprightAspect(inputWidth,inputHeight);val viewAspect=width.toFloat()/height;if(imageAspect>viewAspect)x*=viewAspect/imageAspect else y*=imageAspect/viewAspect;if(policy.extraMirror)x=-x;repeat(policy.extraRotation/90){val old=x;x=y;y=-old};coords[i*2]=x+.5f;coords[i*2+1]=y+.5f}
        coordinates.clear();coordinates.put(coords);coordinates.position(0);coordinateWidth=width;coordinateHeight=height;coordinatePolicy=policy
        };coordinates.position(0);vertices.position(0)
        GLES20.glUseProgram(program);val p=positionHandle;val uv=uvHandle
        GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glEnableVertexAttribArray(uv);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,coordinates)
        GLES20.glUniformMatrix4fv(transformHandle,1,false,matrix,0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);GLES20.glUniform1i(samplerHandle,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }
    fun snapshot(done:(Bitmap?)->Unit){gl.post{
        if(closed||!frameSeen){main.execute{done(null)};return@post}
        current(pbuffer);draw(320,240);val buffer=ByteBuffer.allocateDirect(320*240*4).order(ByteOrder.nativeOrder());GLES20.glReadPixels(0,0,320,240,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
        val pixels=IntArray(320*240);for(y in 0 until 240)for(x in 0 until 320){val i=(y*320+x)*4;pixels[(239-y)*320+x]=ColorPack.argb(buffer.get(i).toInt()and 255,buffer.get(i+1).toInt()and 255,buffer.get(i+2).toInt()and 255)}
        val image=Bitmap.createBitmap(pixels,320,240,Bitmap.Config.ARGB_8888);main.execute{done(image)}
    }}
    fun close(){gl.post{if(closed)return@post;closed=true;detachNow();releaseInputNow();GLES20.glDeleteProgram(program);EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,pbuffer);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);thread.quitSafely()}}
    private object ColorPack{fun argb(r:Int,g:Int,b:Int)=(-0x1000000)or(r shl 16)or(g shl 8)or b}
}
