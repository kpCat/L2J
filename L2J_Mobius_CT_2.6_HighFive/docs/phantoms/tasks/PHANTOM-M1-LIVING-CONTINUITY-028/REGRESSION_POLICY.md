# Что повторять и что не переисследовать

Перед правками один base sanity targeted, не ночь на старых all-suites.
На каждом собственном RED — focused tests + ближайшие affected boundary tests.
На final frozen SHA:
- existing PhantomLifecycleCompletion027Suite:11cases;
-18 required routes из R12_FROZEN_REGRESSION027 matrix, собственные safe DB lanes;
-affected LocalPlayPilot/Synthetic tests;
-new028 transport/continuity/native return contracts;
-jar и compile-tests через существующие targets init/init-test (не выдумывать ant clean).

Повторный запуск после fixture/setup failure разрешён только с конкретной поправкой;
отличать INVALID fixture от настоящего RED. Negative stale/fatal tests не ослаблять.
No shared TEST metadata edits; own cloned lane сохраняет существующий restore gate.

Actual crash027 proof не повторять автоматически: product core/transactions028 read-only.
Составить PROVENANCE.tsv: gate, observedSHA, dependency paths/hashes, currenthash,
reused/new, limitation. Если зависимость изменена — не переносить PASS автоматически.
Final run tests и runtime не утверждать по библиотечным standalone tests пакета.
