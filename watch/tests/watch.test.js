import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import fs from 'node:fs';
import {MODES,modeAfter,elapsed,nextValue,cameraState,noticeText} from '../shared/model.js';
function host(locked=false,overrides={}){let page,gesture,backCalls=0;const widgets=[];const context={ui:{setStatusBarVisible(){},widget:{TEXT:'text',BUTTON:'button',IMG:'img',FILL_RECT:'rect'},event:{CLICK_DOWN:'down',CLICK_UP:'up',MOVE:'move',MOVE_OUT:'out'},prop:{SRC:'src',TEXT:'text',X:'x',Y:'y',ANIM:'anim'},align:{CENTER_H:0},text_style:{WRAP:1},createWidget(type,p){const w={type,...p,listeners:{},addEventListener(e,f){this.listeners[e]=f},getProperty(k){return this[k]},setProperty(k,v){this[k]=v}};widgets.push(w);return w},deleteWidget(w){widgets.splice(widgets.indexOf(w),1)}},home:()=>{backCalls++},setWakeUpRelaunch(){},setPageBrightTime(){},resetPageBrightTime(){},connectStatus:()=>true,Vibrator:class{getType(){return{GENTLE_SHORT:3}}start(){}stop(){}},VIBRATOR_SCENE_SHORT_LIGHT:1,VIBRATOR_SCENE_SHORT_MIDDLE:2,VIBRATOR_SCENE_NOTIFICATION:3,LocalStorage:class{getItem(){return true}setItem(){}},BasePage:x=>x,Page:p=>page=p,onGesture:o=>gesture=o.callback,offGesture(){},GESTURE_UP:1,GESTURE_DOWN:2,GESTURE_LEFT:3,GESTURE_RIGHT:4,MODES,modeAfter,elapsed,nextValue,cameraState,noticeText,setTimeout,clearTimeout,setInterval:()=>1,clearInterval,Date,Promise,console};
const source=fs.readFileSync(new URL('../page/index.js',import.meta.url),'utf8').replace(/^import .*$/gm,'');Object.assign(context,overrides);vm.runInNewContext(source,context);page.onInit();if(!locked){page.paired=true;page.panel="main";page.camera.ready=true}page.build();return {page,widgets,gesture,backCount:()=>backCalls};}
test('mandatory pairing blocks camera controls and captures',async()=>{const h=host(true);let requests=0;h.page.request=async()=>{requests++;return{error:'not connected'}};await h.page.send('SHUTTER');assert.equal(requests,0);assert.ok(h.widgets.some(w=>w.normal_src==='back.png'));assert.ok(h.widgets.some(w=>w.normal_src==='connect.png'));assert.equal(h.page.paired,false)});
test('mode endpoints and record clock',()=>{assert.equal(modeAfter('Photo',-1),'Photo');assert.equal(modeAfter('Timelapse',1),'Timelapse');assert.equal(elapsed(1000,62000),'01:01');assert.equal(nextValue([0,3,10],10),0)});
test('real dashboard source has large icon targets and gestures',()=>{const h=host();const icons=h.widgets.filter(w=>w.normal_src);assert.ok(icons.length>=4);assert.ok(icons.every(w=>w.w>=72&&w.h>=72));assert.ok(h.widgets.some(w=>w.src==='shutter.png'&&w.w===120));assert.ok(h.widgets.some(w=>w.type==='rect'&&w.color===0));h.gesture(1);assert.equal(h.page.panel,'settings');h.gesture(2);assert.equal(h.page.panel,'main');h.gesture(2);assert.equal(h.page.panel,'quick')});
test('all panels fit display, buttons do not overlap',()=>{const h=host();for(const panel of ['main','settings','quick','review','error','hint','connection','zoom','exit']){h.page.panel=panel;h.page.draw();for(const w of h.widgets){assert.ok(w.x>=0&&w.y>=0&&w.x+w.w<=390&&w.y+w.h<=450,`${panel}: ${w.text}`)}const b=h.widgets.filter(w=>w.type==='button');for(let i=0;i<b.length;i++)for(let j=i+1;j<b.length;j++){const a=b[i],c=b[j];assert.ok(!(a.x<c.x+c.w&&a.x+a.w>c.x&&a.y<c.y+c.h&&a.y+a.h>c.y),`${panel} overlap`)}}});
test('correlated Zepp response applies confirmed phone state',async()=>{const h=host();h.page.request=async q=>({id:q.params.id,ok:true,state:{mode:'Video',recording:true,startedAt:Date.now()},message:'Recording'});await h.page.send('SHUTTER');assert.equal(h.page.camera.mode,'Video');assert.equal(h.page.camera.recording,true);assert.ok(h.widgets.some(w=>w.src==='stop.png'))});
test('bridge errors are actionable and release busy lock',async()=>{const h=host();h.page.request=async()=>{throw Error('Open Pixel camera')};await h.page.send('STATUS');await h.page.send('STATUS');assert.equal(h.page.panel,'connection');assert.equal(h.page.busy,false);assert.ok(h.widgets.some(w=>w.normal_src==='connect.png'))});
test('only accepted shutter/start/stop taps get one immediate bounded pulse; replies add none',async()=>{
 const h=host();const calls=[];h.page.vibrator={getType:()=>({GENTLE_SHORT:3}),stop(){},start(v){calls.push(v)}};h.page.call=async()=>{}
 h.gesture(1);assert.equal(calls.length,0);h.page.panel='main'
 let resolve;h.page.request=q=>new Promise(r=>resolve=()=>r({id:q.params.id,ok:true,state:{mode:h.page.camera.mode,recording:h.page.camera.mode!=='Photo'&&q.params.op==='SHUTTER'}}))
 let pending=h.page.send('SHUTTER');assert.equal(calls.length,1);resolve();await pending;assert.equal(calls.length,1)
 h.page.camera.mode='Video';pending=h.page.send('SHUTTER');assert.equal(calls.length,2);resolve();await pending;pending=h.page.send('STOP');assert.equal(calls.length,3);resolve();await pending;assert.equal(calls.length,3)
 for(const pulse of calls){assert.equal(pulse.length,1);assert.equal(pulse[0].type,3);assert.equal(pulse[0].duration,12)}
});
test('received camera file switches to still review',()=>{const h=host();h.page.imageBusy=true;h.page.expectedImageKind='review';h.page.expectedImageId='review1';h.page.onReceivedFile({params:{kind:'review',requestId:'review1'},filePath:'data://camera.img',readyState:'transferred',on(){}});assert.equal(h.page.panel,'review');assert.ok(h.widgets.some(w=>w.src==='data://camera.img'))});
if(process.env.EXPORT_LAYOUT){const h=host();const frames={};for(const panel of ['main','settings','quick','error']){h.page.panel=panel;h.page.notice='Open the Pixel camera app, then tap Reconnect.';h.page.draw();frames[panel]=h.widgets.map(({type,x,y,w,h,text,text_size,color,normal_color,normal_src,src,radius})=>({type,x,y,w,h,text,text_size,color,normal_color,normal_src,src,radius}))}fs.writeFileSync(process.env.EXPORT_LAYOUT,JSON.stringify(frames))}

