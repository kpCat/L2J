"""Audit all original memberships and observed lifetimes; never reconstruct a missed receipt."""
import hashlib
import json
from pathlib import Path
from continuity028 import segment_result
from boundary_sql028 import properties, tables, native_fields_exact

TASK = Path(__file__).resolve().parents[1]
EVIDENCE = TASK / 'evidence'
SCENES = ('FINAL_SCENE_07C2_F1', 'FINAL_SCENE_07C2_F2', 'FINAL_AWAY_07C2_F3')


def main():
    memberships, lifetimes, receipts = [], set(), {}
    for name in SCENES:
        root = EVIDENCE / name
        baseline = json.loads((root / 'baseline-cohort.json').read_text(encoding='utf-8-sig'))
        memberships.append({'scene': name, 'cohortSha256': hashlib.sha256((root / 'baseline-cohort.json').read_bytes()).hexdigest(),
                            'actors': [{'profileId': int(a['profileId']), 'epoch': int(a['materializedAtNanos'])} for a in baseline]})
        for actor in baseline:
            lifetimes.add((int(actor['profileId']), int(actor['materializedAtNanos'])))
        frames = json.loads((root / 'all-samples.json').read_text(encoding='utf-8-sig'))
        for frame in frames:
            for actor in frame['actors']:
                if actor.get('worldPresent') == 'true':
                    lifetimes.add((int(actor['profileId']), int(actor['materializedAtNanos'])))
        for path in (root / 'full-native').glob('*-finalized.properties'):
            witness = properties(path)
            key = (int(witness['profileId']), int(witness['epoch']))
            if key not in receipts or int(witness['preparedRowVersion']) > int(receipts[key][1]['preparedRowVersion']):
                receipts[key] = (path, witness)
    lifetime_rows = []
    for pid, epoch in sorted(lifetimes):
        receipt = receipts.get((pid, epoch))
        row = {'profileId': pid, 'epoch': epoch, 'receiptCaptured': receipt is not None,
               'terminalPermanentSealProved': 'UNKNOWN', 'completeTransitionLineageProved': False}
        if receipt is not None:
            path, p = receipt
            sql_path = path.with_name(path.name + '.sql.tsv')
            native = tables(sql_path)
            states = [r for query, rows in native.items() if 'FROM phantom_profile_components' in query
                      for r in rows if r['component_type'] == 'background.state']
            row.update({'witnessPath': str(path.relative_to(TASK)), 'preparedVersion': int(p['preparedRowVersion']),
                        'witnessSha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'sqlSha256': hashlib.sha256(sql_path.read_bytes()).hexdigest(),
                        'nativeFieldsExact': native_fields_exact(p, native),
                        'statePayloadExactAtExport': len(states) == 1
                        and int(states[0]['row_version']) == int(p['preparedRowVersion']) + 1
                        and hashlib.sha256(bytes.fromhex(states[0]['payload'])).hexdigest() == p['afterPayloadSha256']})
        lifetime_rows.append(row)
    away = EVIDENCE / SCENES[-1]
    baseline = json.loads((away / 'baseline-cohort.json').read_text(encoding='utf-8-sig'))
    frames = json.loads((away / 'all-samples.json').read_text(encoding='utf-8-sig'))
    farm = []
    for actor in baseline:
        pid = str(actor['profileId'])
        by_epoch, faults = {}, set()
        for frame in frames:
            rows = [r for r in frame['actors'] if str(r['profileId']) == pid]
            if len(rows) != 1 or rows[0].get('worldPresent') != 'true' or rows[0].get('dead') != 'false':
                faults.add('COMPLETE_LINEAGE_REQUIRED'); continue
            row = rows[0]
            if row.get('current.nativeFirstUnprovenReason') != 'NONE' or row.get('current.nativeEvidenceOverflow') != 'false':
                faults.add('NATIVE_UNPROVEN')
            epoch = int(row['current.nativeEvidenceEpoch'])
            if epoch == int(actor['materializedAtNanos']):
                faults.add('RETURN_NEW_EPOCH_REQUIRED')
            by_epoch.setdefault(epoch, []).append((float(frame['elapsedSeconds']), row))
        segments = [segment_result(points, float(frames[-1]['elapsedSeconds'])) for points in by_epoch.values()]
        if not any(s['cycles'] >= 2 and s['delta']['ExpGained'] > 0 and s['delta']['SpGained'] > 0 for s in segments):
            faults.add('POST_RETURN_2_CYCLES_EXP_SP_UNPROVEN')
        farm.append({'profileId': int(pid), 'segments': segments, 'faults': sorted(faults), 'pass': not faults})
    doc = {'kind': 'WHOLE_ORIGINAL_MEMBERSHIP_AUDIT', 'memberships': memberships,
           'uniqueProfiles': sorted({pid for pid, _ in lifetimes}), 'observedLifetimes': lifetime_rows,
           'missingReceiptLifetimes': [r for r in lifetime_rows if not r['receiptCaptured']],
           'postReturnWholeCohort': farm, 'WHOLE_GROUP_SAVE_PASS': False,
           'terminalReceiptProofLimitation': 'Native snapshot records checkpoint SEALED, not permanent terminal seal. Missing receipts stay missing.',
           'POST_RETURN_WHOLE_COHORT_PASS': all(r['pass'] for r in farm)}
    with (EVIDENCE / 'WHOLE_GROUP_028_AUDIT.json').open('x', encoding='utf-8') as stream:
        json.dump(doc, stream, indent=2); stream.write('\n')
    print(json.dumps({'uniqueProfiles': len(doc['uniqueProfiles']), 'observedLifetimes': len(lifetime_rows),
                      'missing': [(r['profileId'], r['epoch']) for r in doc['missingReceiptLifetimes']],
                      'postReturnPassing': sum(r['pass'] for r in farm), 'postReturnDenominator': len(farm)}))


if __name__ == '__main__':
    main()
