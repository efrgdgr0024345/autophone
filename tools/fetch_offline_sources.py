#!/usr/bin/env python3
"""Reproduce the pinned research inputs; fetch documentation only, never install or run tools."""
import argparse,concurrent.futures,hashlib,io,json,shutil,tarfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--output',required=True,type=Path);a=ap.parse_args();out=a.output.resolve()
 if out==ROOT or ROOT in out.parents:ap.error('Keep research caches outside the application checkout.')
 out.mkdir(parents=True,exist_ok=True);data=json.loads((ROOT/'catalog/research/upstream-inventory.json').read_text())
 def fetch(url,path,expected,maximum):
  if path.exists():
   raw=path.read_bytes()
  else:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'BlackCat-pinned-offline-research/1.0'}),timeout=60) as r:
    raw=r.read(maximum+1)
   if len(raw)>maximum:raise ValueError('Source exceeded download budget')
  if hashlib.sha256(raw).hexdigest()!=expected:raise ValueError('Source hash mismatch: '+path.name)
  path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);return raw
 kali=json.loads((ROOT/'catalog/research/kali-source.json').read_text());raw=fetch(kali['url'],out/'kali-tools.tar.gz',kali['sha256'],50331648)
 total=0
 with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
  for member in tar.getmembers():
   if not member.isfile() or not member.name.endswith('.md'):continue
   parts=Path(member.name).parts
   if Path(member.name).is_absolute() or '..' in parts or len(parts)<2:raise ValueError('Unsafe source archive path')
   if member.size>3000000:raise ValueError('Oversized source document')
   total+=member.size
   if total>100000000:raise ValueError('Source archive expanded beyond budget')
   target=out/'kali-docs'/Path(*parts[1:]);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(member).read())
 shutil.copy2(ROOT/'catalog/research/kali-source.json',out/'kali-source.json')
 records=json.loads((ROOT/'catalog/research/upstream-retrievals.json').read_text())
 def one(record):
  if record['http_status']!=200:raise ValueError('Cannot reproduce a failed source retrieval')
  if '..' in Path(record['path']).parts:raise ValueError('Unsafe source document path')
  fetch(record['url'],out/'upstream'/record['family']/record['path'],record['sha256'],3000000)
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:list(pool.map(one,records['files']))
 for source in data['sources']:
  if source['id'].startswith('upstream-'):
   family=source['id'][len('upstream-'):]
   (out/(family+'-commit.json')).write_text(json.dumps({'commit':source['commit'],'repository':source['repository'].removeprefix('https://github.com/'),'tree_sha':source['git_tree_sha']}))
 for name in ['upstream-retrievals.json','retrievals.json']:shutil.copy2(ROOT/'catalog/research'/name,out/name)
 print(json.dumps({'pinned_upstream_files':len(records['files']),'kali_archive_verified':True,'cache':str(out),'executed_tools':False}))
if __name__=='__main__':main()
