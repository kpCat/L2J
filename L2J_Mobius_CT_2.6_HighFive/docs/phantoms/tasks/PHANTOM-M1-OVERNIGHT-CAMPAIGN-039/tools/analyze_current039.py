"""Read-only current timelines, reusing historical039 signatures; no verdict replacement."""
import argparse
import hashlib
import json
from pathlib import Path

KEYS = ['materializedAtNanos','missing','dead','worldPresent','nativePhase','runtimeReason',
        'continuationSession','liveAutoPlay','liveAutoPlayTickReason','liveAutoUseTickReason',
        'currentActionGuard','actionReason','liveResourceRecovery','liveResourceReason',
        'nativeFarmCycleSequence','nativeDamageSequence','nativeRewardSequence',
        'nativeKillSequence','nativeFirstUnprovenReason','targetObjectId','aiAttackTarget',
        'hp','mp','exp','sp','x','y','z','nativeIsMage','liveFirstStopReason']

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('scene',type=Path); parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); root=args.scene.resolve()
    source=root/'all-samples.json'
    baseline=json.loads((root/'baseline-cohort.json').read_text(encoding='utf-8-sig'))
    samples=json.loads(source.read_text(encoding='utf-8-sig'))
    rows=[]
    for enrolled in baseline:
        profile=enrolled['profileId']; epoch=enrolled['materializedAtNanos']; timeline=[]; previous=None
        exact=[]
        for sample in samples:
            actor=next((r for r in sample['actors'] if r['profileId']==profile),None)
            if actor is None: continue
            signature=tuple(actor.get(k) for k in KEYS[:14])
            elapsed=float(sample['elapsedSeconds'])
            if signature!=previous or not timeline or elapsed-timeline[-1]['elapsed']>=30:
                timeline.append({'elapsed':round(elapsed,3),**{k:actor.get(k,'') for k in KEYS}}); previous=signature
            if actor.get('materializedAtNanos')==epoch and actor.get('missing')=='false': exact.append(actor)
        gains={}
        for name in ('nativeFarmCycleSequence','nativeDamageSequence','nativeRewardSequence','nativeKillSequence','exp','sp'):
            gains[name]=int(exact[-1].get(name,0))-int(exact[0].get(name,0)) if exact else None
        row={'profileId':profile,'enrolledEpoch':epoch,'wholeWindowPresent':len(exact)==len(samples),'lastExactGains':gains,'timeline':timeline}
        rows.append(row)
        print(profile,'exactSamples='+str(len(exact))+'/'+str(len(samples)),gains)
        for r in timeline:
            print(' ',r['elapsed'],r['materializedAtNanos'],r['missing'],r['nativePhase'],r['runtimeReason'],r['actionReason'],r['nativeFarmCycleSequence'],r['liveFirstStopReason'])
    value={'contract':'CURRENT039_READ_ONLY_CAUSAL_TIMELINE','source':str(source),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'actors':rows,'ruling':'No filtered cohort, substituted epoch, counter repair or verdict change'}
    with args.output.open('x',encoding='utf-8') as stream: json.dump(value,stream,ensure_ascii=False,indent=2); stream.write('\n')

if __name__=='__main__': main()
