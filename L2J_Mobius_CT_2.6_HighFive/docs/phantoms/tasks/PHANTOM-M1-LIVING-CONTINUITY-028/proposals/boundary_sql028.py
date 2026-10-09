"""Assemble actual per-receipt FINALIZED SQL views; never substitute a later SQL row."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)


def tables(path):
    result, query, header = {}, None, None
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('QUERY\t'):
            query, header = line[6:], None
            result[query] = []
        elif query is not None:
            if header is None:
                header = line.split('\t')
            elif line:
                values = line.split('\t')
                if len(values) != len(header):
                    raise ValueError('Incomplete SQL row')
                result[query].append(dict(zip(header, values)))
    return result


def write_tsv(path, rows, names):
    with path.open('x', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=names, delimiter='\t', lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)


def native_fields_exact(p, native):
    actual = next(rows for query, rows in native.items() if 'FROM characters c' in query)
    if len(actual) != 1 or int(actual[0]['charId']) != int(p['objectId']):
        return False
    row = actual[0]
    aliases = {'hp':'curHp','mp':'curMp','cp':'curCp','classId':'classid','vitality':'vitality_points'}
    integers = ('level','exp','sp','expBeforeDeath','x','y','z','heading','classIndex','classId','race','vitality')
    floats = ('hp','maxHp','mp','maxMp','cp','maxCp')
    scalar = all(int(p[key]) == int(row[aliases.get(key,key)]) for key in integers)
    scalar &= all(abs(float(p[key])-float(row[aliases.get(key,key)])) <= 1e-6 for key in floats)
    ih = hashlib.sha256()
    items = next(rows for query,rows in native.items() if 'FROM items ' in query)
    for item in sorted((item for item in items if item['loc'] in ('INVENTORY','PAPERDOLL')),key=lambda item:int(item['object_id'])):
        for key in ('object_id','item_id','count','loc'):
            ih.update(item[key].encode('ascii')); ih.update(b'\0')
    skills = next(rows for query,rows in native.items() if 'FROM character_skills ' in query)
    skills = sorted((skill for skill in skills if int(skill['class_index'])==int(p['classIndex'])),key=lambda skill:int(skill['skill_id']))
    sh = hashlib.sha256(''.join(f"{skill['skill_id']}:{skill['skill_level']}\n" for skill in skills).encode('ascii')).hexdigest()
    return scalar and ih.hexdigest()==p['inventoryHash'] and sh==p['skillsHash']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cohort', type=Path, required=True)
    parser.add_argument('--sealed', type=Path, action='append', required=True)
    parser.add_argument('--post-stop-sql', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    cohort = json.loads(args.cohort.read_text(encoding='utf-8-sig'))
    ids = {int(row['profileId']) for row in cohort}
    if not 4 <= len(ids) <= 8 or len(ids) != len(cohort):
        raise ValueError('Whole enrolled cohort4..8 required')
    latest, inventory = {}, []
    for root in args.sealed:
        for path in root.glob('*-finalized.properties'):
            p = properties(path)
            pid = int(p['profileId'])
            if pid not in ids:
                continue
            if p.get('source') != 'native-finalized-snapshot' or p.get('checkpointStage') != 'FINALIZED' or p.get('exactArgument') != 'true' or p.get('nativeOwnerSealed') != 'true':
                raise ValueError('Actual exact native FINALIZED witness required')
            sql_path = path.with_name(path.name + '.sql.tsv')
            native = tables(sql_path)
            components = next(rows for query, rows in native.items() if 'FROM phantom_profile_components' in query)
            states = [row for row in components if row['component_type'] == 'background.state']
            observed = states[0] if len(states) == 1 else None
            exact = bool(observed and int(observed['row_version']) == int(p['preparedRowVersion']) + 1 and hashlib.sha256(bytes.fromhex(observed['payload'])).hexdigest() == p['afterPayloadSha256'])
            inventory.append({'profileId': pid, 'epoch': int(p['epoch']), 'preparedRowVersion': int(p['preparedRowVersion']), 'exactBoundaryState': exact, 'nativeFieldsExact':native_fields_exact(p,native), 'source': str(path), 'sql': str(sql_path), 'sqlSha256': hashlib.sha256(sql_path.read_bytes()).hexdigest()})
            if pid not in latest or int(p['preparedRowVersion']) > int(latest[pid][0]['preparedRowVersion']):
                latest[pid] = (p, native, path)
    if set(latest) != ids:
        raise ValueError(f'Missing finalized SQL views: {sorted(ids-set(latest))}')
    args.output.mkdir(parents=True, exist_ok=False)
    chars, items, skills, durable = [], [], [], []
    mapping = {'charId': 'objectId', 'curHp': 'hp', 'curMp': 'mp', 'curCp': 'cp', 'classid': 'classId', 'vitality_points': 'vitality'}
    for pid in sorted(ids):
        p, native, path = latest[pid]
        db = next(rows[0]['database_name'] for query, rows in native.items() if query.startswith('SELECT DATABASE'))
        if db not in {f'l2jmobiush5_localplay_contract028{x}' for x in 'bcdefgh'}:
            raise ValueError('Exact own DB required')
        actual = next(rows for query, rows in native.items() if 'FROM characters c' in query)
        if len(actual) != 1 or int(actual[0]['charId']) != int(p['objectId']):
            raise ValueError('Native object SQL identity mismatch')
        chars.append({'profileId': pid, **{mapping.get(key, key): value for key, value in actual[0].items()}})
        for query, rows in native.items():
            if 'FROM items ' in query:
                items.extend({'profileId': pid, 'owner_id': p['objectId'], **row} for row in rows)
            elif 'FROM character_skills ' in query:
                skills.extend({'profileId': pid, 'charId': p['objectId'], **row} for row in rows)
            elif 'FROM phantom_profile_components' in query:
                durable.extend('\t'.join(row[key] for key in ('profile_id', 'row_version', 'component_type', 'payload')) for row in rows)
    # Actual stopped counts remain separate from these transaction boundary views.
    write_tsv(args.output/'characters.tsv', chars, list(chars[0]))
    write_tsv(args.output/'items.tsv', items, ['profileId','owner_id','object_id','item_id','count','loc','loc_data','enchant_level'])
    write_tsv(args.output/'skills.tsv', skills, ['profileId','charId','skill_id','skill_level','class_index'])
    (args.output/'durable-hex.tsv').write_text('\n'.join(durable)+'\n', encoding='utf-8')
    for name in ('counts.tsv', 'runtime-manifest.json'):
        shutil.copyfile(args.post_stop_sql/name, args.output/name)
    module = Path(__file__).resolve().parents[5]
    helper = module/'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDurable024.java'
    java = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe'
    result = subprocess.run([java, '-cp', str(module/'dist/libs/*'), str(helper), str(args.output/'durable-hex.tsv')], capture_output=True, text=True, check=True)
    (args.output/'durable-decoded.tsv').write_text(result.stdout, encoding='utf-8')
    (args.output/'boundary-inventory.json').write_text(json.dumps({'kind':'ACTUAL_PER_RECEIPT_FINALIZED_SQL', 'requiredProfiles': sorted(ids), 'allFinalizedReceipts': inventory, 'singleWholeDatabaseSnapshotClaimed': False, 'lateSqlSubstituted': False}, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'profiles':len(ids), 'receipts':len(inventory), 'allBoundaryStateExact':all(row['exactBoundaryState'] for row in inventory), 'allNativeFieldsExact':all(row['nativeFieldsExact'] for row in inventory)}))


if __name__ == '__main__':
    main()
