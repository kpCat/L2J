"""Read-only reuse of028 exact native FINALIZED SQL comparison; no late SQL substitution."""
import argparse, hashlib, importlib.util, json
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--root',type=Path,action='append',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
module=Path(__file__).resolve().parents[5]
original=module/'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/boundary_sql028.py'
spec=importlib.util.spec_from_file_location('boundary028',original)
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
rows=[]
for root in a.root:
 for path in sorted(root.glob('*-finalized.properties')):
  fields=b.properties(path)
  sql=path.with_name(path.name+'.sql.tsv')
  native=b.tables(sql)
  db=next(values[0]['database_name'] for query,values in native.items() if query.startswith('SELECT DATABASE'))
  if db not in {f'l2jmobiush5_localplay_contract032{x}' for x in 'abcdt'}:raise ValueError('Own DB required')
  if fields.get('source')!='native-finalized-snapshot' or fields.get('checkpointStage')!='FINALIZED' or fields.get('exactArgument')!='true' or fields.get('nativeOwnerSealed')!='true':raise ValueError('Exact actual FINALIZED witness required')
  components=next(values for query,values in native.items() if 'FROM phantom_profile_components' in query)
  states=[r for r in components if r['component_type']=='background.state']
  state=states[0] if len(states)==1 else None
  exact=bool(state and int(state['row_version'])==int(fields['preparedRowVersion'])+1 and hashlib.sha256(bytes.fromhex(state['payload'])).hexdigest()==fields['afterPayloadSha256'])
  rows.append(dict(profileId=int(fields['profileId']),epoch=int(fields['epoch']),preparedRowVersion=int(fields['preparedRowVersion']),exactBoundaryState=exact,nativeFieldsExact=b.native_fields_exact(fields,native),source=str(path.resolve().relative_to(module)),sqlSha256=hashlib.sha256(sql.read_bytes()).hexdigest()))
failures=[r for r in rows if not(r['exactBoundaryState'] and r['nativeFieldsExact'])]
result=dict(kind='ACTUAL_FINALIZED_SQL_BOUNDARY_032',originalValidatorSha256=hashlib.sha256(original.read_bytes()).hexdigest(),receipts=len(rows),profiles=len({r['profileId'] for r in rows}),allBoundaryStateExact=all(r['exactBoundaryState'] for r in rows),allNativeFieldsExact=all(r['nativeFieldsExact'] for r in rows),failures=failures,allReceipts=rows,lateSqlSubstituted=False,completeLaterLineageClaimed=False)
if not rows:raise ValueError('No actual receipts')
with a.output.open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2);stream.write('\n')
print(json.dumps({k:result[k] for k in ('kind','receipts','profiles','allBoundaryStateExact','allNativeFieldsExact')}))
print('failures='+str(len(failures)))
