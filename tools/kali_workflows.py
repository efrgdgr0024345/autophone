"""Build source-linked local workflows. No inventoried program is executed."""
import re, shlex

NAME = re.compile(r'[A-Za-z0-9_][A-Za-z0-9+_.:-]{0,119}\Z')
PACKAGE = re.compile(r'[a-z0-9][a-z0-9+.-]+\Z')
HELP_FLAGS = {'--help', '-h', '--version', '-V'}

def literal(value):
    assert value and all(32 <= ord(c) <= 126 for c in value)
    return value if NAME.fullmatch(value) else "'" + value.replace("'", "'\\''") + "'"

def build_support(data):
    entries = data['command_inventory']
    cli_ids = {e['id'] for e in entries}
    command_workflows = []
    for entry in entries:
        tokens = shlex.split(entry['invocation'])
        assert 1 <= len(tokens) <= 2
        # All names are literals, never shell expressions from documentation.
        assert all(NAME.fullmatch(t) or t in {'.', ':', '['} for t in tokens)
        topic = entry['name']
        observed = None
        for candidate in entry['observed_help_invocations']:
            try: words = shlex.split(candidate)
            except ValueError: continue
            if words[:-1] == tokens and words[-1] in HELP_FLAGS and candidate == ' '.join(words):
                observed = candidate
                break
        command_workflows.append({
            'entry_id': entry['id'], 'executable': tokens[0], 'invocation_tokens': tokens,
            'manual_topic': topic, 'observed_help_command': observed,
            'template_id': 'command-workflow',
            'discovery_commands': [f'command -v {literal(tokens[0])}',
                f'man -f -- {literal(topic)}',
                f'man -P cat -- {literal(topic)} | head -n 40'] + ([observed] if observed else []),
            'task_builder': {'argument_mode': 'literal_arguments', 'default_risk': 'high',
                'maximum_command_length': 512, 'requires_user_preparation': True},
            'evidence': 'user_reviews_actual_terminal_output', 'automatic_execution': False})
    tool_workflows = []
    for tool in data['tools']:
        packages = [p['name'] for p in tool.get('packages', [])]
        assert all(PACKAGE.fullmatch(p) for p in packages)
        commands = [x for x in tool['entry_ids'] if x in cli_ids]
        assert packages or commands
        tool_workflows.append({'tool_id': tool['id'], 'template_id': 'package-workflow' if packages else 'command-workflow',
            'package_names': packages, 'command_entry_ids': commands,
            'mode': 'commands_and_packages' if commands and packages else 'commands' if commands else 'package_only',
            'automatic_execution': False})
    kali_tools = {t['id'] for t in data['tools'] if 'kali' in t['distributions']}
    kali_commands = {e['id'] for e in entries if kali_tools.intersection(e['tool_ids'])}
    coverage = {'kali_tools': len(kali_tools), 'kali_commands': len(kali_commands),
        'supported_kali_tools': sum(w['tool_id'] in kali_tools for w in tool_workflows),
        'supported_kali_commands': sum(w['entry_id'] in kali_commands for w in command_workflows),
        'package_only_kali_groups': sum(w['tool_id'] in kali_tools and w['mode']=='package_only' for w in tool_workflows),
        'all_supported_tools': len(tool_workflows), 'all_supported_commands': len(command_workflows)}
    data['workflow_support'] = {'version': 1, 'coverage': coverage,
        'command_workflows': command_workflows, 'tool_workflows': tool_workflows,
        'templates': [
            {'id': 'command-workflow', 'stages': ['availability', 'manual_summary', 'bounded_manual', 'observed_help_if_available', 'prepare_literal_arguments', 'review_exact_command_and_destination', 'review_actual_output'],
             'on_missing_or_unexpected_result': 'Stop, inspect the exact result, and revise the plan; do not infer success or retry blindly.',
             'completion': 'Navigation and command construction do not execute or confirm results.'},
            {'id': 'package-workflow', 'stages': ['choose_documented_package', 'installed_version', 'package_status', 'bounded_file_list', 'choose_documented_command_or_package_documentation'],
             'commands': ['dpkg-query -W -- {package}', 'dpkg-query -s -- {package} | head -n 40', 'dpkg-query -L -- {package} | head -n 40'],
             'on_missing_or_unexpected_result': 'Report the package as unavailable; inspect the installation source before proposing a separate install step.',
             'completion': 'A library, firmware bundle or metapackage is not invented as a runnable executable.'}],
        'scope': 'Source-linked discovery, literal argument preparation and reviewed plan/TYPE ONLY use. Does not expand unattended Auto policy or imply installed/executed tool validation.'}
    data['counts'].update({f'workflow_{k}': v for k,v in coverage.items()})
