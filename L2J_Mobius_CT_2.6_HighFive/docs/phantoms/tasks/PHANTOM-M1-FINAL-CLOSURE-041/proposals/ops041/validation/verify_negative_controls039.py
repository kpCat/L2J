"""Three explicit negative copies of actual raw039; never campaign producer records."""
import argparse
import base64
import hashlib
import json
import subprocess
from pathlib import Path
from verify_commit_run039 import props

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('proof',type=Path); parser.add_argument('--jar',type=Path,required=True); parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); output=args.output.resolve()
    output.mkdir(parents=False,exist_ok=False)
    proof=json.loads(args.proof.read_text(encoding='utf-8-sig'))
    actual=next(r for r in proof['rows'] if r['nativeCommit']=='PASS')
    original=Path(actual['commit']); raw=original.read_bytes(); p=props(original)
    changed=bytearray(base64.b64decode(p['afterPayloadBase64'])); changed[-1]^=1
    changes={
        'wrong-epoch':('nativeEpoch',str(int(p['nativeEpoch'])+1)),
        'tampered-payload':('afterPayloadBase64',base64.b64encode(changed).decode()),
        'wrong-locked-exp':('canonical.exp',str(int(p['canonical.exp'])+1)),
    }
    cases=[('actual',original,'PASS')]
    for name,(key,value) in changes.items():
        path=output/(name+'.negative-control.properties')
        path.write_bytes(raw+b'\nnegativeControl039=true\n'+(key+'='+value+'\n').encode())
        cases.append((name,path,'FAIL'))
    inputs=output/'inputs.tsv'
    inputs.write_text(''.join(str(path)+'\t'+actual['context']+'\t'+actual['seal']+'\n' for _,path,_ in cases),encoding='utf-8')
    command=[r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe','-cp',str(args.jar),str(Path(__file__).with_name('VerifyNativeCommit039.java')),str(inputs)]
    result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8')
    (output/'decoder.log').write_text(result.stdout+result.stderr,encoding='utf-8')
    returned={line.split('\t',2)[0]:line.split('\t',2)[1:] for line in result.stdout.splitlines() if '\t' in line}
    rows=[{'case':name,'expected':expected,'actual':returned.get(str(path),['MISSING'])[0],'detail':returned.get(str(path),[]),'copySha256':hashlib.sha256(path.read_bytes()).hexdigest()} for name,path,expected in cases]
    passed=all(r['expected']==r['actual'] for r in rows) and original.read_bytes()==raw
    receipt={'scope':'VERIFIER_NEGATIVE_CONTROLS_ONLY','originalRaw':str(original),'originalSha256':hashlib.sha256(raw).hexdigest(),'originalUnchanged':original.read_bytes()==raw,'pass':passed,'cases':rows,'command':command,'decoderExit':result.returncode}
    (output/'result.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'pass':passed,'cases':rows},ensure_ascii=False))
    return 0 if passed else 2

if __name__=='__main__': raise SystemExit(main())