test('optional vibrator failure never blanks the page',()=>{
 const h=host(false,{Vibrator:class{constructor(){throw Error('unavailable')}}});assert.ok(h.widgets.some(w=>w.src==='shutter.png'));h.page.captureHaptic();assert.equal(h.page.alive,true)
});
test('fresh idle connection selects Photo before exposing the dashboard',async()=>{
 const h=host();h.page.paired=false;h.page.panel='connection';const ops=[]
 h.page.request=async q=>{if(q.method==='CONNECT')return{ok:true};ops.push(q.params.op);return{id:q.params.id,ok:true,state:{mode:q.params.op==='CONFIG'?'Photo':'Video',recording:false}}}
 await h.page.connectLocal();assert.deepEqual(ops,['STATUS','CONFIG']);assert.equal(h.page.camera.mode,'Photo');assert.equal(h.page.panel,'main');assert.equal(h.page.initializing,false)
 // A later reconnect in the same page does not reset the mode selected by hand.
 h.page.request=async q=>q.method==='CONNECT'?{ok:true}:{id:q.params.id,ok:true,state:{mode:'Video',recording:false}}
 await h.page.connectLocal();assert.equal(h.page.camera.mode,'Video')
});
test('opening during an active recording preserves it',async()=>{
 const h=host();const ops=[];h.page.request=async q=>{if(q.method==='CONNECT')return{ok:true};ops.push(q.params.op);return{id:q.params.id,ok:true,state:{mode:'Video',recording:true}}}
 await h.page.connectLocal();assert.deepEqual(ops,['STATUS']);assert.equal(h.page.camera.recording,true)
});
test('exit removes widgets, stops the motor, and ignores late responses',async()=>{
 const h=host();let resolve;h.page.request=()=>new Promise(r=>resolve=r);h.page.call=async()=>{}
 const pending=h.page.send('STATUS');h.page.onDestroy();assert.equal(h.widgets.length,0);resolve({ok:true,id:'late',state:{mode:'Video'}});await pending;assert.equal(h.widgets.length,0);assert.equal(h.page.camera.mode,'Photo')
 const source=fs.readFileSync(new URL('../page/index.js',import.meta.url),'utf8');assert.ok(!source.includes('setStatusBarVisible(true)'))
});

test('camera state rejects undefined/null/wrong-type fields without erasing known settings',()=>{
 const state=cameraState({mode:'Video',fps:60,aspect:'16:9'},{mode:undefined,fps:null,aspect:'undefined',timer:'bad',frameRates:[undefined,30,'60',null]})
 assert.equal(state.mode,'Video');assert.equal(state.fps,60);assert.equal(state.aspect,'16:9');assert.deepEqual(state.frameRates,[30]);assert.equal(nextValue([],60),60)
});
test('main swipes select adjacent modes both ways and never close controls',async()=>{
 const h=host();const ops=[];h.page.request=async q=>{ops.push(q.params.args.mode);return{id:q.params.id,ok:true,state:{mode:q.params.args.mode,ready:true,recording:false}}}
 assert.ok(h.widgets.some(w=>w.normal_src==='close.png'&&w.x===40))
 for(const expected of ['Video','Slow Motion','Timelapse']){h.gesture(3);await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.mode,expected)}
 h.gesture(3);assert.equal(h.page.camera.mode,'Timelapse');assert.equal(ops.length,3)
 for(const expected of ['Slow Motion','Video','Photo']){h.gesture(4);await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.mode,expected);assert.equal(h.page.panel,'main')}
 h.gesture(4);assert.equal(h.page.panel,'main');assert.equal(ops.length,6);assert.equal(h.backCount(),0)
 h.widgets.find(w=>w.normal_src==='close.png'&&w.x===40).click_func();assert.equal(h.page.panel,'exit');h.widgets.find(w=>w.normal_src==='back.png').click_func();assert.equal(h.page.panel,'main')
});
test('quick repeated mode swipes coalesce to the final intended page',async()=>{
 const h=host();const pending=[];h.page.request=q=>new Promise(resolve=>pending.push(()=>resolve({id:q.params.id,ok:true,state:{mode:q.params.args.mode,ready:true,recording:false}})))
 h.gesture(3);h.gesture(3);h.gesture(3);assert.equal(h.page.pendingMode,'Timelapse');assert.equal(pending.length,1)
 pending.shift()();await new Promise(r=>setTimeout(r,0));assert.equal(pending.length,1);pending.shift()();await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.mode,'Timelapse');assert.equal(h.page.busy,false)
});
test('Photo FPS and missing capability rows never send undefined CONFIG',()=>{
 const h=host();h.page.camera=cameraState({}, {frameRates:[],qualities:[],photoSizes:[]});h.page.panel='settings';h.page.draw();let count=0;h.page.request=async()=>{count++;return{}}
 for(const w of h.widgets.filter(w=>w.type==='button'&&!w.normal_src&&(w.y===116||w.y===194)))w.click_func()
 assert.equal(count,0);assert.ok(h.widgets.every(w=>!String(w.text||'').includes('undefined')))
});

