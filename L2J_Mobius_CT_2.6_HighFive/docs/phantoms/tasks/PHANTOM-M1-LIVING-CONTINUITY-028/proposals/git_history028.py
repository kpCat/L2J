"""Extract exact Git shell invocations from this chat's local rollout, excluding other content."""
import argparse
import json
from pathlib import Path
import re

TASK = Path(__file__).resolve().parents[1]
CHAT_ID = '01a11daa-6d98-7c00-9c26-defeef083075'
COMMAND = re.compile(r'\bcmd\s*:\s*("(?:\\.|[^"\\])*")')
GIT = re.compile(r'(?<![A-Za-z])git\s+(?:rev-parse|status|fetch|ls-remote|worktree|diff|show|ls-files|add|commit|push)\b')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('rollout', type=Path)
    args = parser.parse_args()
    if CHAT_ID not in args.rollout.name or args.rollout.suffix != '.jsonl':
        raise ValueError('Exact current chat rollout required')
    invocations = []
    with args.rollout.open(encoding='utf-8') as stream:
        for line in stream:
            record = json.loads(line)
            payload = record.get('payload', {})
            if record.get('type') != 'response_item' or payload.get('type') not in ('function_call', 'custom_tool_call'):
                continue
            arguments = payload.get('arguments', payload.get('input', ''))
            if payload.get('name') in ('functions.exec', 'exec'):
                try:
                    wrapped = json.loads(arguments)
                    if isinstance(wrapped, dict):
                        arguments = wrapped.get('code', arguments)
                except json.JSONDecodeError:
                    pass
                commands = [json.loads(m.group(1)) for m in COMMAND.finditer(arguments)]
            elif payload.get('name') == 'exec_command':
                commands = [json.loads(arguments).get('cmd', '')]
            else:
                continue
            for command in commands:
                if GIT.search(command):
                    invocations.append({'utc': record['timestamp'], 'shellCommandExact': command})
    output = TASK / 'evidence/GIT_COMMANDS_EXACT.json'
    output.write_text(json.dumps({'kind': 'EXACT_CURRENT_CHAT_GIT_INVOCATIONS', 'chatId': CHAT_ID,
                                 'invocations': invocations, 'scope': 'Git shell inputs only; no rollout content exported.'},
                                ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'gitShellInvocations': len(invocations), 'output': output.name}))


if __name__ == '__main__':
    main()
