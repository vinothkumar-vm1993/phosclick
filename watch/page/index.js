import * as ui from '@zos/ui'
import { Vibrator } from '@zos/sensor'
import { LocalStorage } from '@zos/storage'
import { home } from '@zos/router'
import { setWakeUpRelaunch,setPageBrightTime,resetPageBrightTime } from '@zos/display'
import { onGesture,offGesture,GESTURE_UP,GESTURE_DOWN,GESTURE_LEFT,GESTURE_RIGHT } from '@zos/interaction'
import { BasePage } from '@zeppos/zml/base-page'
import { MODES,modeAfter,elapsed,nextValue,cameraState,noticeText } from '../shared/model'
let seq=0
Page(BasePage({
 onInit(){
  this.nav=[];this.historyGeneration=0;this.history=[];this.historyIndex=0;this.historyActive=false;this.historyLoading=false;this.introStep=0;this.settingPage=0;this.thumbnail=null;this.thumbnailKey='';this.thumbnailFailedKey=''
  try{setWakeUpRelaunch({relaunch:true})}catch(e){}
  try{this.guideStore=new LocalStorage();this.introVisible=!this.guideStore.getItem("guide_v1",false)}catch(e){this.introVisible=true}
  this.connectionHapticPlayed=false;this.alive=true;this.widgets=[];this.paired=false;this.panel='connection';this.busy=false;this.photoFirst=true;this.initializing=false;this.revision=0;this.statusFailures=0
  this.camera={mode:'Photo',quality:'HD',fps:30,timer:0,aspect:'4:3',photoSize:'Auto',flash:'OFF',torch:false,recording:false,ready:false}
  this.notice='Open WristSnap on phone';this.connected=false;this.media=null;this.hapticStarts=0;this.touchUps=0
  // Optional device features must never prevent the camera page from opening.
  try{this.vibrator=new Vibrator()}catch(e){}

 },
 text(x,y,w,h,size,text,color=0xffffff){const a=ui.createWidget(ui.widget.TEXT,{x,y,w,h,text:String(text??'—'),text_size:size,color,text_style:ui.text_style.WRAP,align_h:ui.align.CENTER_H});this.widgets.push(a);return a},
 button(x,y,w,h,label,action,color=0x22272e){const a=ui.createWidget(ui.widget.BUTTON,{x,y,w,h,text:String(label??'—'),text_size:24,color:0xffffff,normal_color:color,press_color:0x505760,radius:Math.min(20,h/2),click_func:action});this.widgets.push(a);return a},
 icon(x,y,name,action,size=72){const file=name+(size===96?'96':'');const a=ui.createWidget(ui.widget.BUTTON,{x,y,w:size,h:size,text:'',normal_src:file+'.png',press_src:file+'-pressed.png',click_func:action,longpress_func:()=>{this.hintReturn=this.panel;this.hintText={'flash-off':'Photo flash Off / Auto / On, or video torch', 'flash-auto':'Photo flash is automatic', 'flash-on':'Photo flash is on', 'torch-on':'Video torch is on; tap to turn off',gallery:'Left: older capture\nRight: newer capture',snapshot:'Request a still camera frame',switch:'Switch front or rear camera',connect:'Watch 0.6.19 · Zepp '+(this.bridgeVersion||'?')+' · phone '+(this.phoneVersion||'?'),settings:'Capture settings',shutter:'Take a photo',record:'Start recording',stop:'Stop and save recording',back:'Back',next:'Next page'}[name]||'Tap to select';this.navigate('hint')}});this.widgets.push(a);return a},
 connectionHaptic(){
  if(this.connectionHapticPlayed)return
  this.connectionHapticPlayed=true
  if(this.phoneSessionId){
   try{if(this.guideStore?.getItem('haptic_session_v1')===this.phoneSessionId)return;this.guideStore?.setItem('haptic_session_v1',this.phoneSessionId)}catch(e){}
  }
  try{
   clearTimeout(this.hapticStopTimer);this.vibrator.stop()
   this.vibrator.start([{type:this.vibrator.getType().GENTLE_SHORT,duration:12}])
   this.hapticStopTimer=setTimeout(()=>{try{this.vibrator.stop()}catch(e){}},18)
  }catch(e){}
 },
 captureHaptic(){
  try{
   const type=this.vibrator.getType().GENTLE_SHORT
   clearTimeout(this.hapticStopTimer);this.vibrator.stop()
   this.vibrator.start([{type,duration:12}]);this.hapticStarts++;this.lastHapticType=type
   this.hapticStopTimer=setTimeout(()=>{try{this.vibrator.stop()}catch(e){}},18)
  }catch(e){this.hapticIssue=String(e?.message||e).slice(0,120)}
  this.diagnose('haptic')
 },
 captureControl(name,action){
  const normal=name+'.png',pressed=name+'-pressed.png'
  const w=ui.createWidget(ui.widget.IMG,{x:135,y:294,w:120,h:120,src:normal});this.widgets.push(w)
  const cancel=()=>{if(this.capturePress?.widget===w){this.capturePress=null;try{w.setProperty(ui.prop.SRC,normal)}catch(e){}}}
  w.addEventListener(ui.event.CLICK_DOWN,e=>{if(!this.alive||this.busy)return;this.capturePress={widget:w,x:e.x,y:e.y,at:Date.now(),cancel};w.setProperty(ui.prop.SRC,pressed)})
  w.addEventListener(ui.event.MOVE,e=>{const p=this.capturePress;if(p?.widget===w&&Math.hypot((e.x??p.x)-p.x,(e.y??p.y)-p.y)>14)cancel()})
  w.addEventListener(ui.event.MOVE_OUT,cancel)
  w.addEventListener(ui.event.CLICK_UP,()=>{const p=this.capturePress;if(p?.widget!==w)return;cancel();if(this.alive&&Date.now()-p.at<650&&!this.busy){this.touchUps++;this.diagnose('touch-up');action()}})
 },
 diagnose(event){
  if(!this.alive||!this.paired)return
  const info={event,watchVersion:'0.6.19',captureWidget:'IMG',hapticStarts:this.hapticStarts,touchUps:this.touchUps,vibrationScene:this.lastHapticType}
  try{info.vibrationTypes=JSON.stringify({gentle:this.vibrator?.getType?.().GENTLE_SHORT,durationMs:12,stopMs:18,issue:this.hapticIssue||''})}catch(e){}
  try{this.call({diagnostics:info}).catch(()=>{})}catch(e){}
 },
 finishIntro(){this.introVisible=false;this.introStep=0;try{this.guideStore?.setItem("guide_v1",true)}catch(e){}this.layoutSignature=null;this.draw();if(!this.paired)this.connectLocal()},
 async exitControls(){
  if(this.closing)return
  this.closing=true
  this.revision++
  this.syncTimers()
  try{setWakeUpRelaunch({relaunch:false})}catch(e){}
  try{
   if(this.paired||this.connecting)await this.request({method:'DETACH'},{timeout:1500})
  }catch(e){/* The phone expires the connection if the watch is out of reach. */}
  finally{if(this.alive){try{resetPageBrightTime()}catch(e){}home()}}
 },
 goBack(){
  if(this.panel==='exit'){this.panel=this.exitReturn?.panel||'main';this.introVisible=this.exitReturn?.intro||false;this.draw();return}
  if(this.introVisible){if(this.introStep>0){this.introStep=0;this.draw()}else this.offerExit();return}
  if(!this.paired||this.panel==='main'){this.offerExit();return}
  if(this.panel==='review'){this.historyGeneration++;this.historyActive=false;this.historyLoading=false}
  this.panel=this.nav.pop()||(this.panel==='hint'?this.hintReturn||'main':'main');this.transitionAxis=ui.prop.X;this.transition=-1;this.draw()
 },
 offerExit(){if(this.historyLoading){this.historyGeneration++;this.historyActive=false}this.exitReturn={panel:this.panel,intro:this.introVisible};this.introVisible=false;this.panel='exit';this.draw()},
 settingsTile(x,y,icon,value,action,selected=false){
  this.icon(x+37,y,icon,action)
  const w=this.button(x,y+78,146,46,value,action,0x000000)
  if(selected)w.setProperty(ui.prop.TEXT,String(value)+' ✓')
 },
 openSetting(key,icon,title,options){if(this.camera.recording||this.busy||options.length<2)return;this.settingKey=key;this.settingIcon=icon;this.settingTitle=title;this.settingOptions=options;this.settingPage=0;this.navigate('picker')},
 navigate(panel,direction=1){
  if(!this.paired){this.panel='connection';this.draw();return}
  if(panel==='main')this.nav=[]
  else if(this.nav[this.nav.length-1]===panel)this.nav.pop()
  else if(this.panel!==panel)this.nav.push(this.panel)
  this.panel=panel;this.transitionAxis=ui.prop.Y;this.transition=direction;this.draw()
 },
 animateIn(){
  if(!this.transition)return
  const direction=this.transition,axis=this.transitionAxis||ui.prop.Y;this.transition=0
  this.widgets.slice(1).forEach(w=>{try{const x=w.getProperty(axis);if(typeof x==='number'&&!(axis===ui.prop.Y&&x>=400))w.setProperty(ui.prop.ANIM,{anim_steps:[{anim_prop:axis,anim_from:x+direction*14,anim_to:x,anim_duration:180,anim_rate:'easeout'}],anim_fps:30})}catch(e){/* Native immediate layout remains usable if firmware declines an animation. */}})
 },
 syncTimers(){
  if(!this.alive)return
  const reconnect=!this.closing&&!this.paired&&!this.manualDisconnect&&!this.introVisible
  if(reconnect&&!this.connectTimer)this.connectTimer=setInterval(()=>{if(!this.connecting)this.connectLocal()},5000)
  else if(!reconnect&&this.connectTimer){clearInterval(this.connectTimer);this.connectTimer=null}
  // Follow phone mode changes while the camera controls are visible.
  const statusInterval=this.paired&&!this.closing
   ? (this.panel==='main'&&!this.introVisible ? 2000 : 10000)
   : 0
  if(statusInterval!==(this.statusInterval||0)){
   clearInterval(this.sync)
   this.statusInterval=statusInterval
   this.sync=statusInterval ? setInterval(()=>{
    if(this.alive&&!this.closing&&!this.busy&&!this.statusBusy&&!this.connecting&&!this.initializing){
     return this.send('STATUS',{},true)
    }
   },statusInterval) : null
  }
  const clock=this.paired&&(this.camera.recording||(this.captureCountdownDeadline||0)>Date.now())&&this.panel==='main'&&!this.introVisible
  if(clock&&!this.tick)this.tick=setInterval(()=>this.updateLabels(),1000)
  else if(!clock&&this.tick){clearInterval(this.tick);this.tick=null}
 },
 label(widget,key,text){if(widget&&this[key]!==text){widget.setProperty(ui.prop.TEXT,text);this[key]=text}},
 updateLabels(){
  if(!this.alive)return
  this.syncTimers()
  if(this.panel==='zoom'){this.zoomValueWidget?.setProperty(ui.prop.TEXT,(this.zoomRequested??this.camera.zoomRatio).toFixed(1)+'×');return}
  if(this.panel!=='main')return
  this.label(this.zoomChip,'lastZoomText',this.camera.zoomRatio.toFixed(1)+'×')
  const c=this.camera
  const countdown=Math.max(0,Math.ceil(((this.captureCountdownDeadline||0)-Date.now())/1000))
  const detail=countdown?String(countdown):c.recording?'● '+elapsed(c.startedAt||Date.now(),Date.now()):this.busy?(c.mode==='Photo'&&c.shotCount>1?c.shotCount+' photos…':'Please wait…'):!c.ready?'Camera loading…':c.mode==='Photo'?c.aspect+' · '+c.timer+'s':({HD:'720p',FHD:'1080p',UHD:'4K'}[c.quality]||c.quality)+' · '+c.fps+' fps'
  this.label(this.clock,'lastClockText',detail)
  this.label(this.statusWidget,'lastStatusText',({battery_low:'Phone battery low',storage_low:'Storage low',storage_critical:'Storage almost full'}[c.resourceWarning])|| (this.imageBusy&&this.expectedImageKind!=='thumbnail'?'Receiving image…':this.statusFailures?'Reconnecting…':this.busy?'':this.camera.front?'Front camera':'Rear camera'))
 },
 draw(){
  if(!this.alive)return
  this.syncTimers()
  this.camera=cameraState({},this.camera)
  if(this.thumbnail&&this.thumbnailKey!==this.camera.latestMediaKey){this.thumbnail=null;this.thumbnailKind=null}
  const signature=JSON.stringify([this.historyActive,this.historyLoading,this.historyIndex,this.camera.resourceWarning,this.camera.latestMediaKey,this.introStep,this.introVisible,this.paired,this.initializing,this.panel,this.pendingMode||this.camera.mode,this.panel==='main'?null:this.camera.mode,this.camera.recording,this.camera.flash,this.camera.torch,this.thumbnail,this.thumbnailKind,this.media,this.mediaKind,this.mediaContentKind,this.mediaReviewToken,this.hintText,this.settingKey,this.settingPage,this.settingTitle,
   this.panel==='zoom'?[this.camera.minZoom,this.camera.maxZoom]:this.panel==='settings'?[this.camera.quality,this.camera.fps,this.camera.timer,this.camera.aspect,this.camera.photoSize,this.camera.qualities,this.camera.frameRates,this.camera.photoSizes,this.camera.shotCount]:null,
   !this.paired||this.panel==='error'?this.notice:null])
  if(signature===this.layoutSignature){this.updateLabels();this.queueThumbnail();return}
  this.layoutSignature=signature
  this.capturePress?.cancel();this.widgets.forEach(w=>ui.deleteWidget(w));this.widgets=[];this.lastClockText=null;this.lastZoomText=null;this.lastStatusText=null;this.clock=null;this.statusWidget=null;this.zoomChip=null;this.zoomValueWidget=null
  this.widgets.push(ui.createWidget(ui.widget.FILL_RECT,{x:0,y:0,w:390,h:450,color:0x000000}))
  this.camera=cameraState({},this.camera)
  const displayedMode=this.pendingMode||this.camera.mode
  if(this.introVisible){
   const gestures=this.introStep===1
   this.text(40,34,310,48,30,gestures?'Camera modes':'Quick start')
   this.widgets.push(ui.createWidget(ui.widget.IMG,{x:40,y:108,w:96,h:96,src:gestures?'back96.png':'phone96.png'}))
   this.text(148,124,202,80,25,gestures?'Swipe left\nNext mode':'Open phone app')
   this.widgets.push(ui.createWidget(ui.widget.IMG,{x:40,y:222,w:96,h:96,src:gestures?'next96.png':'check96.png'}))
   this.text(148,236,202,80,25,gestures?'Swipe right\nPrevious mode':'Allow on phone')
   this.button(40,356,140,64,'Skip',()=>this.finishIntro())
   this.button(194,356,156,64,gestures?'Start':'Continue',()=>{if(gestures)this.finishIntro();else{this.introStep=1;this.draw()}},0x30363e)
   return
  }
  if(this.panel==='exit'){
   this.icon(40,28,'back',()=>this.goBack());this.text(124,46,140,42,30,'Close?')
   this.icon(147,154,'close',()=>this.exitControls(),96)
   this.text(50,274,290,42,26,'Close WristSnap')
   this.text(40,330,310,64,23,'Phone stays ready',0xa8b0bc)
   return
  }
  if(!this.paired||this.initializing){
   this.icon(40,30,'back',()=>this.goBack())
   this.icon(278,30,'settings-disabled',()=>{})
   this.text(124,45,140,38,28,'Phone',0xbac3ce)
   this.widgets.push(ui.createWidget(ui.widget.IMG,{x:147,y:124,w:96,h:96,src:'phone96.png'}))
   this.text(40,238,310,72,26,this.initializing?'Preparing Photo…':noticeText(this.notice,2))
   this.icon(159,341,'connect',()=>this.connectLocal(true))
   this.animateIn();return
  }
  if(this.panel!=='main'&&this.panel!=='review'){
   this.icon(40,28,'back',()=>this.goBack())
   this.text(124,46,140,42,30,{settings:'Settings',picker:this.settingTitle||'Settings',quick:'Controls',review:this.mediaKind==='review'?(this.mediaContentKind==='Photo'?'Photo':'Video'):'Frame',delete:'Delete?',error:'Attention',hint:'Help',connection:'Phone',zoom:'Zoom'}[this.panel]||'Camera')
  }
  if(this.panel==='connection')this.icon(278,28,'help',()=>{this.introVisible=true;this.draw()})
  if(this.panel==='error'){
   this.text(40,127,310,170,26,noticeText(this.notice,5),0xffb45b)
   this.icon(68,323,'connect',()=>{if(this.photoFirst)this.connectLocal(true);else{this.panel='main';this.send('STATUS')}},96)
   this.icon(226,323,'close',()=>this.navigate('main',-1),96)
  }else if(this.panel==='zoom'){
   this.zoomValueWidget=this.text(40,146,310,84,52,(this.zoomRequested??this.camera.zoomRatio).toFixed(1)+'×')
   this.text(40,238,310,36,22,this.camera.minZoom.toFixed(1)+'–'+this.camera.maxZoom.toFixed(1)+'×',0xa8b0bc)
   this.icon(68,306,'minus',()=>this.changeZoom(-.1),96)
   this.icon(226,306,'plus',()=>this.changeZoom(.1),96)
  }else if(this.panel==='hint'){
   this.text(40,140,310,180,28,this.hintText)
   this.icon(159,340,'check',()=>this.goBack())
  }else if(this.panel==='settings'){
   const c=this.camera,photo=c.mode==='Photo'
   const rows=[{icon:'resolution',key:photo?'photoSize':'quality',title:'Resolution',values:photo?(c.photoSizeOptions||[]).map(v=>({value:v.value,label:v.label})):(c.qualities||[]).map(v=>({value:v,label:{HD:'720p',FHD:'1080p',UHD:'4K'}[v]||v})),label:photo?c.photoSizeLabel:({HD:'720p',FHD:'1080p',UHD:'4K'}[c.quality]||c.quality)},
    ...(!photo&&c.mode!=='Timelapse'?[{icon:'fps',key:'fps',title:'FPS',values:(c.frameRates||[]).map(v=>({value:v,label:v+' fps'})),label:c.fps+' fps'}]:[]),
    {icon:'timer',key:'timer',title:'Timer',values:[0,3,10].map(v=>({value:v,label:v?v+'s':'Off'})),label:c.timer?c.timer+'s':'Off'},
    ...(photo?[{icon:'multishot',key:'shotCount',title:'Shots',values:[1,3,5].map(v=>({value:v,label:v+'×'})),label:c.shotCount+'×'},{icon:'aspect',key:'aspect',title:'Ratio',values:['4:3','16:9'].map(v=>({value:v,label:v})),label:c.aspect}]:[])]
   rows.forEach((r,i)=>{const x=rows.length===3&&i===2?122:i%2?212:32,y=i<2?120:272;this.settingsTile(x,y,r.icon,r.label||'Auto',()=>this.openSetting(r.key,r.icon,r.title,r.values))})
  }else if(this.panel==='picker'){
   const all=this.settingOptions||[],values=all.slice(this.settingPage*4,this.settingPage*4+4)
   values.forEach((r,i)=>{const x=values.length===3&&i===2?122:i%2?212:32,y=i<2?120:272,selected=String(this.camera[this.settingKey])===String(r.value)
    this.settingsTile(x,y,selected?'check':this.settingIcon,r.label,()=>{if(this.busy||this.camera.recording)return;if(selected){this.goBack();return}this.configure({[this.settingKey]:r.value})?.then(reply=>{if(reply?.ok&&this.alive&&this.panel==='picker')this.goBack()})})
   })
   if(all.length>4)this.icon(278,28,'next',()=>{this.settingPage=(this.settingPage+1)%Math.ceil(all.length/4);this.draw()})
  }else if(this.panel==='connection'){
   this.text(50,135,290,50,30,'Phone connected',0xffffff)
   this.icon(147,220,'close',()=>{this.manualDisconnect=true;this.call({close:true}).catch(()=>{});this.paired=false;this.panel='connection';this.notice='Open phone app';this.draw()},96)
   this.text(70,340,250,40,24,'Disconnect')
  }else if(this.panel==='quick'){
   this.icon(68,174,this.camera.mode==='Photo'?'flash-'+this.camera.flash.toLowerCase():this.camera.torch?'torch-on':'flash-off',()=>this.send('FLASH'),96)
   this.icon(226,174,'connect',()=>this.navigate('connection'),96)
   this.text(58,288,116,42,24,'Flash')
   this.text(216,288,116,42,24,'Phone')
  }else if(this.panel==='delete'){
   if(this.media)this.widgets.push(ui.createWidget(ui.widget.IMG,{x:115,y:132,w:160,h:100,src:this.media,auto_scale:true,auto_scale_obj_fit:false}))
   this.icon(147,290,'trash-danger',()=>{if(!this.mediaReviewToken||this.busy)return;this.send('DELETE_REVIEW',{reviewToken:this.mediaReviewToken}).then(reply=>{if(reply?.ok&&this.alive){this.media=null;this.mediaReviewToken=null;const history=this.historyActive;this.historyActive=false;this.historyLoading=false;this.navigate('main',-1);if(history)this.openHistory()}})},96)
  }else if(this.panel==='review'){
   if(this.media)this.drawReviewImage()
   if(this.historyLoading&&!this.media)this.text(40,196,310,42,26,'Loading…',0xbac3ce)
   this.icon(16,14,'back',()=>this.goBack())
   if(this.historyActive){this.widgets.push(ui.createWidget(ui.widget.FILL_RECT,{x:146,y:28,w:100,h:34,color:0x000000}));this.text(146,28,100,34,24,this.historyLoading?'…':(this.historyIndex+1)+' / '+this.history.length)}
   if(this.mediaKind==='review'){
    const movie=this.mediaContentKind!=='Photo'
    if(movie){this.widgets.push(ui.createWidget(ui.widget.FILL_RECT,{x:267,y:28,w:110,h:34,color:0x000000}));this.text(267,28,110,34,24,'Video')}
    if(movie)this.icon(16,362,'phone',()=>{this.hintText='Video thumbnail. Play in phone gallery';this.navigate('hint')})
    if(this.mediaReviewToken)this.icon(159,362,'trash',()=>{if(!this.imageBusy&&!this.historyLoading)this.navigate('delete')})
   }else this.icon(159,362,'snapshot',()=>this.send('SNAPSHOT'))
  }else{
   this.icon(40,30,'close',()=>this.offerExit())
   this.icon(278,316,'switch',()=>{if(this.camera.recording){this.statusWidget?.setProperty(ui.prop.TEXT,'Stop recording first');return}this.send('SWITCH')})
   this.icon(278,30,'settings',()=>this.navigate('settings'))
   MODES.forEach((mode,i)=>{this.widgets.push(ui.createWidget(ui.widget.FILL_RECT,{x:158+i*20,y:113,w:mode===displayedMode?16:6,h:4,color:mode===displayedMode?0xffffff:0x3f4650}))})
   this.zoomChip=this.button(147,136,96,64,this.camera.zoomRatio.toFixed(1)+'×',()=>{if(this.camera.maxZoom>this.camera.minZoom)this.navigate('zoom')})
   this.modeWidget=this.text(70,210,250,42,30,displayedMode)
   this.clock=this.text(65,255,260,35,26,this.camera.recording?'● '+elapsed(this.camera.startedAt||Date.now(),Date.now()):this.busy?'Please wait…':displayedMode==='Photo'?this.camera.aspect+' · '+this.camera.timer+'s':this.camera.quality+' · '+this.camera.fps+' fps',this.camera.recording?0xff4c4c:0xbac3ce)
   this.drawThumbnail()
   this.captureControl(this.camera.recording?'stop':displayedMode==='Photo'?'shutter':'record',()=>this.send(this.camera.recording?'STOP':'SHUTTER'))
   this.statusWidget=this.text(65,420,260,28,22,this.busy?'Please wait…':'',this.camera.resourceWarning?0xffc85c:0xa8b0bc)
  }
  this.updateLabels();this.animateIn();this.queueThumbnail()
 },
 drawReviewImage(){
  this.widgets.push(ui.createWidget(ui.widget.IMG,{x:0,y:0,w:390,h:450,src:this.media,auto_scale:true,auto_scale_obj_fit:false}))
 },
 async openHistory(){
  if(this.busy||this.historyLoading)return
  if(this.imageBusy){if(this.expectedImageKind==='thumbnail')this.queuedHistory=true;return}
  this.historyLoading=true;const generation=++this.historyGeneration
  const reply=await this.send('HISTORY')
  if(!this.alive)return
  if(generation!==this.historyGeneration){this.historyLoading=false;return}
  if(!reply?.ok){this.historyLoading=false;return}
  this.history=(Array.isArray(reply.history)?reply.history:[]).slice(0,8).filter(v=>v&&typeof v.id==='string'&&/^[a-f0-9-]{36}$/.test(v.id)&&MODES.includes(v.kind))
  this.historyLoading=false
  if(!this.history.length){this.hintText='No captures yet';this.navigate('hint');return}
  this.historyActive=true;this.historyIndex=0;this.loadHistory(0)
 },
 async loadHistory(index){
  if(this.busy||this.imageBusy||this.historyLoading||!this.history[index])return
  this.historyLoading=true;this.historyPendingIndex=index;this.mediaReviewToken=null
  if(this.panel==='review')this.media=null
  this.draw()
  const reply=await this.send('REVIEW',{historyId:this.history[index].id,fullScreen:true})
  if(!reply?.ok&&this.alive){this.historyLoading=false;this.draw()}
 },
 drawThumbnail(){
  const open=()=>this.openHistory()
  if(!this.thumbnail){this.icon(40,316,'gallery',open);return}
  const w=ui.createWidget(ui.widget.IMG,{x:40,y:316,w:72,h:72,src:this.thumbnail,auto_scale:true,auto_scale_obj_fit:false});this.widgets.push(w)
  let press=null
  w.addEventListener(ui.event.CLICK_DOWN,e=>{press={x:e.x,y:e.y,at:Date.now()}})
  w.addEventListener(ui.event.MOVE,e=>{if(press&&Math.hypot((e.x??press.x)-press.x,(e.y??press.y)-press.y)>14)press=null})
  w.addEventListener(ui.event.MOVE_OUT,()=>{press=null})
  w.addEventListener(ui.event.CLICK_UP,()=>{const p=press;press=null;if(p&&Date.now()-p.at<650&&!this.busy&&!this.imageBusy)open()})
  if(this.thumbnailKind!=='Photo')this.widgets.push(ui.createWidget(ui.widget.IMG,{x:84,y:360,w:24,h:24,src:'video.png',auto_scale:true,auto_scale_obj_fit:false}))
 },
 queueThumbnail(){
  if(!this.alive||!this.paired||this.panel!=='main'||this.introVisible||this.busy||this.imageBusy||this.thumbnailSending||this.historyLoading)return
  if(this.queuedHistory){this.queuedHistory=false;this.openHistory();return}
  if(this.queuedImageOp){const q=this.queuedImageOp;this.queuedImageOp=null;this.send(q.op,q.args);return}
  const key=this.camera.latestMediaKey||''
  if(!key){if(this.thumbnail){this.thumbnail=null;this.thumbnailKey='';this.draw()}return}
  if(key===this.thumbnailKey||key===this.thumbnailFailedKey)return
  this.thumbnailSending=true;this.imageBusy=true;this.expectedImageKind='thumbnail';this.expectedThumbKey=key
  const id='thumb_'+Date.now()+'_'+(++seq);this.expectedImageId=id
  const fail=()=>{if(!this.alive||this.expectedImageId!==id)return;clearTimeout(this.imageTimer);this.imageBusy=false;this.thumbnailSending=false;this.thumbnailFailedKey=key;this.draw()}
  this.imageTimer=setTimeout(fail,60000)
  this.request({method:'COMMAND',params:{v:1,id,op:'THUMBNAIL',args:{},expires:Date.now()+25000}},{timeout:12000}).then(r=>{
   if(!this.alive||this.expectedImageId!==id)return
   if(typeof r==='string')r=JSON.parse(r)
   if(!r?.ok||r.id!==id||!r.mediaPath||r.mediaKey!==key){fail();return}
   this.pendingThumbnailKind=r.previewKind||'Photo'
   this.call({mediaPath:r.mediaPath,kind:'thumbnail',requestId:id}).catch(fail)
  }).catch(fail)
 },
 async connectLocal(manual=false){
  if(this.closing||this.connecting||this.busy||this.statusBusy||!this.alive)return
  this.connecting=true;if(manual){this.manualDisconnect=false}
  try{const r=await this.request({method:'CONNECT'},{timeout:12000});if(!this.alive||this.closing)return
   if(r.ok){
    this.bridgeVersion=r.bridgeVersion;this.phoneVersion=r.phoneVersion;this.phoneSessionId=r.sessionId
    this.paired=true;this.connected=true;this.diagnose('connected');this.initializing=this.photoFirst;this.draw()
    let status=await this.send('STATUS',{},true)
    // Native camera permission/provider startup is asynchronous; never expose a stale dashboard.
    const until=Date.now()+10000
    while(this.alive&&status?.ok&&!this.camera.ready&&Date.now()<until){await new Promise(resolve=>{this.startupResolve=resolve;this.startupTimer=setTimeout(resolve,400)});if(!this.alive)return;status=await this.send('STATUS',{},true)}
    if(!this.alive)return
    if(status?.ok&&this.photoFirst){
     // Preserve an active recording; a fresh idle session always starts in Photo.
     if(!this.camera.recording&&this.camera.mode!=='Photo')await this.send('CONFIG',{mode:'Photo'},true)
     this.photoFirst=false
    }
    this.initializing=false;if(this.paired&&this.panel!=='error'){this.panel='main';if(status?.ok&&this.camera.ready)this.connectionHaptic()}this.draw()
   }
   else{this.notice=r.pending?'Allow camera control on phone':String(r.error||'Open WristSnap on phone').replace('Error: ','');this.draw()}
  }catch(e){const reason=e?.message||String(e);this.notice=/PHONE_APP_CLOSED|PHONE_INACTIVE|AUTH_REQUIRED|WATCH_REQUIRED/.test(reason)?reason:'Connection lost. Move phone closer';this.draw()}
  finally{this.connecting=false;this.initializing=false;if(this.alive)this.draw()}
 },
 async changeZoom(delta){
  if(!this.paired||!this.camera.ready||this.busy&&!this.zoomSending)return
  const base=this.zoomRequested??this.camera.zoomRatio
  this.zoomRequested=Math.max(this.camera.minZoom,Math.min(this.camera.maxZoom,Math.round((base+delta)*10)/10))
  this.updateLabels()
  if(this.zoomSending)return
  this.zoomSending=true
  try{let sent;do{sent=this.zoomRequested;const result=await this.send('ZOOM',{ratio:sent});if(!result?.ok)break}while(this.alive&&this.paired&&this.zoomRequested!==sent)}finally{this.zoomSending=false;this.zoomRequested=null;this.updateLabels()}
 },
 changeMode(delta){
  if(this.camera.recording)return
  const current=this.queuedMode||this.pendingMode||this.camera.mode,next=modeAfter(current,delta)
  if(next===current)return
  if(this.busy){if(this.activeOp==='CONFIG'){this.queuedMode=next;this.pendingMode=next;this.transitionAxis=ui.prop.X;this.transition=delta;this.draw()}return}
  this.configure({mode:next})
 },
 configure(p){if(Object.keys(p).every(k=>this.camera[k]===p[k]))return;return this.send('CONFIG',p)},
 async send(op,args={},silent=false){
  if(!this.alive||this.closing||!this.paired)return
  const status=op==='STATUS'
  if(status?(this.statusBusy||this.busy):this.busy){if(!status){this.notice='Wait for the current action';this.updateLabels()}return}
  if(['SNAPSHOT','REVIEW'].includes(op)&&this.imageBusy){if(this.expectedImageKind==='thumbnail')this.queuedImageOp={op,args};return}
  if(status)this.statusBusy=true;else{this.busy=true;this.activeOp=op;this.revision++;this.syncSuspended=false}
  let succeeded=false
  const revision=this.revision,id=Date.now()+'_'+(++seq),historyGen=op==='REVIEW'&&this.historyActive?this.historyGeneration:null
  if(op==='SHUTTER'){this.pendingCaptureId=id;this.captureCountdownDeadline=0}
  if(op==='REVIEW')this.historyImageGeneration=historyGen
  if(['SNAPSHOT','REVIEW'].includes(op)){this.expectedImageId=id;this.imageBusy=true;this.expectedImageKind=op==='REVIEW'?'review':'snapshot';clearTimeout(this.imageTimer);this.imageTimer=setTimeout(()=>{if(!this.alive)return;this.imageBusy=false;this.historyLoading=false;if(historyGen!==null&&historyGen!==this.historyGeneration){this.draw();return}this.notice='Image transfer timed out. Review on phone or retry Frame.';this.panel='error';this.draw()},60000)}
  if(op==='CONFIG'&&MODES.includes(args.mode)){this.pendingMode=args.mode;this.transitionAxis=ui.prop.X;this.transition=MODES.indexOf(args.mode)>=MODES.indexOf(this.camera.mode)?1:-1;this.draw()}else this.updateLabels()
  if(op==='SHUTTER'||op==='STOP')this.captureHaptic()
  try{
   let r=await this.request({method:'COMMAND',params:{v:1,id,op,args,...(op==='SHUTTER'&&this.camera.timer>0?{progress:true}:{}),expires:Date.now()+25000}},{timeout:status?12000:35000})
   if(!this.alive||(status&&revision!==this.revision))return
   if(historyGen!==null&&historyGen!==this.historyGeneration){this.imageBusy=false;this.historyLoading=false;clearTimeout(this.imageTimer);return}
   if(typeof r==='string')r=JSON.parse(r)
   if(!r||typeof r!=='object')throw Error('Invalid phone response. Reopen both apps.')
   if(r.error&&!r.id)throw Error(String(r.error))
   if(r.id!==id)throw Error('Response ID mismatch; reconnect')
   let state=r.state;if(typeof state==='string')state=JSON.parse(state)
   if(state&&typeof state==='object'){
    const previousMode=this.camera.mode
    this.camera=cameraState(this.camera,state)
    if(status&&r.ok===true&&this.panel==='main'&&!this.initializing&&previousMode!==this.camera.mode){
     this.transitionAxis=ui.prop.X
     this.transition=Math.sign(MODES.indexOf(this.camera.mode)-MODES.indexOf(previousMode))
    }
   }
   if(r.ok!==true)throw Error(String(r.error||'Camera rejected command'))
   succeeded=true;this.statusFailures=0;this.connected=true;this.notice=r.message||'Camera ready'
   if(op==='REVIEW'){this.pendingReviewToken=r.reviewToken||null;this.pendingMediaContentKind=r.previewKind||'Photo'}
   if(r.mediaPath){this.imageBusy=true;this.call({mediaPath:r.mediaPath,kind:this.expectedImageKind,requestId:id}).catch(()=>{if(!this.alive)return;this.imageBusy=false;this.historyLoading=false;clearTimeout(this.imageTimer);if(historyGen!==null&&historyGen!==this.historyGeneration){this.draw();return}this.notice='Image request failed';this.panel='error';this.draw()})}
   return r
  }catch(e){
   if(!this.alive||(status&&revision!==this.revision))return
   if(['SNAPSHOT','REVIEW'].includes(op)){this.imageBusy=false;clearTimeout(this.imageTimer)}
   this.notice=(e?.message||String(e)||'Phone connection failed').replace('Error: ','').slice(0,120)
   const disconnected=/PHONE_APP_CLOSED|PHONE_INACTIVE|AUTH_REQUIRED|WATCH_REQUIRED|timed? ?out|timeout|network|disconnect|transport|connection lost/i.test(this.notice)||status&&this.statusFailures>=1
   if(status&&!this.initializing){this.statusFailures++;if(this.statusFailures<2&&!disconnected)return}
   this.syncSuspended=true;this.panel='error'
   if(disconnected){this.paired=false;this.connected=false;this.panel='connection';if(!/PHONE_APP_CLOSED|PHONE_INACTIVE|AUTH_REQUIRED|WATCH_REQUIRED/.test(this.notice))this.notice='Connection lost. Move phone closer'}
  }finally{
   if(op==='SHUTTER'){this.pendingCaptureId=null;this.captureCountdownDeadline=0}
   if(status)this.statusBusy=false;else{this.busy=false;this.activeOp=null;this.pendingMode=null}
   const queued=!status?this.queuedMode:null;if(!status)this.queuedMode=null
   if(this.alive&&succeeded&&queued&&queued!==this.camera.mode&&this.paired){this.configure({mode:queued})}else if(this.alive)this.draw()
  }
 },
 onCall(data){if(data.captureProgress){const p=data.captureProgress;if(this.alive&&this.busy&&this.activeOp==='SHUTTER'&&p.requestId===this.pendingCaptureId&&Number.isFinite(p.remainingMs)&&p.remainingMs>=0&&p.remainingMs<=10000){const deadline=Date.now()+p.remainingMs;this.captureCountdownDeadline=this.captureCountdownDeadline?Math.min(this.captureCountdownDeadline,deadline):deadline;this.updateLabels()}return}if(data.imageError&&this.expectedImageKind==='thumbnail'&&data.requestId===this.expectedImageId){clearTimeout(this.imageTimer);this.imageBusy=false;this.thumbnailSending=false;this.thumbnailFailedKey=this.expectedThumbKey;this.draw();return}if(data.imageError&&this.imageBusy&&data.requestId===this.expectedImageId){this.imageBusy=false;clearTimeout(this.imageTimer);this.historyLoading=false;if(this.historyImageGeneration!=null&&this.historyImageGeneration!==this.historyGeneration){this.draw();return}this.notice=data.imageError;this.panel='error';this.draw()}},
 onReceivedFile(file){
  if(!this.imageBusy||file.params?.kind!==this.expectedImageKind||file.params?.requestId!==this.expectedImageId)return
  if(this.expectedImageKind==='thumbnail'){
   const finish=()=>{if(!this.alive||file.params.requestId!==this.expectedImageId)return;clearTimeout(this.imageTimer);this.imageBusy=false;this.thumbnailSending=false;if(this.expectedThumbKey===this.camera.latestMediaKey){this.thumbnail=file.filePath;this.thumbnailKey=this.expectedThumbKey;this.thumbnailKind=this.pendingThumbnailKind;this.thumbnailFailedKey=''}this.draw();this.queueThumbnail()}
   file.on('change',e=>{if(e.data.readyState==='transferred')finish();else if(e.data.readyState==='error'){this.imageBusy=false;this.thumbnailSending=false;this.thumbnailFailedKey=this.expectedThumbKey;this.draw()}});if(file.readyState==='transferred')finish();return
  }
  const render=()=>{if(!this.alive||!this.imageBusy||file.params.requestId!==this.expectedImageId)return;this.imageBusy=false;clearTimeout(this.imageTimer);if(this.historyImageGeneration!=null&&this.historyImageGeneration!==this.historyGeneration){this.historyLoading=false;this.draw();return}this.media=file.filePath;this.mediaKind=file.params.kind;this.mediaContentKind=this.pendingMediaContentKind||'Photo';this.mediaReviewToken=file.params.kind==='review'?this.pendingReviewToken:null;if(this.historyActive){this.historyIndex=this.historyPendingIndex??0;this.historyLoading=false}this.notice='Still image • swipe to return';this.navigate('review')}
  file.on('change',e=>{if(!this.alive||!this.imageBusy||file.params.requestId!==this.expectedImageId)return;if(e.data.readyState==='transferred')render();else if(e.data.readyState==='error'){this.imageBusy=false;this.historyLoading=false;clearTimeout(this.imageTimer);if(this.historyImageGeneration!=null&&this.historyImageGeneration!==this.historyGeneration){this.draw();return}this.notice='Image failed. Retry Frame.';this.panel='error';this.draw()}})
  if(file.readyState==='transferred')render()
 },
 build(){try{setPageBrightTime({brightTime:60000})}catch(e){}ui.setStatusBarVisible(false);this.draw();if(!this.paired&&!this.introVisible)this.connectLocal();onGesture({callback:g=>{this.capturePress?.cancel();
  if(!this.alive||this.closing)return false
  if(this.panel==='review'&&this.historyActive&&(g===GESTURE_LEFT||g===GESTURE_RIGHT)){
   if(!this.historyLoading&&!this.imageBusy){const index=Math.max(0,Math.min(this.history.length-1,this.historyIndex+(g===GESTURE_LEFT?1:-1)));if(index!==this.historyIndex)this.loadHistory(index)}return true
  }
  if(g===GESTURE_RIGHT){if(this.paired&&!this.introVisible&&!this.initializing&&this.panel==='main')this.changeMode(-1);else this.goBack();return true}
  if(this.introVisible||!this.paired||this.initializing)return false
  if(this.panel==='main'){
   if(g===GESTURE_LEFT){this.changeMode(1);return true}
   if(g===GESTURE_UP){this.navigate('settings');return true}
   if(g===GESTURE_DOWN){this.navigate('quick',-1);return true}
  }else if(this.panel==='settings'&&g===GESTURE_DOWN||this.panel==='quick'&&g===GESTURE_UP){this.goBack();return true}
  return true
 }});this.syncTimers()},
 onDestroy(){
  // Never enable an app title bar while the OS is restoring the watch face.
  if(!this.closing&&(this.paired||this.connecting)){
   try{this.call({detach:true}).catch(()=>{})}catch(e){}
  }
  try{resetPageBrightTime()}catch(e){}
  this.alive=false;offGesture();clearInterval(this.tick);clearInterval(this.sync);clearTimeout(this.pollTimer);clearTimeout(this.imageTimer);clearInterval(this.connectTimer);clearTimeout(this.startupTimer);this.startupResolve?.()
  clearTimeout(this.hapticStopTimer);try{this.vibrator?.stop()}catch(e){}this.capturePress=null
  this.widgets.forEach(w=>{try{ui.deleteWidget(w)}catch(e){}});this.widgets=[];this.lastClockText=null;this.lastZoomText=null;this.lastStatusText=null;this.clock=null;this.statusWidget=null;this.zoomChip=null;this.zoomValueWidget=null
 }
}))