test('image buttons always pass empty native text instead of undefined',()=>{
 const h=host();for(const panel of ['main','settings','quick','connection']){h.page.panel=panel;h.page.draw();for(const w of h.widgets.filter(w=>w.type==='button'))assert.equal(typeof w.text,'string',panel+': native button text')}
});
test('heartbeat neither rebuilds unchanged controls nor flashes Please wait',async()=>{
 const h=host();const controls=[...h.widgets];let resolve;h.page.request=q=>new Promise(r=>resolve=()=>r({id:q.params.id,ok:true,state:{mode:'Photo',recording:false,ready:true}}))
 const pending=h.page.send('STATUS',{},true);assert.equal(h.page.busy,false);assert.ok(!h.widgets.some(w=>w.text==='Please wait…'));resolve();await pending;assert.equal(h.widgets.length,controls.length);assert.ok(h.widgets.every((w,i)=>w===controls[i]))
});
test('record tap during pending heartbeat executes and stale status cannot undo recording',async()=>{
 const h=host();h.page.camera.mode='Video';h.page.draw();let resolveStatus;const ops=[]
 h.page.request=q=>{ops.push(q.params.op);if(q.params.op==='STATUS')return new Promise(r=>resolveStatus=()=>r({id:q.params.id,ok:true,state:{mode:'Photo',recording:false}}));return Promise.resolve({id:q.params.id,ok:true,state:{mode:'Video',recording:true,ready:true,startedAt:Date.now()}})}
 const status=h.page.send('STATUS',{},true);await h.page.send('SHUTTER');resolveStatus();await status;assert.deepEqual(ops,['STATUS','SHUTTER']);assert.equal(h.page.camera.recording,true);assert.equal(h.page.camera.mode,'Video');assert.equal(h.page.busy,false)
});
test('camera fields do not replace framework messaging state',()=>{
 const framework={messaging:{native:true}};const h=host(false,{BasePage:p=>({...p,state:framework})});assert.equal(h.page.state,framework);assert.equal(h.page.camera.mode,'Photo')
});
test('Photo edge swipe stays in camera; connection Back still offers touch exit',async()=>{
 const h=host();assert.equal(h.gesture(4),true);assert.equal(h.page.panel,'main');assert.equal(h.backCount(),0);h.page.paired=false;h.page.panel='connection';assert.equal(h.gesture(4),true);assert.equal(h.page.panel,'exit');await h.widgets.find(w=>w.normal_src==='close96.png').click_func();assert.equal(h.backCount(),1)
});
test('startup waits for the actual phone camera before leaving its loading page',async()=>{
 const h=host();h.page.paired=false;h.page.camera.ready=false;let statuses=0;h.page.request=async q=>q.method==='CONNECT'?{ok:true}:{id:q.params.id,ok:true,state:{mode:q.params.op==='CONFIG'?'Photo':'Video',recording:false,ready:q.params.op==='CONFIG'||++statuses>1}}
 const start=h.page.connectLocal();await new Promise(r=>setTimeout(r,10));assert.equal(h.page.initializing,true);assert.ok(!h.widgets.some(w=>w.normal_src==='record.png'));await start;assert.equal(h.page.camera.mode,'Photo');assert.equal(h.page.camera.ready,true)
});

test('late or mismatched image transfers cannot replace a newer preview',()=>{
 const h=host();h.page.imageBusy=true;h.page.expectedImageId='new';h.page.expectedImageKind='review';h.page.onReceivedFile({params:{kind:'review',requestId:'old'},filePath:'old.img',readyState:'transferred',on(){}});assert.equal(h.page.panel,'main');assert.equal(h.page.imageBusy,true);h.page.onCall({imageError:'old failure',requestId:'old'});assert.equal(h.page.panel,'main');assert.equal(elapsed(undefined,Date.now()),'00:00')
});

test('capture uses low-level image touch; one tap dispatches once and swipe cannot capture',async()=>{
 const h=host();let count=0;h.page.send=async()=>count++;let w=h.widgets.find(w=>w.src==='shutter.png');assert.equal(w.type,'img');w.listeners.down({x:170,y:330});w.listeners.up();assert.equal(count,1);w.listeners.up();assert.equal(count,1)
 w.listeners.down({x:170,y:330});w.listeners.move({x:210,y:330});w.listeners.up();assert.equal(count,1)
 w.listeners.down({x:170,y:330});h.gesture(1);w.listeners.up();assert.equal(count,1)
});
test('watch feedback has no audio API, sound controls or audio assets',()=>{
 const source=fs.readFileSync(new URL('../page/index.js',import.meta.url),'utf8');assert.ok(!/@zos\/media|SystemSounds|toggleSound|soundEnabled|\.cue\(/.test(source));const h=host();for(const panel of ['settings','quick']){h.page.panel=panel;h.page.draw();assert.ok(!h.widgets.some(w=>String(w.normal_src||'').startsWith('sound')||w.text==='Sound'))}assert.ok(!fs.existsSync(new URL('../assets/square390/sounds',import.meta.url)))
});

test('front/rear switch is directly visible on main and applies only confirmed lens state',async()=>{
 const h=host();let pulses=0;h.page.captureHaptic=()=>pulses++;h.page.request=async q=>{assert.equal(q.params.op,'SWITCH');return{id:q.params.id,ok:true,state:{front:!h.page.camera.front}}}
 const flip=h.widgets.find(w=>w.normal_src==='switch.png'&&w.x===278&&w.y===316);assert.ok(flip);flip.click_func();await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.front,true);assert.equal(h.page.statusWidget.text,'Front camera');flip.click_func();await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.front,false);assert.equal(h.page.statusWidget.text,'Rear camera');assert.equal(pulses,0)
});
test('main camera flip does not send SWITCH during recording',()=>{
 const h=host();h.page.camera.recording=true;h.page.draw();let count=0;h.page.send=async()=>count++;h.widgets.find(w=>w.normal_src==='switch.png'&&w.x===278).click_func();assert.equal(count,0);assert.equal(h.page.statusWidget.text,'Stop recording first')
});

