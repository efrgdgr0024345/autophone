import os,json,urllib.request,urllib.error,ssl,subprocess,time,datetime,hashlib,array,math
from pathlib import Path
OUT=Path(__file__).resolve().parent
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):return None
opener=urllib.request.build_opener(NoRedirect,urllib.request.HTTPSHandler(context=ssl.create_default_context()))
results=[]
for name in ('plan','preflight','clipped','page','revision','speech'):
 body=(OUT/(name+'.request.json')).read_bytes()
 endpoint='https://api.openai.com/v1/'+('audio/speech' if name=='speech' else 'responses')
 req=urllib.request.Request(endpoint,data=body,headers={'Authorization':'Bearer '+os.environ['BLACKCAT_TEST_API_KEY2'],'Content-Type':'application/json; charset=utf-8'},method='POST')
 started=time.monotonic(); item={'scenario':name,'requested_model':json.loads(body)['model'],'request_sha256':hashlib.sha256(body).hexdigest()}
 try:
  with opener.open(req,timeout=180 if name in ('plan','revision') else 25 if name=='speech' else 60) as response:
   item.update(http=response.status,content_type=response.headers.get_content_type())
   limit=2400000 if name=='speech' else 262144
   payload=response.read(limit+1)
   if len(payload)>limit:raise ValueError('Response exceeds production limit')
  if name=='speech':
   if item['content_type'] not in ('audio/pcm','audio/L16','application/octet-stream'):raise ValueError('Unexpected production PCM content type')
   if not payload or len(payload)%2:raise ValueError('Invalid PCM size')
   samples=array.array('h',payload); rms=math.sqrt(sum(s*s for s in samples)/len(samples))
   if len(samples)<2400 or rms<1:raise ValueError('Empty or silent audio')
   item.update(passed=True,pcm_bytes=len(payload),duration_seconds=len(samples)/24000,sample_rms=round(rms,2),phone_playback_tested=False)
   (OUT/'speech.pcm').write_bytes(payload)
  else:
   data=json.loads(payload);item.update(returned_model=data.get('model'),usage=data.get('usage'))
   (OUT/(name+'.response.json')).write_bytes(payload)
   p=subprocess.run(['/workspace/.autophone-tools/jdk-17/bin/java','-cp',(OUT/'classpath.txt').read_text(),'ProductionCodecCheck',name],capture_output=True,text=True)
   if p.returncode:
    item.update(passed=False,production_parser_or_behavior_check='failed',assertion=p.stderr.splitlines()[0][:300] if p.stderr else 'no diagnostic')
   else:item.update(json.loads((OUT/(name+'.parsed.json')).read_text()))
 except urllib.error.HTTPError as e:
  try:err=json.loads(e.read(16384)).get('error',{})
  except Exception:err={}
  # Persist only bounded non-sensitive codes, never provider text or credentials.
  item.update(http=e.code,passed=False,provider_error_code=err.get('code'),provider_error_type=err.get('type'),provider_error_param=err.get('param'))
 except Exception as e:item.update(passed=False,failure_type=type(e).__name__)
 item['elapsed_seconds']=round(time.monotonic()-started,2);results.append(item)
 print(json.dumps(item),flush=True)
 report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source_sha':'887a98d37d23f95714aada1f5d094a885729198e','artifact_sha256':hashlib.sha256(Path('/workspace/autophone/app/build/outputs/apk/ai/debug/app-ai-debug.apk').read_bytes()).hexdigest(),'method':'Host HTTPS requests serialized and parsed by unchanged compiled production Kotlin codecs; synthetic data only. No target commands executed.','key_saved_or_logged':False,'physical_bluetooth_tested':False,'phone_camera_openai_transport_tested':False,'results':results,'passed':len(results)==6 and all(r.get('passed') for r in results)}
 (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n')
 if item.get('http') in (401,403,429):break
raise SystemExit(0 if report['passed'] else 1)
