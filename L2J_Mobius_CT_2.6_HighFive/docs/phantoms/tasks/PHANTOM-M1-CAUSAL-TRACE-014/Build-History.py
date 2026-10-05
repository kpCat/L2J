"""Read-only exact history inventory; outputs only inside this task directory."""
import csv
import re
import subprocess
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
GOOD = '561c84a2dc23d6dd953e755e2fafcfbddcd5d395'
BAD = 'c23915df10239bfab15ae49276e14833268b9afc'
MODULE = 'L2J_Mobius_CT_2.6_HighFive/'
PATTERNS = {
    'REAL_IDENTITY': ('GameClient.java', 'EnterWorld.java', 'PhantomIdentityLeaseRegistry.java', 'LocalPlayPilotService.java', 'LocalPlayPilotConfig.java', 'LocalPlayPilotActions.java', 'LocalPlayPilotProtocol.java', 'LocalPlayM1Observation.java'),
    'LOCALITY': ('PhantomHumanLocalityControl.java', 'PhantomNativeLocalityEnvelope.java'),
    'PRESENCE': ('PhantomPresenceRegistry.java', 'PhantomPopulationManager.java'),
    'ECOLOGY': ('PhantomPopulationEcologyService.java',),
    'SCHEDULER': ('PhantomScheduler.java', 'PhantomSchedulerPolicy.java', 'ThreadPool.java'),
    'READINESS': ('PhantomReconcileFirstActivityPort.java', 'PhantomActivityMaterializationPort.java'),
    'MATERIALIZATION': ('PhantomMaterializationService.java', 'PhantomMaterializationServiceActivityPort.java', 'PhantomMaterializationLifecyclePort.java'),
    'BACKGROUND_ATTESTATION': ('PhantomHistoricalBackground', 'PhantomBackground', 'PhantomLegacy', 'LocalPlayM1LegacyQuarantine.java', 'L2jPhantomBackgroundAuthority.java', 'PhantomNativeContext.java', 'PhantomOrdinaryDeathRecovery.java'),
    'WORLD_SPAWN': ('PhantomMaterializedPlayer.java', 'World.java', 'WorldObject.java', 'Player.java'),
    'VISIBLE_AUTOPLAY': ('PhantomVisible', 'AutoPlayTaskManager.java', 'AutoUseTaskManager.java', 'PlayerAI.java'),
    'NATIVE_WORK': ('PlayerNative', 'PhantomNativeWorkScope.java', 'MovementTaskManager.java', 'CreatureAI.java', 'PlayerAutoSaveTaskManager.java', 'EffectList.java', 'QuestTimer.java', 'TimerExecutor.java', 'TimerHolder.java', 'NativeEventWork.java'),
    'STORE_DEMAT': ('PhantomOwnedStore', 'PhantomStore', 'LocalPlayPhantomStoreJournal.java', 'PhantomCleanupIncident.java'),
    'TOPOLOGY': ('PhantomTopology',),
}
SEMANTICS = {
    '8609ca6': ('Read-only locality target/human-locality projection для Pilot envelope.', 'NO', 'NO', 'NO'),
    'ad4c239': ('Read-only READY/AVAILABLE target selection с bounded distance.', 'NO', 'NO', 'NO'),
    '686761a': ('Read-only target fallback afterProfileId в proven lane.', 'NO', 'NO', 'NO'),
    '033ee7a': ('Отдельный local scheduler pulse/promotion; native farm travel lifecycle; ordinary death recovery и topology route data.', 'POTENTIAL', 'POTENTIAL_LOCAL_PROMOTION', 'POTENTIAL_TRAVEL_LIFECYCLE'),
    'c28c5d9': ('Locality eligibility меняется с not-OFFLINE на presence.isOnline; eligible topology queries перед cap; preparation projection.', 'POTENTIAL', 'POTENTIAL_PRESENCE_FILTER', 'NO'),
    'de40c81': ('Deferred promotion resets retry due; soft reclamation/retention и visible failure/no-target continuity.', 'POTENTIAL', 'POTENTIAL_RETRY_RECLAMATION', 'POTENTIAL_VISIBLE_CONTINUITY'),
    'e92d7d4': ('Calendar presence отделена от async ecology readiness; installPopulationReadiness и materialization demand; native travel handoff.', 'POTENTIAL', 'POTENTIAL_READINESS_ECOLOGY', 'POTENTIAL_TRAVEL'),
    '6f6dec7': ('Physical demand независимо от relevance delivery; async ecology wake scheduler; DEAD native vitals capture after load.', 'POTENTIAL', 'POTENTIAL_DUE_WAKE', 'POTENTIAL_AFTER_LOAD'),
    'ca2dbc7': ('Batch preparation admission из DemandFact; <=8 capacity slots; queue/wake и population preparation ownership.', 'POTENTIAL', 'POTENTIAL_PREPARATION_ADMISSION', 'NO'),
    '9e35763': ('Typed farm input failures, historical replans и bounded background recovery.', 'POTENTIAL', 'POTENTIAL_BACKGROUND_READINESS', 'POTENTIAL_GOAL_CONTINUITY'),
    '309e5ab': ('Read-only stable materialized snapshot, canonical/live position selection и bounded observer targets.', 'NO', 'NO', 'NO'),
    '7653282': ('Observer approach envelope reuse и typed observer replans; не scheduler/materialization gate.', 'NO_OBSERVATION_ONLY', 'NO', 'NO'),
    'f29142c': ('Recover abandoned MATERIALIZED marker при отсутствии native owners; sequential completed-unplanned renewal.', 'POTENTIAL', 'POTENTIAL_DURABLE_READINESS', 'NO'),
    'f9562c8': ('Headless autosave не публикует canonical state вне owned store/capture.', 'POTENTIAL', 'NO', 'POTENTIAL_PERSISTENCE'),
    '9a9dfb5': ('Attested legacy headless volatile repair: exact identity/rowVersion/payload/native guards.', 'POTENTIAL', 'POTENTIAL_ATTESTATION', 'NO'),
    '0b8fdf1': ('Расширен pinned exact inconsistent witness set в legacy recovery.', 'POTENTIAL', 'POTENTIAL_ATTESTED_COHORT', 'NO'),
    '538cf3e': ('Дополнительный pinned MATERIALIZED cohort и single inconsistent-marker transition.', 'POTENTIAL', 'POTENTIAL_DURABLE_ATTESTATION', 'NO'),
    'af15a1c': ('Historical pending receipts reconciliation и guarded reopen/recovery stale goals/background.', 'POTENTIAL', 'POTENTIAL_HISTORY_READINESS', 'NO'),
    '0903733': ('Owned LocalPlay synthetic identity lane и exact legacy quarantine; diagnostics owner composition.', 'POTENTIAL', 'POTENTIAL_IDENTITY_QUARANTINE', 'POTENTIAL_STORE_BOUNDARY'),
    '9050c5d': ('Autosave rejects headless либо offline callbacks до native store.', 'POTENTIAL', 'NO', 'POTENTIAL_RETIRED_CALLBACK'),
    '3f9d79a': ('Native maxima refresh расширен DEAD→READY/DEAD после background level changes под current-vitals monitor.', 'POTENTIAL', 'NO', 'POTENTIAL_AFTER_LOAD_CAPTURE'),
    'ff5cb16': ('Exact pre-9050 quarantine witness V2 и bounded observer selection exclusions.', 'POTENTIAL', 'POTENTIAL_EXACT_QUARANTINE', 'NO'),
    'b4f1f03': ('Owned store PREPARE/native snapshot/FINALIZE crash consistency; lifecycle support и journal.', 'POTENTIAL', 'POTENTIAL_PENDING_STORE_GUARD', 'POTENTIAL_STORE_LIFECYCLE'),
    'ea56bef': ('Pending owned native store recovery и visible executor/travel handoff; scoped journal.', 'POTENTIAL', 'POTENTIAL_OWNED_RECOVERY', 'POTENTIAL_VISIBLE_HANDOFF'),
    '461a4ab': ('Bounded cleanup phase/failure snapshots; диагностические projections без смены gameplay решений.', 'NO_OBSERVATION_ONLY', 'NO', 'NO'),
    'dd58a51': ('Unaccepted candidate007: lifetime-owned native work tickets/contexts; strict executor submission; Player load/restore, timers, AutoPlay и lifecycle drain ownership.', 'POTENTIAL', 'POTENTIAL_ECOLOGY_NATIVE_CONTEXT', 'POTENTIAL_NATIVE_WORK_LOAD_DRAIN'),
    'ffc0b97': ('Allowlisted real-client AutoAttach hook после EnterWorld; существующая REAL_LOGIN/IN_GAME consent lease.', 'NO_NATURAL_PHANTOM_CHANGE', 'NO', 'NO'),
    '8457b90': ('Current materialization demand допускает ecology.native_materialization_required для исторического native-context gate; history остаётся fenced.', 'POTENTIAL', 'POTENTIAL_ECOLOGY_DUE', 'NO'),
    'c23915d': ('Native arrival tracking сохраняет committed item IDs при READY/DEAD, читая фактические native counts/locations.', 'POTENTIAL', 'NO', 'POTENTIAL_AFTER_LOAD_INVENTORY'),
}

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, encoding='utf-8')