test('phone-closed copy hides bridge codes and fits separated connection text area',()=>{
 const h=host(true);h.page.notice='PHONE_APP_CLOSED: open WristSnap on Pixel';h.page.draw();
 const message=h.widgets.find(w=>w.type==='text'&&w.y===238);assert.equal(message.text,'Open phone app');assert.equal(message.h,72)
 assert.ok(!h.widgets.some(w=>/Pixel|PHONE_APP_CLOSED/.test(w.text||'')))
 const connect=h.widgets.find(w=>w.normal_src==='connect.png');assert.ok(message.y+message.h<connect.y)
 for(const text of ['x'.repeat(180),'AUTH_REQUIRED: expired','Allow camera control on phone']){const value=noticeText(text,3);assert.ok(value.split('\n').length<=3);assert.ok(value.split('\n').every(l=>l.length<=20))}
});

test('first ready connection emits one short pulse; repeated connect/status and reconnect stay quiet',async()=>{
 const h=host();let pulses=[];h.page.vibrator={getType:()=>({GENTLE_SHORT:3}),stop(){},start(p){pulses.push(p)}};h.page.call=async()=>{}
 h.page.request=async q=>q.method==='CONNECT'?{ok:true}:{id:q.params.id,ok:true,state:{ready:true,mode:'Photo'}}
 await h.page.connectLocal();assert.equal(pulses.length,1);assert.equal(pulses[0].length,1);assert.equal(pulses[0][0].duration,12)
 await h.page.connectLocal();await h.page.send('STATUS');h.page.paired=false;await h.page.connectLocal();assert.equal(pulses.length,1)
});
test('pending and failed connections do not vibrate',async()=>{
 const h=host(true);let count=0;h.page.vibrator={getType:()=>({GENTLE_SHORT:3}),stop(){},start(){count++}}
 h.page.request=async()=>({ok:false,pending:true});await h.page.connectLocal();assert.equal(count,0)
 h.page.request=async()=>{throw Error('PHONE_APP_CLOSED')};await h.page.connectLocal();assert.equal(count,0)
});

test('closing watch UI never disconnects the persistent phone session',()=>{
 const h=host();const calls=[];h.page.call=d=>{calls.push(d);return Promise.resolve()};h.page.onDestroy();assert.ok(!calls.some(d=>d.close))
});
test('status transport failure enters minimal reconnect state without replaying a capture',async()=>{
 const h=host();const ops=[];h.page.request=async q=>{ops.push(q.params.op);throw Error('Request timeout')};await h.page.send('STATUS',{},true);assert.equal(h.page.paired,false);assert.equal(h.page.panel,'connection');assert.ok(h.widgets.some(w=>w.text==='Move phone closer'));assert.deepEqual(ops,['STATUS'])
});

test('first-install guide is minimal and Skip persists without granting camera access',()=>{
 const values={};const Storage=class{getItem(k,f){return values[k]??f}setItem(k,v){values[k]=v}}
 const h=host(true,{LocalStorage:Storage});assert.equal(h.page.introVisible,true);assert.ok(h.widgets.some(w=>w.text==='Skip'));assert.ok(!h.widgets.some(w=>w.src==='shutter.png'));h.page.request=async()=>({ok:false,pending:true});h.widgets.find(w=>w.text==='Skip').click_func();assert.equal(values.guide_v1,true);assert.equal(h.page.paired,false)
 const reopened=host(true,{LocalStorage:Storage});assert.equal(reopened.page.introVisible,false)
});

test('zoom controls respect hardware limits and do not trigger haptic or mode changes',async()=>{
 const h=host();h.page.camera=cameraState(h.page.camera,{minZoom:1,maxZoom:3,zoomRatio:1});h.page.panel='zoom';h.page.draw();let pulses=0;h.page.vibrator.start=()=>pulses++;const ops=[]
 h.page.request=async q=>{ops.push(q.params);return{id:q.params.id,ok:true,state:{minZoom:1,maxZoom:3,zoomRatio:q.params.args.ratio}}}
 await h.page.changeZoom(10);assert.equal(h.page.camera.zoomRatio,3);await h.page.changeZoom(-10);assert.equal(h.page.camera.zoomRatio,1);assert.ok(ops.every(o=>o.op==='ZOOM'));assert.equal(pulses,0)
});
test('rapid zoom taps coalesce to the last requested ratio',async()=>{
 const h=host();h.page.camera=cameraState(h.page.camera,{minZoom:1,maxZoom:5,zoomRatio:1});let release;const sent=[]
 h.page.request=q=>new Promise(resolve=>{sent.push(q.params.args.ratio);release=()=>resolve({id:q.params.id,ok:true,state:{minZoom:1,maxZoom:5,zoomRatio:q.params.args.ratio}})})
 const pending=h.page.changeZoom(.1);await h.page.changeZoom(.1);await h.page.changeZoom(.1);release();await new Promise(r=>setTimeout(r,0));release();await pending;assert.deepEqual(sent,[1.1,1.3]);assert.equal(h.page.camera.zoomRatio,1.3)
});

test('nested Back returns to the opener and swipe-to-close never disconnects phone',async()=>{
 const h=host();h.page.navigate('quick');h.page.navigate('connection');h.gesture(4);assert.equal(h.page.panel,'quick');h.gesture(4);assert.equal(h.page.panel,'main');const calls=[];h.page.call=d=>{calls.push(d);return Promise.resolve()};h.gesture(4);assert.equal(h.page.panel,'main');assert.equal(h.backCount(),0);h.widgets.find(w=>w.normal_src==='close.png'&&w.x===40).click_func();await h.widgets.find(w=>w.normal_src==='close96.png').click_func();assert.equal(h.backCount(),1);assert.ok(!calls.some(d=>d.close))
});
test('first-use Continue teaches the distinct swipe directions before Start',()=>{
 const h=host(true,{LocalStorage:class{getItem(){return false}setItem(){}}});h.widgets.find(w=>w.text==='Continue').click_func();assert.ok(h.widgets.some(w=>w.text==='Swipe right\nPrevious mode'));assert.ok(h.widgets.some(w=>w.text==='Swipe left\nNext mode'));assert.ok(h.widgets.some(w=>w.text==='Start'))
});

