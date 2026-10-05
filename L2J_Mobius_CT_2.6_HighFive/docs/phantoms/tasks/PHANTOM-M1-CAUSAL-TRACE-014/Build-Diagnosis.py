"""Produce five edge-related history suspects and factual frozen-trace statistics."""
import collections
import csv
import json
from pathlib import Path

TASK = Path(__file__).resolve().parent
matrix = list(csv.DictReader((TASK / 'REGRESSION_MATRIX.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
suspects = [
    ('8457b90', 'registerDue native-materialization exception and REPLAN_REQUIRED retry handling; exact gate now returns INCOMPLETE instead of ecology.native_materialization_required.'),
    ('dd58a51', 'ensureNativeContext adds native-context attestation prerequisite and emits native_context.required:<delivery> for simulation-ineligible context; direct source of observed reason.'),
    ('e92d7d4', 'installPopulationReadiness and async requestMaterializationDue gate delegate materialization on due.complete; exact observed lost edge.'),
    ('6f6dec7', 'Physical current demand, isCurrentLocal composition and ecology wake/pending handoff; currentDemand is a direct prerequisite of native-materialization branch.'),
    ('ca2dbc7', 'Batch preparation admission and population-plan/inventory/metadata prerequisite ownership in ecology; affects pending request availability and drain completion.'),
]
with (TASK / 'SUSPECT_COMMITS.tsv').open('w', encoding='utf-8', newline='') as out:
    writer = csv.writer(out, delimiter='\t')
    writer.writerow(['rank', 'ordinal', 'sha', 'date', 'subject', 'edge_relation', 'causality'])
    for rank, (prefix, reason) in enumerate(suspects, 1):
        row = next(r for r in matrix if r['sha'].startswith(prefix))
        writer.writerow([rank, row['ordinal'], row['sha'], row['date'], row['subject'], reason, 'UNPROVEN; exact edge/direct prerequisite, not file-touch alone'])
rows = list(csv.DictReader((TASK / 'CAUSAL_TRACE.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
summary = [r for r in rows if r['event']=='HUMAN_REFRESH_SUMMARY']
facts = dict(eventCounts=dict(collections.Counter(r['event'] for r in rows)),
    profiles={p:dict(collections.Counter(r['event'] for r in rows if r['profileId']==p)) for p in sorted({r['profileId'] for r in rows if r['profileId']!='0'},key=int)},
    humanCounts=sorted({int(r['value1']) for r in summary}), candidateCounts=sorted({int(r['value2']) for r in summary}), liveWorldPhantomCounts=sorted({int(r['value3']) for r in summary}),
    deferReasons=dict(collections.Counter(r['reason'] for r in rows if r['event']=='READY_ECOLOGY_DEFER')),
    minSeq=int(rows[0]['seq']), maxSeq=int(rows[-1]['seq']), firstEventNanos=int(rows[0]['nanoTime']), lastEventNanos=int(rows[-1]['nanoTime']),
    jfrBytes=(TASK.parents[3] / '.phantom-local/observe014/causal-observation.jfr').stat().st_size)
(TASK / 'CAUSAL_FACTS.json').write_text(json.dumps(facts, indent=2) + '\n', encoding='utf-8')
print(json.dumps(facts))