history = git('log', '--reverse', '--format=@@%H\t%ad\t%s', '--date=short', '--name-only', GOOD + '..' + BAD)
blocks = [block.strip().splitlines() for block in history.split('@@') if block.strip()]
commits = [block[0] for block in blocks]
rows, review = [], []
for ordinal, commit in enumerate(commits, 1):
    sha, date, subject = commit.split('\t', 2)
    all_files = [line for line in blocks[ordinal - 1][1:] if line.strip()]
    files = all_files
    files = [p for p in files if p.startswith(MODULE + 'java/') and any(any(term in p for term in terms) for terms in PATTERNS.values())]
    # Composition is part of the path, even when its dependent implementations are unchanged.
    files += [p for p in all_files if p == MODULE + 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java' and p not in files]
    files += [p for p in all_files if p.startswith(MODULE + 'dist/game/data/phantoms/topology/') or p == MODULE + 'dist/game/config/Custom/LocalPlayPilot.ini']
    if not files:
        continue
    areas = [area for area, terms in PATTERNS.items() if any(any(term in p for term in terms) for p in files)]
    if any(p.endswith('/PhantomSystem.java') for p in files):
        areas.append('COMPOSITION')
    if any('/data/phantoms/topology/' in p for p in files):
        areas.append('TOPOLOGY')
    diff = git('show', '--format=', '--ignore-space-at-eol', '--unified=0', sha, '--', *files)
    changed = [line for line in diff.splitlines() if line[:1] in ('+', '-') and line[:3] not in ('+++', '---')]
    relevant = [line for line in changed if re.search(r'presence|local|materializ|requestDue|requestMaterialization|admission|native|catchup|reconcile|online|spawn|lease|promot|signal|autoPlay|AutoPlay|AutoUse|return|permits|require|guard', line, re.I)]
    snippets = relevant[:24]
    review.append('\n## ' + str(ordinal) + ' ' + sha + ' ' + subject + '\n' + '\n'.join(snippets))
    semantic, visible, before, after = SEMANTICS[sha[:7]]
    rows.append([ordinal, sha, date, subject, ';'.join(areas), ';'.join(files), semantic, visible, before, after, 'SEMANTIC_REVIEWED_CAUSALITY_UNPROVEN', 'git show --ignore-space-at-eol ' + sha + '; HISTORY_REVIEW.md'])

with (TASK / 'REGRESSION_MATRIX.tsv').open('w', encoding='utf-8', newline='') as out:
    writer = csv.writer(out, delimiter='\t', lineterminator='\n')
    writer.writerow('ordinal sha date subject critical_area files semantic_change could_break_visible_presence could_block_before_materialize could_block_after_materialize confidence evidence'.split())
    writer.writerows(rows)
(TASK / 'HISTORY_REVIEW.md').write_text('# Адресные diff-фрагменты GOOD→BAD\n\nФрагменты для обзора; не доказательство runtime causality.\n' + '\n'.join(review), encoding='utf-8')
(TASK / 'COMMITS_ALL.tsv').write_text('sha\tdate\tsubject\n' + '\n'.join(commits) + '\n', encoding='utf-8')
print(f'commits={len(commits)} critical={len(rows)}')