test('quick controls contain only Flash and Phone without duplicate Frame/Gallery controls',()=>{
 const h=host();h.page.navigate('quick');assert.ok(h.widgets.some(w=>w.text==='Flash'));assert.ok(h.widgets.some(w=>w.text==='Phone'));assert.ok(!h.widgets.some(w=>/snapshot|gallery|switch/.test(w.normal_src||'')))
});

test('reopening watch controls in the same persistent phone session does not repeat connection haptic',async()=>{
 const data={guide_v1:true};const Storage=class{getItem(k,f){return data[k]??f}setItem(k,v){data[k]=v}};let pulses=0
 async function connect(id){const h=host(true,{LocalStorage:Storage});h.page.vibrator={getType:()=>({GENTLE_SHORT:3}),stop(){},start(){pulses++}};h.page.call=async()=>{};h.page.request=async q=>q.method==='CONNECT'?{ok:true,sessionId:id}:{id:q.params.id,ok:true,state:{ready:true,mode:'Photo'}};await h.page.connectLocal()}
 await connect('phone-session-1');await connect('phone-session-1');assert.equal(pulses,1);await connect('phone-session-2');assert.equal(pulses,2)
});

test('sleep relaunch uses the official API, while deliberate touch exit returns to watch face',async()=>{
 const flags=[];let exited=0;const h=host(false,{setWakeUpRelaunch:o=>flags.push(o.relaunch),home:()=>exited++});h.page.goBack();assert.deepEqual(flags,[true]);assert.equal(exited,0);await h.widgets.find(w=>w.normal_src==='close96.png').click_func();assert.deepEqual(flags,[true,false]);assert.equal(exited,1)
});

test('photo settings use named resolutions and Auto flash has its own asset',()=>{
 const h=host();h.page.camera=cameraState(h.page.camera,{photoSize:'4032x3024',photoSizeLabel:'High',photoSizeOptions:[{label:'Auto',value:'Auto'},{label:'High',value:'4032x3024'}]});h.page.panel='settings';h.page.draw();assert.ok(h.widgets.some(w=>w.text==='High'));assert.ok(!h.widgets.some(w=>String(w.text).includes('4032x3024')))
 h.page.panel='quick';h.page.camera.flash='AUTO';h.page.draw();assert.ok(h.widgets.some(w=>w.normal_src==='flash-auto96.png'));h.page.camera.flash='ON';h.page.draw();assert.ok(h.widgets.some(w=>w.normal_src==='flash-on96.png'))
});

test('icon settings open a choice page and apply exactly the chosen timer',async()=>{
 const h=host();const requests=[];h.page.request=async q=>{requests.push(q.params);return{id:q.params.id,ok:true,state:{timer:q.params.args.timer,ready:true}}};h.page.navigate('settings');assert.ok(!h.widgets.some(w=>w.normal_src==='fps.png'));h.widgets.find(w=>w.normal_src==='timer.png').click_func();assert.equal(h.page.panel,'picker');assert.equal(h.page.settingTitle,'Timer');assert.ok(h.widgets.some(w=>w.text==='3s'));h.widgets.find(w=>w.text==='3s').click_func();await new Promise(r=>setTimeout(r,0));assert.equal(h.page.camera.timer,3);assert.equal(h.page.panel,'settings');assert.equal(requests.length,1);assert.equal(requests[0].args.timer,3);h.page.goBack();assert.equal(h.page.panel,'main')
});
test('repeated right swipes cannot accidentally exit or disconnect',()=>{
 const h=host();h.page.navigate('settings');for(let i=0;i<8;i++)h.gesture(4);assert.equal(h.backCount(),0);assert.equal(h.page.paired,true)
});

test('choice page targets fit the watch with no overlap and capture haptics stay silent',()=>{
 const h=host();let pulses=0;h.page.vibrator={stop(){},start(){pulses++}};h.page.openSetting('quality','resolution','Resolution',[{value:'HD',label:'720p'},{value:'FHD',label:'1080p'},{value:'UHD',label:'4K'}]);for(const w of h.widgets){assert.ok(w.x>=0&&w.y>=0&&w.x+w.w<=390&&w.y+w.h<=450)}const buttons=h.widgets.filter(w=>w.type==='button');for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){const a=buttons[i],b=buttons[j];assert.ok(!(a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y))}assert.equal(pulses,0);h.page.goBack();assert.equal(pulses,0)
});

test('only reviewed photos expose delete, confirmation sends its exact token once',async()=>{
 const h=host();h.page.media='data://photo.img';h.page.mediaKind='review';h.page.mediaReviewToken='exact-reviewed-photo';h.page.navigate('review');const calls=[];h.page.request=async q=>{calls.push(q.params);return{id:q.params.id,ok:true,state:{ready:true}}};h.widgets.find(w=>w.normal_src==='trash.png').click_func();assert.equal(h.page.panel,'delete');assert.equal(calls.length,0);h.page.goBack();assert.equal(h.page.panel,'review');assert.equal(calls.length,0);h.widgets.find(w=>w.normal_src==='trash.png').click_func();h.widgets.find(w=>w.normal_src==='trash-danger96.png').click_func();await new Promise(r=>setTimeout(r,0));assert.equal(calls.length,1);assert.equal(calls[0].op,'DELETE_REVIEW');assert.equal(calls[0].args.reviewToken,'exact-reviewed-photo');assert.equal(h.page.panel,'main');assert.equal(h.page.media,null)
});
test('snapshot previews never offer a delete action',()=>{
 const h=host();h.page.media='data://frame.img';h.page.mediaKind='snapshot';h.page.mediaReviewToken=null;h.page.navigate('review');assert.ok(!h.widgets.some(w=>String(w.normal_src).startsWith('trash')))
});

