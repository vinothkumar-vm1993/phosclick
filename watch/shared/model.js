export const MODES=['Photo','Video','Slow Motion','Timelapse']
export function modeAfter(mode,delta){return MODES[Math.max(0,Math.min(MODES.length-1,Math.max(0,MODES.indexOf(mode))+delta))]}
export function elapsed(start,now){if(!Number.isFinite(start)||!Number.isFinite(now))return '00:00';const n=Math.max(0,Math.floor((now-start)/1000));return String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0')}
export function nextValue(values,current){return values.length?values[(values.indexOf(current)+1)%values.length]:current}

// Accept only typed camera fields; missing/null values cannot erase confirmed state.
export function cameraState(previous={},incoming={}){
 const out={shotCount:1,resourceWarning:'',latestMediaKey:'',mode:'Photo',quality:'HD',fps:30,timer:0,aspect:'4:3',photoSize:'Auto',photoSizeLabel:'Auto',photoSizeOptions:[],zoomRatio:1,minZoom:1,maxZoom:1,flash:'OFF',torch:false,recording:false,...previous}
 if(!incoming||typeof incoming!=='object'||Array.isArray(incoming))return out
 const choices={shotCount:[1,3,5],resourceWarning:['','battery_low','storage_low','storage_critical'],mode:MODES,quality:['HD','FHD','UHD'],fps:[30,60,120,240],timer:[0,3,10],aspect:['4:3','16:9'],flash:['OFF','AUTO','ON']}
 Object.keys(choices).forEach(k=>{if(choices[k].includes(incoming[k]))out[k]=incoming[k]})
 ;['torch','recording','front','ready','previewReady','flashAvailable'].forEach(k=>{if(typeof incoming[k]==='boolean')out[k]=incoming[k]})
 if(typeof incoming.latestMediaKey==='string'&&incoming.latestMediaKey.length<200)out.latestMediaKey=incoming.latestMediaKey
 if(typeof incoming.photoSize==='string'&&/^(Auto|[0-9]+x[0-9]+)$/.test(incoming.photoSize))out.photoSize=incoming.photoSize
 if(['Auto','High','Standard','Small'].includes(incoming.photoSizeLabel))out.photoSizeLabel=incoming.photoSizeLabel
 if(Array.isArray(incoming.photoSizeOptions))out.photoSizeOptions=incoming.photoSizeOptions.filter(v=>v&&['Auto','High','Standard','Small'].includes(v.label)&&typeof v.value==='string'&&/^(Auto|[0-9]+x[0-9]+)$/.test(v.value)).map(v=>({label:v.label,value:v.value}))
 if(Number.isFinite(incoming.minZoom)&&incoming.minZoom>0)out.minZoom=incoming.minZoom
 if(Number.isFinite(incoming.maxZoom)&&incoming.maxZoom>=out.minZoom)out.maxZoom=incoming.maxZoom
 if(Number.isFinite(incoming.zoomRatio)&&incoming.zoomRatio>0)out.zoomRatio=Math.max(out.minZoom,Math.min(out.maxZoom,incoming.zoomRatio))
 if(Number.isFinite(incoming.startedAt)&&incoming.startedAt>=0)out.startedAt=incoming.startedAt
 ;['qualities','frameRates','photoSizes'].forEach(k=>{if(Array.isArray(incoming[k]))out[k]=[...new Set(incoming[k].filter(v=>k==='qualities'?choices.quality.includes(v):k==='frameRates'?choices.fps.includes(v):typeof v==='string'&&/^(Auto|[0-9]+x[0-9]+)$/.test(v)))]})
 return out
}

// Keep bridge codes in state for reconnect logic, never in watch-facing copy.
export function noticeText(value,maxLines=3){
 const raw=String(value||'').replace(/Pixel/gi,'phone')
 let message=/PHONE_APP_CLOSED|PHONE_INACTIVE|open.*camera.*phone|keep zepp connected/i.test(raw)?'Open phone app'
  :/AUTH_REQUIRED|WATCH_REQUIRED/i.test(raw)?'Connect phone'
  :/connection lost|move phone closer/i.test(raw)?'Move phone closer'
  :/allow camera control/i.test(raw)?'Allow on phone'
  :raw.replace(/^Error:\s*/i,'').replace(/\b[A-Z][A-Z_]{3,}:?\s*/g,'').trim()||'Open phone app'
 const words=message.split(/\s+/),lines=[];let line=''
 for(let word of words){
  while(word.length>20){if(line){lines.push(line);line=''}lines.push(word.slice(0,20));word=word.slice(20)}
  if(line&&line.length+word.length+1>20){lines.push(line);line=''}
  line+=(line?' ':'')+word
 }
 if(line)lines.push(line)
 if(lines.length>maxLines){lines.length=maxLines;lines[maxLines-1]=lines[maxLines-1].slice(0,19)+'…'}
 return lines.join('\n')
}
