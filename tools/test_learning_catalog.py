#!/usr/bin/env python3
import copy,gzip,hashlib,json,shutil,tempfile,unittest
from pathlib import Path
from check_learning_catalog import verify,ROOT

class LearningIntegrityTests(unittest.TestCase):
 def mutate(self,change):
  with tempfile.TemporaryDirectory() as tmp:
   dst=Path(tmp);shutil.copytree(ROOT/'catalog',dst/'catalog')
   assets=dst/'app/src/main/assets';assets.mkdir(parents=True)
   for p in (ROOT/'app/src/main/assets').glob('linux_commands_*.b64'):shutil.copy2(p,assets/p.name)
   src=dst/'app/src/main/java/com/blackcat/remote';src.mkdir(parents=True)
   for name in ['OfflineLearningCatalog.kt','LinuxCommandLibraryPanel.kt','OfflineWorkflows.kt','OfflineCommandComposer.kt']:shutil.copy2(ROOT/'app/src/main/java/com/blackcat/remote'/name,src/name)
   data=json.loads((dst/'catalog/offline-catalog.json').read_text());change(data)
   raw=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();packed=gzip.compress(raw,mtime=0)
   (dst/'catalog/offline-catalog.json').write_bytes(raw);(assets/'offline_learning_v2.catalog').write_bytes(packed)
   manifest=json.loads((dst/'catalog/MANIFEST.json').read_text());manifest.update(json_sha256=hashlib.sha256(raw).hexdigest(),asset_gzip_sha256=hashlib.sha256(packed).hexdigest(),counts=data['counts']);(dst/'catalog/MANIFEST.json').write_text(json.dumps(manifest))
   with self.assertRaises(AssertionError):verify(dst,schema=False)
 def test_dangling_next_step(self):self.mutate(lambda d:d['lessons'][0]['steps'][0]['transitions'][0].update(next_step_id='missing'))
 def test_step_loop(self):self.mutate(lambda d:d['lessons'][0]['steps'][0]['transitions'][0].update(next_step_id=d['lessons'][0]['steps'][0]['id']))
 def test_changed_legacy_command(self):self.mutate(lambda d:d['command_examples'][0].update(command='whoami'))
 def test_newline_in_new_command(self):self.mutate(lambda d:d['command_examples'][-1].update(command='id\nwhoami'))
 def test_inflated_count(self):self.mutate(lambda d:d['counts'].update(documented_cli_entries=d['counts']['documented_cli_entries']+1))
 def test_dangling_source(self):self.mutate(lambda d:d['command_examples'][-1]['source_refs'][0].update(source_id='unknown'))
 def test_automatic_execution_cannot_replace_manual_lesson_boundary(self):self.mutate(lambda d:d['lessons'][0].update(automation_policy='run_all'))
 def test_missing_cli_workflow(self):self.mutate(lambda d:d['workflow_support']['command_workflows'].pop())
 def test_invented_help_invocation(self):self.mutate(lambda d:d['workflow_support']['command_workflows'][0].update(observed_help_command='nmap --invented'))
 def test_broad_auto_permission(self):self.mutate(lambda d:d['workflow_support']['tool_workflows'][0].update(automatic_execution=True))
 def test_unreviewed_discovery_command(self):self.mutate(lambda d:d['workflow_support']['command_workflows'][0]['discovery_commands'].__setitem__(0,'rm /tmp/example'))
if __name__=='__main__':unittest.main()