test('dashboard requests a nonblocking thumbnail and does not open review on arrival',async()=>{
 const h=host();const requests=[],transfers=[];h.page.camera.latestMediaKey='content://media/external/images/media/123';h.page.request=async q=>{requests.push(q.params);return{id:q.params.id,ok:true,mediaPath:'/media/'+'a'.repeat(48),mediaKey:h.page.camera.latestMediaKey,previewKind:'Photo'}};h.page.call=async d=>transfers.push(d)
 h.page.draw();await new Promise(r=>setTimeout(r,0));assert.equal(h.page.busy,false);assert.equal(requests.length,1);assert.equal(requests[0].op,'THUMBNAIL');assert.equal(transfers[0].kind,'thumbnail')
 h.page.onReceivedFile({params:{kind:'thumbnail',requestId:requests[0].id},filePath:'data://thumb.img',readyState:'transferred',on(){}});assert.equal(h.page.panel,'main');assert.ok(h.widgets.some(w=>w.src==='data://thumb.img'&&w.w===72));assert.equal(h.page.imageBusy,false);assert.equal(h.page.thumbnailKey,h.page.camera.latestMediaKey)
 h.page.camera.latestMediaKey='';h.page.draw();assert.equal(h.page.thumbnail,null);assert.ok(h.widgets.some(w=>w.normal_src==='gallery.png'))
});
test('saved-video review has a bound trash action and honest phone-playback hint',()=>{
 const h=host();h.page.media='data://video-frame.img';h.page.mediaKind='review';h.page.mediaContentKind='Video';h.page.mediaReviewToken='video-token';h.page.navigate('review');assert.ok(h.widgets.some(w=>w.text==='Video'));assert.ok(h.widgets.some(w=>w.normal_src==='trash.png'));h.widgets.find(w=>w.normal_src==='phone.png').click_func();assert.equal(h.page.hintText,'Video thumbnail. Play in phone gallery')
});
test('every transferred full image explicitly preserves aspect ratio',()=>{
 const h=host();h.page.media='data://photo.img';h.page.mediaKind='review';h.page.mediaContentKind='Photo';h.page.mediaReviewToken='token';h.page.navigate('review');assert.ok(h.widgets.some(w=>w.src==='data://photo.img'&&w.auto_scale_obj_fit===false));h.page.navigate('delete');assert.ok(h.widgets.some(w=>w.src==='data://photo.img'&&w.auto_scale_obj_fit===false))
});

test('thumbnail transfer never loses a user gallery tap',async()=>{
 const h=host();h.page.camera.latestMediaKey='one';const ops=[];h.page.call=async()=>{};h.page.request=async q=>{ops.push(q.params.op);return{id:q.params.id,ok:true,mediaPath:'/media/'+'a'.repeat(48),mediaKey:'one',previewKind:'Photo',state:{ready:true}}};h.page.draw();await new Promise(r=>setTimeout(r,0));const thumbId=h.page.expectedImageId;await h.page.send('REVIEW',{kind:'Latest'});assert.equal(h.page.queuedImageOp.op,'REVIEW');assert.deepEqual(ops,['THUMBNAIL']);h.page.onReceivedFile({params:{kind:'thumbnail',requestId:thumbId},filePath:'data://thumb.img',readyState:'transferred',on(){}});await new Promise(r=>setTimeout(r,0));assert.deepEqual(ops,['THUMBNAIL','REVIEW']);assert.equal(h.page.expectedImageKind,'review');h.page.onDestroy()
});
test('an out-of-date thumbnail cannot replace the latest capture',async()=>{
 const h=host();h.page.camera.latestMediaKey='old';h.page.call=async()=>{};h.page.request=async q=>({id:q.params.id,ok:true,mediaPath:'/media/'+'a'.repeat(48),mediaKey:h.page.camera.latestMediaKey,previewKind:'Photo'});h.page.draw();await new Promise(r=>setTimeout(r,0));const oldId=h.page.expectedImageId;h.page.camera.latestMediaKey='new';h.page.onReceivedFile({params:{kind:'thumbnail',requestId:oldId},filePath:'data://old.img',readyState:'transferred',on(){}});assert.equal(h.page.thumbnail,null);assert.ok(!h.widgets.some(w=>w.src==='data://old.img'));h.page.onDestroy()
});

test('all saved-video modes have separate phone and trash targets; frames never do',()=>{
 const h=host();for(const kind of ['Video','Slow Motion','Timelapse']){h.page.mediaKind='review';h.page.mediaContentKind=kind;h.page.mediaReviewToken='token_'+kind;h.page.panel='review';h.page.draw();const phone=h.widgets.find(w=>w.normal_src==='phone.png'),trash=h.widgets.find(w=>w.normal_src==='trash.png');assert.equal(phone.x,16);assert.equal(trash.x,159);assert.ok(phone.x+phone.w<trash.x);trash.click_func();assert.equal(h.page.panel,'delete');h.page.goBack();assert.equal(h.page.panel,'review')}h.page.mediaKind='snapshot';h.page.mediaReviewToken=null;h.page.panel='review';h.page.draw();assert.ok(!h.widgets.some(w=>String(w.normal_src).startsWith('trash')))
});

test('recording locks mode swipes but the explicit close control remains available',()=>{
 const h=host();h.page.camera.mode='Video';h.page.camera.recording=true;h.page.draw();h.gesture(4);assert.equal(h.page.camera.mode,'Video');assert.equal(h.page.panel,'main');h.widgets.find(w=>w.normal_src==='close.png'&&w.x===40).click_func();assert.equal(h.page.panel,'exit');assert.equal(h.page.camera.recording,true);assert.equal(h.backCount(),0)
});

