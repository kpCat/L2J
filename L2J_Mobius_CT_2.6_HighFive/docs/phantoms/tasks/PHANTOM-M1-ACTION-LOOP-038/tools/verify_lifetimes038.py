"""Read-only whole-incarnation save audit using existing028 native-field comparison."""
import argparse
import base64
import hashlib
import importlib.util
import json
import struct
from pathlib import Path


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--episode', required=True, choices=('b', 'c'))
    parser.add_argument('--startup', required=True, type=Path)
    parser.add_argument('--source-sha', required=True)
    args = parser.parse_args()
    task = Path(__file__).resolve().parents[1]
    evidence = (task / 'evidence').resolve()
    startup = args.startup.resolve()
    assert startup.parent == evidence and startup.name.startswith(f'STARTUP032{args.episode}-')
    scene = evidence / f'FINAL_SCENE038{args.episode}'
    full = scene / 'full-native'
    output = evidence / f'FINAL_ALL_LIFETIMES038{args.episode}.json'
    assert not output.exists(), 'Immutable audit exists'
    module = task.parents[3]
    existing = module / 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/boundary_sql028.py'
    spec = importlib.util.spec_from_file_location('boundary028', existing)
    boundary = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(boundary)
    ledger = [json.loads(line) for line in (startup / 'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    assert [row['seq'] for row in ledger] == list(range(1, len(ledger) + 1))
    assert len({row['run_id'] for row in ledger}) == 1
    assert ledger[0]['code_sha'] == args.source_sha
    end = ledger[-1]
    assert end['kind'] == 'RUN_END' and end['exporter_drained'] and end['dropped'] == 0 and not end['failure']
    roots = (startup, full)
    raw = []
    for row in ledger:
        if 'raw_file' not in row:
            continue
        matches = [root / row['raw_file'] for root in roots if (root / row['raw_file']).exists()]
        assert len(matches) == 1
        path = matches[0]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == row['raw_sha256']
        raw.append((path, properties(path)))
    births = {(row['profile_id'], row['object_id'], row['epoch']) for row in ledger if row['kind'] == 'REGISTER'}
    terminals = {(row['profile_id'], row['object_id'], row['epoch']) for row in ledger if row['kind'] == 'TERMINAL'}
    assert births == terminals and len(births) == end['native_births']
    results = []
    for profile, object_id, epoch in sorted(births):
        result = {'profile': profile, 'object': object_id, 'epoch': epoch, 'pass': False}
        try:
            terminal_path, terminal = next((path, p) for path, p in raw if p.get('kind') == 'TERMINAL' and int(p['profileId']) == profile and int(p['epoch']) == epoch)
            result['terminal'] = terminal_path.name
            assert terminal['nativeOwnerState'] == 'DETACHED' and terminal['terminalOutstanding'] == terminal['terminalPendingTimers'] == '0', 'Native owner not drained'
            assert all(terminal[k] == 'true' for k in ('worldAbsent', 'autoSaveAbsent', 'leaseAbsent')), 'Native release triad'
            final_path, final = next((path, p) for path, p in raw if p.get('checkpointStage') == 'FINALIZED' and int(p['profileId']) == profile and int(p['epoch']) == epoch and p['receiptSha256'] == terminal['receiptSha256'])
            result['finalized'] = final_path.name
            prepared = properties(final_path.with_name(final_path.name.replace('-finalized.properties', '.properties')))
            assert int(final['objectId']) == object_id and final['exactArgument'] == final['nativeOwnerSealed'] == 'true', 'Exact sealed native argument'
            assert int(final['hookNanos']) < int(terminal['terminalNanos']), 'Finalized before terminal'
            assert final['fullStateBase64'] == prepared['fullStateBase64'] and final['receiptSha256'] == prepared['receiptSha256'], 'Prepared/finalized argument mismatch'
            payload = base64.b64decode(final['fullStateBase64'])
            digest = hashlib.sha256(payload).hexdigest()
            assert digest == final['afterPayloadSha256'], 'Sealed payload hash'
            sql_path = final_path.with_name(final_path.name + '.sql.tsv')
            sections = boundary.tables(sql_path)
            db = next(rows[0]['database_name'] for query, rows in sections.items() if query.startswith('SELECT DATABASE'))
            assert db == f'l2jmobiush5_localplay_contract038{args.episode}', 'Exact owned DB'
            assert boundary.native_fields_exact(final, sections), 'Native characters/skills/full inventory SQL parity'
            components = next(rows for query, rows in sections.items() if 'FROM phantom_profile_components' in query)
            state = next(row for row in components if row['component_type'] == 'background.state')
            assert int(state['row_version']) == int(final['preparedRowVersion']) + 1 and bytes.fromhex(state['payload']) == payload, 'Late SQL boundary mismatch; producer/lineage UNKNOWN'
            context = next(row for row in components if row['component_type'] == 'background.native-context')
            data = bytes.fromhex(context['payload'])
            length = struct.unpack_from('>H', data, 51)[0]
            assert data[32] == 1 and struct.unpack_from('>q', data, 43)[0] == int(state['row_version']) and data[53:53 + length].decode('ascii') == digest, 'Completed native context binding'
            assert not [row for row in components if row['component_type'] == 'background.owned-store'], 'Owned store still retained'
            result.update(pass_=True, receipt=final['receiptSha256'], rowVersion=int(state['row_version']), inventoryHash=final['inventoryHash'], sqlSha256=hashlib.sha256(sql_path.read_bytes()).hexdigest())
            result['pass'] = result.pop('pass_')
        except (AssertionError, StopIteration, KeyError, ValueError) as failure:
            result['reason'] = str(failure) or 'Required sealed receipt/SQL field missing; NOT_VERIFIED'
        results.append(result)
    value = {'contract': 'TASK038_ALL_NATIVE_LIFETIMES', 'sourceSha': args.source_sha, 'startup': startup.name, 'births': len(births), 'allBirthsTerminal': True, 'pass': all(row['pass'] for row in results), 'rows': results}
    output.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value[key] for key in ('contract', 'births', 'allBirthsTerminal', 'pass')}))
    return 0 if value['pass'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
