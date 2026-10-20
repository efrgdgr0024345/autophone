#!/usr/bin/env python3
"""Validate data hashes, preserved cards, reference links, lesson graphs and offline boundaries."""
import base64,csv,gzip,hashlib,io,json,re,shlex
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def verify(root=ROOT, schema=True):
 raw=(root/'catalog/offline-catalog.json').read_bytes();d=json.loads(raw);m=json.loads((root/'catalog/MANIFEST.json').read_text());asset=(root/'app/src/main/assets/offline_learning_v2.catalog').read_bytes()
 assert gzip.decompress(asset)==raw,'Bundled asset differs from public JSON'
 assert hashlib.sha256(raw).hexdigest()==m['json_sha256'];assert hashlib.sha256(asset).hexdigest()==m['asset_gzip_sha256']
 if schema:
  import jsonschema
  jsonschema.Draft202012Validator(json.loads((root/'catalog/offline-catalog.schema.json').read_text())).validate(d)
 def keyed(name):
  a=d[name];keys=[x['id'] for x in a];assert len(keys)==len(set(keys)),f'Duplicate {name} IDs';return {x['id']:x for x in a}
 commands=keyed('command_examples');tools=keyed('tools');entries=keyed('command_inventory');references=keyed('reference_inventory');assert not set(entries)&set(references);entries.update(references);lessons=keyed('lessons');sources=keyed('sources')
 base=base64.b64decode(''.join((root/f'app/src/main/assets/linux_commands_{n:02}.b64').read_text().strip() for n in range(7)));legacy=list(csv.DictReader(io.StringIO(gzip.decompress(base).decode()),delimiter='\t'))
 for old in legacy:
  c=commands[old['id']]
  for a,b in [('id','id'),('category','category'),('topic','topic'),('title','title'),('command','command'),('explanation','explanation'),('expected','expected_result'),('risk','risk'),('compatibility','compatibility')]:assert c[b]==old[a],f'Preserved card changed: {old["id"]}/{a}'
  assert c['requires_admin']==(old['admin']=='true')
 for c in commands.values():
  line=c['command'];assert 0<len(line)<=512 and all(32<=ord(x)<=126 for x in line),'Nonprintable/oversized command'
  assert not any(x in line.lower() for x in ['rm -rf /','mkfs.','wipefs ',':(){','| sh','| bash']), 'Excluded command fragment'
  assert not re.search(r'<[A-Z_]+>|\{\{.*?\}\}',line),'Unresolved placeholder in sendable example'
  assert all(r['source_id'] in sources for r in c['source_refs'])
  assert all(x in lessons for x in c['lesson_ids'])
  assert c['verification']['success_is_not_assumed'] is True
  if c['id'].startswith('learn-') and (line.startswith(('rm ','rmdir ','mkdir ','cp ')) or ' > ' in line):
   assert '/tmp/blackcat-offline-lab' in line or '/tmp/blackcat-git-lab' in line,'New practice write escaped owned workspace'
   assert c['risk'] in ['medium','high']
 for t in tools.values():assert all(x in entries for x in t['entry_ids']) and all(x in sources for x in t['source_ids'])
 for e in entries.values():assert all(x in tools for x in e['tool_ids']) and all(x['source_id'] in sources for x in e['source_refs'])
 for l in lessons.values():
  ids=[s['id'] for s in l['steps']];assert len(ids)==len(set(ids)) and 1<=len(ids)<=40
  assert all(s['command_id'] in commands for s in l['steps'])
  assert all(x in lessons for x in l['prerequisite_lesson_ids']+l['next_lesson_ids'])
  edges={s['id']:[e['next_step_id'] for e in s['transitions'] if e['next_step_id'] is not None] for s in l['steps']}
  def visit(node,path):
   assert node in edges,'Dangling step transition';assert node not in path,'Automatic step cycle'
   for child in edges[node]:visit(child,path|{node})
  for node in edges:visit(node,set())
  assert l['automation_policy']=='manual_type_only_no_enter'
 for l in lessons.values():
  def parents(node,path):
   assert node not in path,'Lesson prerequisite cycle'
   for child in lessons[node]['prerequisite_lesson_ids']:parents(child,path|{node})
  parents(l['id'],set())
 for path in d['learning_paths']:assert all(x in lessons for x in path['lesson_ids'])
 counts=d['counts'];assert counts==m['counts']
 expected={'tool_or_package_groups':len(tools),'documented_entries_total':len(entries),'documented_cli_entries':sum(x['kind'] in ['binary','subcommand','shell_builtin'] for x in entries.values()),'distinct_binary_names':sum(x['kind']=='binary' for x in entries.values()),'builtin_only_entries':sum(x['kind']=='shell_builtin' for x in entries.values()),'shell_builtin_names':sum('shell_builtin' in x.get('invocation_kinds',[]) for x in entries.values()),'subcommand_entries':sum(x['kind']=='subcommand' for x in entries.values()),'service_unit_references':sum(x['kind']=='service_unit' for x in entries.values()),'manual_reference_entries':sum(x['kind']=='manual_reference' for x in entries.values()),'command_example_entries':len(commands),'distinct_command_example_lines':len({x['command'] for x in commands.values()}),'legacy_example_entries':len(legacy),'added_example_entries':len(commands)-len(legacy),'lessons':len(lessons),'lesson_steps':sum(len(x['steps']) for x in lessons.values()),'example_categories':len(d['taxonomy']),'example_topics':sum(len(x['topics']) for x in d['taxonomy']),'learning_paths':len(d['learning_paths']),'workflow_templates':len(d['workflow_templates']),'source_documents':len(d['research']['source_documents']),'observed_help_invocations':sum(len(x['observed_help_invocations']) for x in entries.values())}
 for key,value in expected.items():assert counts[key]==value,f'Wrong count: {key}'
 assert counts['kali_package_sections']==sum(len(t.get('packages',[])) for t in tools.values())
 assert any(x['category']=='Kali Linux' for x in commands.values())
 assert not {'tc cake','openssl format-options'} & {e['invocation'] for e in entries.values() if e['kind'] in ['binary','subcommand']},'Manual topic was invented as a runnable command'
 assert {'fsck.minix','mkfs.bfs','sha256sum'}<={e['invocation'] for e in entries.values()},'CLI extractor lost dotted or shared-manual names'
 for name in ['OfflineLearningCatalog.kt','LinuxCommandLibraryPanel.kt']:
  source=(root/'app/src/main/java/com/blackcat/remote'/name).read_text()
  assert not any(x in source for x in ['HttpsURLConnection','URL(','ProcessBuilder(','Runtime.getRuntime','KEYCODE_ENTER'])
 assert d['safety']['offline_phone_network'] is False and d['safety']['auto_command_policy_changed'] is False
 print('PASS: '+str(counts['documented_cli_entries'])+' CLI entries, '+str(len(commands))+' example cards, '+str(len(lessons))+' linked lessons; hashes, preserved cards, links, counts and manual-only boundary')
 return d
if __name__=='__main__':verify()
