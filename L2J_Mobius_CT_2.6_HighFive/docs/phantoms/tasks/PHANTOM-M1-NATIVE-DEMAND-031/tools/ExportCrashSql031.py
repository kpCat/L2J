"""Format actual immutable recovery SQL for the unchanged024 strict comparator."""
import argparse, hashlib, importlib.util, json, shutil, subprocess
from pathlib import Path
p=argparse.ArgumentParser()
for name in ('receipt','window','stopped','output'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args()
module=Path(__file__).resolve().parents[5]
task=Path(__file__).resolve().parents[1]
allowed=(task/'evidence').resolve()
for path in (a.receipt,a.window,a.stopped,a.output):
 if not path.resolve().is_relative_to(allowed):raise ValueError('Exact own031 evidence path required')
original=module/'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/boundary_sql028.py'
spec=importlib.util.spec_from_file_location('boundary028',original)
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
r=b.properties(a.receipt)
if r['owner']!='TASK031_CONTRACT' or r['REALcount']!='0' or r['mode']!='CRASH_NATIVE':raise ValueError('Exact actual crash72 receipt required')
snapshot=a.receipt.parent/r['snapshotFile']; fields=b.properties(snapshot)
native=b.tables(a.window)
db=next(rows[0]['database_name'] for query,rows in native.items() if query.startswith('SELECT DATABASE'))
if db!='l2jmobiush5_localplay_contract031b' or not b.native_fields_exact(fields,native):raise ValueError('Exact recovered native fields differ')
pid=int(r['profileId'])
if pid!=int(fields['profileId']):raise ValueError('Exact profile identity differs')
mapping={'charId':'objectId','curHp':'hp','curMp':'mp','curCp':'cp','classid':'classId','vitality_points':'vitality'}
chars=[{'profileId':pid,**{mapping.get(k,k):v for k,v in row.items()}} for query,rows in native.items() if 'FROM characters c' in query for row in rows]
items=[{'profileId':pid,'owner_id':fields['objectId'],**row} for query,rows in native.items() if 'FROM items ' in query for row in rows]
skills=[{'profileId':pid,'charId':fields['objectId'],**row} for query,rows in native.items() if 'FROM character_skills ' in query for row in rows]
components=next(rows for query,rows in native.items() if 'FROM phantom_profile_components' in query)
states=[row for row in components if row['component_type']=='background.state']
if len(states)!=1 or int(states[0]['row_version'])!=int(fields['preparedRowVersion'])+1 or hashlib.sha256(bytes.fromhex(states[0]['payload'])).hexdigest()!=fields['afterPayloadSha256']:raise ValueError('Exact recovery version/payload differs')
a.output.mkdir(parents=True,exist_ok=False)
b.write_tsv(a.output/'characters.tsv',chars,list(chars[0]))
b.write_tsv(a.output/'items.tsv',items,['profileId','owner_id','object_id','item_id','count','loc','loc_data','enchant_level'])
b.write_tsv(a.output/'skills.tsv',skills,['profileId','charId','skill_id','skill_level','class_index'])
(a.output/'durable-hex.tsv').write_text(''.join('\t'.join(row[k] for k in ('profile_id','row_version','component_type','payload'))+'\n' for row in components),encoding='utf-8')
for name in ('counts.tsv','runtime-manifest.json'):shutil.copyfile(a.stopped/name,a.output/name)
helper=module/'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDurable024.java'
java='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe'
decoded=subprocess.run([java,'-cp',str(module/'dist/libs/*'),str(helper),str(a.output/'durable-hex.tsv')],capture_output=True,text=True,check=True)
(a.output/'durable-decoded.tsv').write_text(decoded.stdout,encoding='utf-8')
(a.output/'format-provenance.json').write_text(json.dumps(dict(kind='IMMUTABLE_RECOVERY_SQL_FORMAT_ONLY',window=str(a.window.relative_to(module)),windowSha256=hashlib.sha256(a.window.read_bytes()).hexdigest(),snapshotSha256=hashlib.sha256(snapshot.read_bytes()).hexdigest(),adapterSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),parserSourceSha256=hashlib.sha256(original.read_bytes()).hexdigest(),nativeFieldsExact=True,stateVersionExact=True,statePayloadExact=True,countsFromSeparateStoppedExport=True,lateNativeSqlSubstituted=False),indent=2)+'\n',encoding='utf-8')
print('IMMUTABLE_RECOVERY_SQL_FORMATTED exactFields=true exactVersion=true exactPayload=true')
