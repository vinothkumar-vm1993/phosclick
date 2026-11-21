import { BaseSideService,convertLib } from '@zeppos/zml/base-side'
// Fixed phone loopback only. No public server, configurable URL, second BLE link or typed key.
const LOCAL='http://127.0.0.1:47835'
AppSideService(BaseSideService({
 onInit(){this.alive=true;this.imageBusy=false;this.presenceRequests=new Set()},
 async http(path,body,publicRequest=false){
  const presence=path==='/session'||path==='/status'||path==='/command'
  if(presence&&this.detached)throw Error('Watch controls closed')
  const token=settings.settingsStorage.getItem('localCapability')||''
  let r,pending
  try{
   pending=fetch({url:LOCAL+path,method:body?'POST':'GET',timeout:4000,headers:{'Content-Type':'application/json',...(!publicRequest?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})})
   if(presence)this.presenceRequests.add(pending)
   r=await pending
  }
  catch(e){throw Error('PHONE_APP_CLOSED: open WristSnap on phone')}
  finally{if(presence)this.presenceRequests.delete(pending)}
  const status=Number(r.status??r.statusCode)
  if(!Number.isInteger(status)||status<200||status>599)throw Error('Invalid local HTTP response. Reopen WristSnap.')
  const value=typeof r.body==='string'?JSON.parse(r.body):r.body
  if(!value||typeof value!=='object')throw Error('Invalid camera data. Reopen WristSnap.')
  if(status>=400){if(status===401)settings.settingsStorage.removeItem('localCapability');throw Error(value?.error||'Phone connection failed')}
  return {status,value}
 },
 async connect(){
  if(this.detaching)await this.detaching.catch(()=>{})
  this.detached=false
  if(settings.settingsStorage.getItem('localCapability')){try{const session=await this.http('/session');return{ok:true,bridgeVersion:'0.6.19',phoneVersion:session.value.version,sessionId:session.value.sessionId}}catch(e){if(!String(e).includes('AUTH_REQUIRED'))throw e}}
  let ticket=settings.settingsStorage.getItem('localTicket')
  if(!ticket){const r=await this.http('/enroll',{},true);ticket=r.value.ticket;settings.settingsStorage.setItem('localTicket',ticket);return{ok:false,pending:true}}
  try{const r=await this.http('/enroll/'+ticket,null,true);if(!r.value.approved)return{ok:false,pending:true};settings.settingsStorage.setItem('localCapability',r.value.token);settings.settingsStorage.removeItem('localTicket');const session=await this.http('/session');return{ok:true,bridgeVersion:'0.6.19',phoneVersion:session.value.version,sessionId:session.value.sessionId}}
  catch(e){settings.settingsStorage.removeItem('localTicket');throw e}
 },
 async detach(){
  if(this.detaching)return this.detaching
  this.detached=true
  this.detaching=(async()=>{
   // Let any already-issued presence request reach the phone before clearing it.
   await Promise.all([...this.presenceRequests].map(request=>request.catch(()=>{})))
   if(settings.settingsStorage.getItem('localCapability'))await this.http('/detach',{})
  })()
  try{await this.detaching}finally{this.detaching=null}
 },
 async onRequest(req,res){
  try{
   if(req.method==='CONNECT'){res(null,await this.connect());return}
   if(req.method==='DETACH'){await this.detach();res(null,{ok:true});return}
   if(req.method!=='COMMAND')throw Error('Unknown camera request')
   const c=req.params
   if(c.op==='STATUS'){const r=await this.http('/status');res(null,{...r.value,id:c.id});return}
   await this.http('/command',c)
   if(c.op==='SHUTTER'&&c.progress===true){try{const status=await this.http('/status');this.call({captureProgress:{requestId:c.id,remainingMs:status.value.state?.countdownRemainingMs||0}}).catch(()=>{})}catch(e){/* Progress failure must not replay or reject an accepted shutter. */}}
   const deadline=Date.now()+30000
   while(this.alive&&Date.now()<deadline){const r=await this.http('/result/'+c.id+'?wait=1');if(r.status===200){res(null,r.value);return};await new Promise(r=>setTimeout(r,100))}
   throw Error('Outcome unknown. Check phone before retrying shutter.')
  }catch(e){res(null,{ok:false,error:String(e)})}
 },
 onCall(data){if(data.detach)this.detach().catch(()=>{});if(data.diagnostics)this.http('/diagnostics',{...data.diagnostics,sideVersion:'0.6.19'}).catch(()=>{});if(data.mediaPath)this.transfer(data.mediaPath,data.kind,data.requestId);if(data.close){this.generation=(this.generation||0)+1;clearTimeout(this.imageTimer);this.imageBusy=false;this.task?.cancel?.();this.http('/close',{}).catch(()=>{})}},
 async transfer(path,kind,requestId){
  if(typeof requestId!=='string'||!/^([A-Za-z0-9_-]{1,80})$/.test(requestId))return
  if(this.imageBusy){this.call({imageError:'Wait for the current image transfer',requestId}).catch(()=>{});return}
  if(!/^\/media\/[a-f0-9]{48}$/.test(path)||!['snapshot','review','thumbnail'].includes(kind))return
  const fileKind=kind==='review'?kind+'-'+(this.reviewSlot=((this.reviewSlot||0)^1)):kind
  this.imageBusy=true;const generation=this.generation=(this.generation||0)+1
  const finish=()=>{if(this.generation!==generation)return;clearTimeout(this.imageTimer);this.imageBusy=false;this.generation++}
  const error=message=>{if(this.alive)this.call({imageError:message,requestId}).catch(()=>{})}
  this.imageTimer=setTimeout(()=>{this.task?.cancel?.();finish();error('Local image timed out. Review on phone or retry Frame.')},60000)
  try{
   const token=settings.settingsStorage.getItem('localCapability')||''
   this.task=network.downloader.downloadFile({url:LOCAL+path,headers:{Authorization:'Bearer '+token},filePath:'data://download/camera-'+fileKind+'.png',timeout:20000})
   this.task.onFail=()=>{if(this.generation!==generation)return;finish();error('Local image download failed. Review on phone.')}
   this.task.onSuccess=async e=>{
    if(!this.alive||this.generation!==generation)return
    try{if(e.statusCode!==200)throw Error('Image expired');const result=await convertLib.convert({filePath:e.filePath||e.tempFilePath,targetFilePath:'data://download/camera-'+fileKind+'-converted.img'});if(!this.alive||this.generation!==generation)return;this.task=this.sendFile(result.targetFilePath,{kind,requestId});this.task.on('change',e=>{if(this.generation!==generation)return;if(['transferred','error','canceled'].includes(e.data.readyState)){finish();if(e.data.readyState!=='transferred')error('Image transfer failed. Retry Frame.')}})}catch(e){finish();error('Image conversion unavailable. Review on phone.')}
   }
  }catch(e){finish();error('Image unavailable. Review on phone.')}
 },
 onDestroy(){this.alive=false;clearTimeout(this.imageTimer);this.task?.cancel?.()}
}))