test('every mode has one capture control and no redundant dashboard frame icon',()=>{
 const h=host();for(const mode of MODES){h.page.camera.mode=mode;h.page.panel='main';h.page.draw();assert.equal(h.widgets.filter(w=>w.src==='shutter.png'||w.src==='record.png').length,1);assert.ok(!h.widgets.some(w=>String(w.normal_src||'').startsWith('snapshot')));assert.ok(h.widgets.some(w=>w.normal_src==='gallery.png'))}
});
test('busy feedback appears once instead of repeating under the shutter',()=>{
 const h=host();h.page.busy=true;h.page.draw();h.page.updateLabels();assert.equal(h.widgets.filter(w=>w.text==='Please wait…').length,1)
});

test('transport timeout keeps Move phone closer during repeated reconnect attempts',async()=>{
 const h=host();h.page.paired=false;h.page.panel='connection';h.page.request=async()=>{throw Error('request timeout')};for(let i=0;i<3;i++){await h.page.connectLocal();assert.ok(h.widgets.some(w=>w.text==='Move phone closer'));assert.ok(!h.widgets.some(w=>w.text==='Open phone app'))}
});
test('explicit phone-app-closed response asks Open phone app instead of range advice',async()=>{
 const h=host();h.page.paired=false;h.page.panel='connection';h.page.request=async()=>({ok:false,error:'PHONE_APP_CLOSED: open WristSnap on phone'});await h.page.connectLocal();assert.ok(h.widgets.some(w=>w.text==='Open phone app'));assert.ok(!h.widgets.some(w=>w.text==='Move phone closer'))
});
test('recovery preserves an existing recording and never replays shutter or stop',async()=>{
 const h=host();h.page.paired=false;h.page.panel='connection';h.page.photoFirst=false;const ops=[];h.page.call=async()=>{};h.page.request=async q=>{ops.push(q.method==='CONNECT'?'CONNECT':q.params.op);return q.method==='CONNECT'?{ok:true,sessionId:'same-session'}:{id:q.params.id,ok:true,state:{mode:'Video',recording:true,ready:true,startedAt:1234}}};await h.page.connectLocal();assert.equal(h.page.panel,'main');assert.equal(h.page.camera.recording,true);assert.equal(h.page.camera.startedAt,1234);assert.deepEqual(ops,['CONNECT','STATUS'])
});

test('resource warnings are conditional, typed and use only the existing footer',()=>{const h=host();const count=h.widgets.length;assert.equal(h.page.camera.resourceWarning,'');h.page.camera=cameraState(h.page.camera,{resourceWarning:'storage_low'});h.page.draw();assert.equal(h.widgets.length,count);assert.equal(h.widgets.filter(w=>w.text==='Storage low').length,1);assert.equal(cameraState(h.page.camera,{resourceWarning:'INVALID'}).resourceWarning,'storage_low');h.page.camera=cameraState(h.page.camera,{resourceWarning:''});h.page.draw();assert.equal(h.widgets.filter(w=>w.text==='Storage low').length,0)});

test('photo shots picker uses 1/3/5 only and stays out of video settings',()=>{const h=host();h.page.navigate('settings');h.widgets.find(w=>w.normal_src==='multishot.png').click_func();assert.equal(h.page.settingKey,'shotCount');assert.deepEqual(Array.from(h.page.settingOptions,v=>v.value),[1,3,5]);assert.equal(cameraState({}, {shotCount:2}).shotCount,1);h.page.camera.mode='Video';h.page.panel='settings';h.page.draw();assert.ok(!h.widgets.some(w=>w.normal_src==='multishot.png'))});
test('history swipes select adjacent exact handles, clamp ends and keep Back explicit',()=>{const h=host();h.page.historyActive=true;h.page.history=[{id:'one',kind:'Photo'},{id:'two',kind:'Video'}];h.page.panel='review';h.page.media='photo.img';h.page.historyIndex=0;const selected=[];h.page.loadHistory=i=>{selected.push(i);h.page.historyIndex=i};h.gesture(4);assert.equal(h.page.panel,'review');assert.equal(selected.length,0);h.gesture(3);assert.deepEqual(selected,[1]);h.gesture(3);assert.deepEqual(selected,[1]);h.gesture(4);assert.deepEqual(selected,[1,0]);h.page.goBack();assert.equal(h.page.panel,'main');assert.equal(h.backCount(),0)});
test('late history image never reopens review after touch Back',()=>{const h=host();h.page.historyActive=true;h.page.historyGeneration=1;h.page.historyImageGeneration=1;h.page.panel='review';h.page.imageBusy=true;h.page.expectedImageKind='review';h.page.expectedImageId='history';h.page.goBack();h.page.onReceivedFile({params:{kind:'review',requestId:'history'},filePath:'old.img',readyState:'transferred',on(){}});assert.equal(h.page.panel,'main');assert.equal(h.page.imageBusy,false)});

test('guide labels and arrows match actual camera pager directions',()=>{const h=host();h.page.introVisible=true;h.page.introStep=1;h.page.draw();assert.ok(h.widgets.some(w=>w.text==='Camera modes'));assert.ok(h.widgets.some(w=>w.text==='Swipe left\nNext mode'));assert.ok(h.widgets.some(w=>w.src==='back96.png'&&w.y===108));assert.ok(h.widgets.some(w=>w.text==='Swipe right\nPrevious mode'));assert.ok(h.widgets.some(w=>w.src==='next96.png'&&w.y===222));h.page.introVisible=false;h.page.camera.mode='Photo';h.page.panel='main';h.page.request=async q=>({id:q.params.id,ok:true,state:{mode:q.params.args.mode}});h.gesture(3);assert.equal(h.page.pendingMode,'Video')});

