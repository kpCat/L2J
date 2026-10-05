# HANDOFF010 — PARTIAL, materialization RED, M1 OPEN

Реальный TestAdmin автоматически получил Pilot ARMED_IDLE без arm-кода.
REAL_LOGIN и native IN_GAME guard подтверждены server-side. Code ffc0b97e2ee
опубликован только experiment/m1-candidate007-observe008.

Observation window12m8s,106 server samples,0 visible materialized Phantom;
пользователь подтвердил «Phantom не видны». Runs имели перерывы/штатные отмены;
непрерывный десятиминутный run не подтверждён. AUTOPLAY_5_CYCLES=NOT_OBSERVED.
Самый ранний пользовательский blocker: no visible materialization рядом с TestAdmin.
Profile110 remained STORED/object0/epoch0 при accepted locality/prewarm;
diagnostic reason native_context.required:coalesced. Gameplay/AI fixes не делались.
Полные факты и ограничения: RESULT.md, LOGIN-ENVELOPE.tsv, OBSERVATION.tsv.

Активный Pilot run остановлен штатно. Consent до disconnect остаётся ARMED_IDLE.
Login19584 и Game5956 живы; graceful CloseMainWindow=False у обоих.
До exact-PID force необходимо разрешение пользователя по PLAN.md.
Проверить native disconnect OFF/no session после выхода пользователя, затем cleanup.
Private clone/runtime сохранить; PLAY не изменять. Не продолжать task007 и не закрывать M1.
