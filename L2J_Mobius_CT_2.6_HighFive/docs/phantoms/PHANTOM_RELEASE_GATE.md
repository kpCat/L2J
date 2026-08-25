# Release gate Phantom World

## Предпосылки

- Входной принятый baseline: `b6e634aa17cc287e658a89a45c4632bc50672e93`.
- Общие статусы Goals 001–029 должны быть `ACCEPT` либо эквивалентным финальным принятым статусом с явно сохранёнными будущими контрактами.
- Production Java, shipped config и schema не меняются в Goal 030 Checkpoint 1.
- Канонический shipped runtime остаётся выключенным: `EnablePhantomSystem = False`, population target `0`, ACTIVE target `0`.

## Безопасный fresh bootstrap

Fresh bootstrap выполняется только существующей целью `prepare-phantom-test-db`, ровно один раз перед release gates. Разрешён только loopback endpoint и отдельная allowlisted база `l2jmobiush5_phantom_test`; production-база запрещена. Admin environment и provision-команда выполняются в одном процессе, значения credentials не печатаются и не сохраняются. Ручное исправление SQL, manifest или config после provisioning запрещено.

## Coverage matrix

`test/resources/phantoms/release/goal030-release-coverage.tsv` связывает каждый из 20 release-доменов с принятым Goal lineage, реальными production owners и focused Ant evidence.

- `COVERED_PRIOR` — домен закрыт принятым focused evidence Goals 001–029.
- `COVERED_CP1` — foundation повторно подтверждается на fresh bootstrap в CP1 либо самим CP1 suite.
- `PENDING_GOAL030` — release-specific evidence отложен только на указанный CP2 или CP3.

Checksum старого scenario smoke не считается end-to-end доказательством живого мира.

## Последовательность Goal 030

1. CP1 — `ACCEPT`: release baseline, fresh bootstrap, coverage closure и disabled no-mutation regression.
2. Goal030A — `ACCEPT`: humanization hardening имён и creation-time role ecology без DB/state/hot-path расширения.
3. Goal030B — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`: transient affiliation semantics и инерция личной воспринимаемой репутации без DB/state/owner integration.
4. Goal030C — `NOT_STARTED`: canonical clan/alliance/war context wiring, expulsion/leadership/directive social events и PK/karma recovery.
5. CP2 — `NOT_STARTED_AFTER_HUMANIZATION`: автономный cross-domain alpha-сценарий на едином мире; пакет должен быть сформирован заново после независимого принятия 030B и завершения 030C.
6. CP3: финальное решение о выпуске, rollback и release-level restart/failure recovery.

Release sequence: `CP1 -> 030A -> 030B -> 030C -> CP2 -> CP3`. Coverage matrix и число её строк не меняются.

## Rollback

Rollback использует существующие operator controls: сначала bounded drain, затем disable. Shipped disabled config остаётся последним fail-closed барьером и не допускает автоматический запуск runtime после рестарта.

CP1 доказывает целостность baseline, guarded bootstrap, полное отображение release-доменов и инертность выключенной конфигурации. CP1 не доказывает cross-domain автономность, release-level restart/failure recovery и не принимает финальное решение о выпуске.