test('idle watch has no one-second clock or paired reconnect wakeups',()=>{const intervals=new Map();let next=0;const h=host(false,{setInterval:(fn,ms)=>{intervals.set(++next,ms);return next},clearInterval:id=>intervals.delete(id)});assert.deepEqual([...intervals.values()],[2000]);h.page.camera.recording=true;h.page.draw();assert.deepEqual([...intervals.values()].sort((a,b)=>a-b),[1000,2000]);h.page.camera.recording=false;h.page.draw();assert.deepEqual([...intervals.values()],[2000]);h.page.paired=false;h.page.draw();assert.deepEqual([...intervals.values()],[5000]);h.page.manualDisconnect=true;h.page.draw();assert.equal(intervals.size,0)});
test('unchanged watch status performs no repeated text writes',()=>{const h=host();let updates=0;h.widgets.forEach(w=>{const original=w.setProperty;w.setProperty=(k,v)=>{updates++;original.call(w,k,v)}});for(let i=0;i<20;i++)h.page.updateLabels();assert.equal(updates,0)});

test('front rear switch sits right of shutter on every camera mode',()=>{const h=host();for(const mode of MODES){h.page.camera.mode=mode;h.page.draw();const flip=h.widgets.filter(w=>w.normal_src==='switch.png');assert.equal(flip.length,1);assert.equal(flip[0].x,278);assert.equal(flip[0].y,316);assert.ok(flip[0].w>=72);assert.ok(!h.widgets.some(w=>w.normal_src==='switch.png'&&w.y<120))}});

test('watch countdown uses matching phone progress and releases its clock when complete',async()=>{let clock=10000;const FakeDate=class extends Date{static now(){return clock}};const h=host(false,{Date:FakeDate});let resolve;h.page.camera.timer=3;h.page.request=q=>new Promise(r=>resolve=()=>r({id:q.params.id,ok:true,state:{mode:'Photo',ready:true}}));const pending=h.page.send('SHUTTER');const id=h.page.pendingCaptureId;h.page.onCall({captureProgress:{requestId:'stale',remainingMs:3000}});assert.equal(h.page.captureCountdownDeadline,0);h.page.onCall({captureProgress:{requestId:id,remainingMs:3000}});assert.equal(h.page.clock.text,'3');clock+=1000;h.page.onCall({captureProgress:{requestId:id,remainingMs:3000}});h.page.updateLabels();assert.equal(h.page.clock.text,'2');clock+=1000;h.page.updateLabels();assert.equal(h.page.clock.text,'1');resolve();await pending;assert.equal(h.page.captureCountdownDeadline,0);assert.equal(h.page.tick,null);h.page.onCall({captureProgress:{requestId:id,remainingMs:3000}});assert.equal(h.page.captureCountdownDeadline,0)});


test('gallery stays within screen with fixed controls and no zoom or drag handlers',()=>{const h=host();h.page.media='photo.img';h.page.mediaKind='review';h.page.mediaReviewToken='exact';h.page.historyActive=true;h.page.history=[{id:'one',kind:'Photo'},{id:'two',kind:'Photo'}];h.page.panel='review';h.page.draw();const image=h.widgets.find(w=>w.src==='photo.img');assert.deepEqual([image.x,image.y,image.w,image.h],[0,0,390,450]);assert.equal(image.auto_scale_obj_fit,false);assert.deepEqual(Object.keys(image.listeners),[]);assert.ok(!h.widgets.some(w=>/^(plus|minus)(96)?\.png$/.test(w.normal_src||'')));const controls=h.widgets.filter(w=>w.normal_src).map(w=>[w.normal_src,w.x,w.y]);let selected=null;h.page.loadHistory=i=>selected=i;h.gesture(3);assert.equal(selected,1);assert.deepEqual(h.widgets.filter(w=>w.normal_src).map(w=>[w.normal_src,w.x,w.y]),controls);for(const w of h.widgets)assert.ok(w.x>=0&&w.y>=0&&w.x+w.w<=390&&w.y+w.h<=450);h.page.goBack();assert.equal(h.page.panel,'main')});


test('phone mode changes update the watch dashboard in both directions from read-only status', async () => {
 const intervals = new Map()
 let next = 0
 const h = host(false, {
  setInterval: (fn, ms) => { intervals.set(++next, {fn, ms}); return next },
  clearInterval: id => intervals.delete(id)
 })
 let phoneMode = 'Photo'
 const operations = []
 h.page.request = async ({params}) => {
  operations.push(params.op)
  return {id: params.id, ok: true, state: {mode: phoneMode, ready: true}}
 }
 const refresh = [...intervals.values()].find(timer => timer.ms === 2000)
 assert.ok(refresh, 'connected camera dashboard refreshes every two seconds')
 for (const mode of ['Video', 'Slow Motion', 'Timelapse', 'Photo']) {
  phoneMode = mode
  await refresh.fn()
  assert.equal(h.page.modeWidget.text, mode)
  assert.ok(h.widgets.some(w => w.src === (mode === 'Photo' ? 'shutter.png' : 'record.png')))
 }
 assert.deepEqual(operations, ['STATUS', 'STATUS', 'STATUS', 'STATUS'])
 h.page.navigate('settings')
 assert.deepEqual([...intervals.values()].map(timer => timer.ms), [10000])
 h.page.onDestroy()
 assert.equal(intervals.size, 0)
})

test('watch exit acknowledges detachment before leaving and resets the page display timeout', async () => {
 const calls=[]
 const h=host(false,{
  setPageBrightTime:({brightTime})=>calls.push(brightTime),
  resetPageBrightTime:()=>calls.push('display reset')
 })
 let acknowledge
 h.page.request=({method})=>{
  calls.push(method)
  return new Promise(resolve=>{acknowledge=resolve})
 }
 const exit=h.page.exitControls()
 assert.equal(h.backCount(),0)
 await h.page.send('STATUS')
 assert.deepEqual(calls,[60000,'DETACH'])
 acknowledge({ok:true})
 await exit
 assert.deepEqual(calls,[60000,'DETACH','display reset'])
 assert.equal(h.backCount(),1)
})

test('OS page destruction reports presence loss without sending the phone stop command', ()=>{
 const h=host()
 const calls=[]
 h.page.call=async data=>calls.push(data.detach?'detach':data.close?'stop':'other')
 h.page.onDestroy()
 assert.deepEqual(calls,['detach'])
})
