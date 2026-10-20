#!/usr/bin/env python3
"""Extract factual CLI inventory from cached, pinned upstream documentation; no execution.

Usage: python3 tools/research_offline_catalog.py /workspace/security-review/ui15/research
Network acquisition is separate: this parser neither downloads nor runs tools.
"""
import argparse, collections, hashlib, html, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def slug(s):
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-') or 'unnamed'
def text(s):
    s = re.sub(r'<[^>]+>', ' ', s)
    for _ in range(4): s = re.sub(r'@[A-Za-z]+\{([^{}]*)\}', r'\1', s)
    return re.sub(r'\s+', ' ', html.unescape(s).replace('**', '').replace('`', '')).strip()
def digest(b): return hashlib.sha256(b).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('cache',type=Path);args=ap.parse_args();cache=args.cache
    groups={};entries={};sources=[];documents=[];excluded=[]
    def group(name, family, summary='', **extra):
        ident=slug(name)
        if ident not in groups:groups[ident]={'id':ident,'name':name,'record_kind':'tool_or_package_group','summary':summary,'families':[],'distributions':[],'source_ids':[],'executable_ids':[],**extra}
        g=groups[ident]
        if family not in g['families']:g['families'].append(family)
        if not g.get('summary'):g['summary']=summary
        return g
    def entry(name, invocation, kind, g, source, path, summary='', flags=(), help_commands=()):
        if not invocation or len(invocation)>200:return
        key=invocation
        if key not in entries:entries[key]={'id':'cli-'+digest(invocation.encode())[:18],'name':name,'invocation':invocation,'kind':kind,'tool_ids':[],'summary':summary,'documented_flags':[],'observed_help_invocations':[],'source_refs':[],'execution_status':'reference_only_not_a_reviewed_action'}
        e=entries[key]
        e.setdefault('invocation_kinds', [e['kind']])
        if kind not in e['invocation_kinds']:e['invocation_kinds'].append(kind)
        if e['kind']=='manual_reference' and kind!='manual_reference':e['kind']=kind
        if not e['summary'] and summary:e['summary']=summary
        if g['id'] not in e['tool_ids']:e['tool_ids'].append(g['id'])
        if e['id'] not in g['executable_ids']:g['executable_ids'].append(e['id'])
        e['documented_flags']=sorted(set(e['documented_flags'])|set(flags))
        e['observed_help_invocations']=sorted(set(e['observed_help_invocations'])|set(help_commands))
        ref={'source_id':source,'path':path}
        if ref not in e['source_refs']:e['source_refs'].append(ref)
    kali=json.loads((cache/'kali-source.json').read_text());sources.append({**kali,'id':'kali-tools','name':'Official Kali tools documentation','publisher':'Kali Linux','documentation_license':'No repository-wide license file found in retrieved archive; upstream rights retained. Inventory uses factual metadata and short labels.'})
    package_count=0
    for p in sorted((cache/'kali-docs').glob('*/index.md')):
        s=p.read_text();m=lambda key: (re.search(r'^'+key+r':\s*(.*)$',s,re.M).group(1).strip() if re.search(r'^'+key+r':\s*(.*)$',s,re.M) else '')
        if not m('Version') or 'PackagesInfo:' not in s:excluded.append({'source_id':'kali-tools','path':p.parent.name+'/index.md','reason':'navigation/non-package page'});continue
        name=m('Title');packages=[]
        sections=list(re.finditer(r'^ ### (\S+)\s*$',s,re.M))
        for i,sec in enumerate(sections):
            body=s[sec.end():sections[i+1].start() if i+1<len(sections) else len(s)]
            title=re.search(r'^ \*\*(.+?)\*\*',body,re.M);size=re.search(r'\*\*Installed size:\*\* `([^`]+)`',body)
            deps=re.search(r'\{\{< spoiler "Dependencies:" >\}\}(.*?)\{\{< /spoiler >\}\}',body,re.S)
            packages.append({'name':sec.group(1),'summary':text(title.group(1)) if title else '', 'installed_size_reported':size.group(1) if size else None,'dependencies':re.findall(r'^ \* (\S+)',deps.group(1),re.M) if deps else [],'install_command_reference':next(iter(re.findall(r'\*\*How to install:\*\* `([^`]+)`',body)),None)})
        package_count+=len(packages)
        summary=next((x['summary'] for x in packages if x['name']==name and x['summary']),next((x['summary'] for x in packages if x['summary']),name+' package collection'))
        g=group(name,'kali',summary,version=m('Version') or None,architectures=m('Architectures').split(),metapackages=m('Metapackages').split(),homepage=m('Homepage') or None,package_repository=m('Repository') or None,packages=packages)
        g['distributions']=['kali'];g['source_ids'].append('kali-tools');g['source_path']=p.parent.name+'/index.md';g['default_metapackage_member']='kali-linux-default' in g.get('metapackages',[])
        parts=list(re.finditer(r'^ ##### (.+?)\s*$',s,re.M))
        for i,sec in enumerate(parts):
            name=sec.group(1).strip();body=s[sec.end():parts[i+1].start() if i+1<len(parts) else len(s)]
            if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9+_.:-]{0,119}',name):
                excluded.append({'source_id':'kali-tools','path':g['source_path'],'heading':name,'reason':'heading not a bare executable name'});continue
            before=body.split('```',1)[0];summary=text(re.sub(r'^ ###.*','',before,flags=re.M))[:350]
            help_commands=[x.strip() for x in re.findall(r'^ (?:root|kali)@kali:[^#\n]*[#$] (.+)$',body,re.M) if re.fullmatch(re.escape(name)+r' (?:--help|-h|--version|-V)',x.strip())]
            flags=re.findall(r'(?<![\w-])--[a-zA-Z][a-zA-Z0-9-]{1,70}',body)
            entry(name,name,'binary',g,'kali-tools',g['source_path'],summary,flags,help_commands)
        documents.append({'source_id':'kali-tools','path':g['source_path'],'sha256':digest(p.read_bytes()),'bytes':p.stat().st_size})
    for family in ['coreutils','util-linux','iproute2','systemd','git','curl','openssh','openssl']:
        meta=json.loads((cache/(family+'-commit.json')).read_text());source_id='upstream-'+family
        sources.append({'id':source_id,'name':family+' upstream source documentation','publisher':meta['repository'].split('/')[0],'repository':'https://github.com/'+meta['repository'],'commit':meta['commit'],'git_tree_sha':meta['tree_sha'],'retrieval_manifest':'catalog/research/upstream-retrievals.json','documentation_license':'See upstream documentation license at the pinned commit; no full manual is redistributed.'})
        g=group(family,'linux',family+' command-line tools and documented subcommands');g['source_ids'].append(source_id)
        if 'linux' not in g['distributions']:g['distributions'].append('linux')
        for p in sorted((cache/'upstream'/family).rglob('*')):
            if not p.is_file():continue
            rel=str(p.relative_to(cache/'upstream'/family));s=p.read_text(errors='replace');documents.append({'source_id':source_id,'path':rel,'sha256':digest(p.read_bytes()),'bytes':p.stat().st_size})
            if family=='coreutils':
                for name,node,summary in re.findall(r'^\* ([^:\n]+): \(coreutils\)([^.\n]+)\.\s*([^\n]+)',s,re.M):
                    if node.endswith('invocation') and re.fullmatch(r'[A-Za-z0-9._+\[]+',name):entry(name,name,'binary',g,source_id,rel,text(summary))
                for name in set(re.findall(r'^@pindex ([^\n]+)',s,re.M)):
                    if name!='multicall' and re.fullmatch(r'[A-Za-z0-9._+\[]+',name):entry(name,name,'binary',g,source_id,rel)
                nodes=list(re.finditer(r'^@node ([^\n]+) invocation\s*$',s,re.M))
                for i,node in enumerate(nodes):
                    name=node.group(1);body=s[node.end():nodes[i+1].start() if i+1<len(nodes) else len(s)]
                    if not re.fullmatch(r'[A-Za-z0-9._+\[]+',name):continue
                    title=re.search(r'^@(?:section|subsection) (.+)',body,re.M);summary=text(title.group(1)).partition(':')[2].strip() if title else ''
                    entry(name,name,'binary',g,source_id,rel,summary,re.findall(r'--[a-zA-Z][a-zA-Z0-9-]+',body))
            elif family=='util-linux':
                m=re.search(r'== NAME\s*\n+([^\n]+)',s);name=re.sub(r'\.[18]\.adoc$','',p.name);summary=text(m.group(1)).partition(' - ')[2] if m else ''
                entry(name,name,'binary',g,source_id,rel,summary,re.findall(r'--[a-zA-Z][a-zA-Z0-9-]+',s))
            elif family=='iproute2':
                name=p.stem;roots={'ip','ss','tc','bridge','devlink','rdma','dcb','nstat','ifstat','rtacct','rtmon','rtstat','ctstat','lnstat','arpd','genl','routel','tipc'}
                prefix=name.split('-')[0]
                is_sub='-' in name and prefix in roots and (prefix!='tc' or name in {'tc-qdisc','tc-class','tc-filter','tc-chain','tc-actions','tc-monitor','tc-exec'})
                invocation=name.replace('-',' ') if is_sub else name if name in roots else name+'(8)'
                m=re.search(r'\.SH NAME\s*\n([^\n]+)',s);summary=text(m.group(1).replace('\\-','-')) if m else ''
                entry(name,invocation,'binary' if name in roots else 'subcommand' if is_sub else 'manual_reference',g,source_id,rel,summary,re.findall(r'--[a-zA-Z][a-zA-Z0-9-]+',s))
            elif family=='systemd':
                vol=re.search(r'<manvolnum>(\d+)</manvolnum>',s)
                if not vol or vol.group(1) not in ('1','8'):continue
                summary=re.search(r'<refpurpose>(.*?)</refpurpose>',s,re.S)
                for name in re.findall(r'<refname>(.*?)</refname>',s,re.S):
                    name=text(name)
                    if re.fullmatch(r'[A-Za-z0-9._+-]+',name):
                        kind='service_unit' if re.search(r'\.(service|socket|timer|target|path)$',name) else 'binary'
                        entry(name,name,kind,g,source_id,rel,text(summary.group(1)) if summary else '',re.findall(r'--[a-zA-Z][a-zA-Z0-9-]+',s))
            elif family=='git':
                if p.name=='command-list.txt':continue
                name=p.name.rsplit('.',1)[0]
                if name=='git':invocation='git';kind='binary'
                elif name.startswith('git-'):invocation='git '+name[4:];kind='subcommand'
                else:continue
                m=re.search(r'\nNAME\n[-=]+\n([^\n]+)',s);summary=text(m.group(1)).partition(' - ')[2] if m else ''
                entry(name,invocation,kind,g,source_id,rel,summary,re.findall(r'--[a-zA-Z][a-zA-Z0-9-]+',s))
            elif family=='curl':
                m=re.search(r'^Long: (.+)$',s,re.M);summary=re.search(r'^Help: (.+)$',s,re.M)
                flags=['--'+m.group(1)] if m else []
                entry('curl','curl','binary',g,source_id,rel,'Transfer data using URL protocols',flags)
            elif family=='openssh':
                name=p.stem;m=re.search(r'\.Nd (.+)',s)
                entry(name,name,'binary',g,source_id,rel,text(m.group(1)) if m else '')
            elif family=='openssl':
                name=p.name.replace('.pod.in','').replace('.pod','');m=re.search(r'=head1 NAME\s*\n+(.*?)(?:\n\n|\n=head)',s,re.S)
                if name=='openssl':invocation='openssl';kind='binary'
                elif name.startswith('openssl-'):
                    sub=name[len('openssl-'):]
                    is_command=re.search(r'B<openssl>\s+B<'+re.escape(sub)+r'>',s) is not None
                    invocation='openssl '+sub if is_command else name+'(1)';kind='subcommand' if is_command else 'manual_reference'
                else:continue
                entry(name,invocation,kind,g,source_id,rel,text(m.group(1))[:350] if m else '')
    local_bash=ROOT/'catalog/research/bash-builtins.json'
    if local_bash.exists():
        bash=json.loads(local_bash.read_text());source_id='bash-local-help'
        sources.append({'id':source_id,'name':'Local Bash builtin help','version':bash['version'],'acquisition_kind':'local_binary_help_not_online_retrieval','reference_url':bash['reference_url'],'reference_url_retrieved':False,'sha256':digest(local_bash.read_bytes())})
        g=group('bash','shell','GNU Bash shell and its builtin commands');g['version']=bash['version'];g['distributions']=['linux'];g['source_ids'].append(source_id)
        entry('bash','bash','binary',g,source_id,'catalog/research/bash-builtins.json','GNU Bash command interpreter')
        for b in bash['builtins']:
            entry(b['name'],b['name'],'shell_builtin',g,source_id,'catalog/research/bash-builtins.json',text(b['description'])[:600])
    for g in groups.values():
        g['source_ids']=sorted(set(g['source_ids']));g['executable_ids']=sorted(set(g['executable_ids']));g['distributions']=sorted(set(g['distributions']));g['review_status']='reference_inventory_not_execution_validated'
    for g in groups.values():g['entry_ids']=g.pop('executable_ids')
    inventory={'schema_version':1,'scope':'Pinned Kali tool/package groups and eight upstream Linux CLI documentation families; not all software ever available for Linux.','tools':sorted(groups.values(),key=lambda x:x['id']),'cli_entries':sorted(entries.values(),key=lambda x:x['invocation']),'sources':sources,'documents':documents,'exclusions':excluded,'counts':{'tool_or_package_groups':len(groups),'kali_package_sections':package_count,'documented_entries_total':len(entries),'documented_cli_entries':sum(x['kind'] in ('binary','subcommand','shell_builtin') for x in entries.values()),'distinct_binary_names':sum(x['kind']=='binary' for x in entries.values()),'builtin_only_entries':sum(x['kind']=='shell_builtin' for x in entries.values()),'shell_builtin_names':sum('shell_builtin' in x.get('invocation_kinds',[]) for x in entries.values()),'subcommand_entries':sum(x['kind']=='subcommand' for x in entries.values()),'service_unit_references':sum(x['kind']=='service_unit' for x in entries.values()),'manual_reference_entries':sum(x['kind']=='manual_reference' for x in entries.values()),'source_documents':len(documents)}}
    dest=ROOT/'catalog/research';dest.mkdir(parents=True,exist_ok=True);(dest/'upstream-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n')
    for name in ['upstream-retrievals.json','retrievals.json','kali-source.json']:(dest/name).write_bytes((cache/name).read_bytes())
    print(json.dumps(inventory['counts'],indent=2))
if __name__=='__main__':main()
