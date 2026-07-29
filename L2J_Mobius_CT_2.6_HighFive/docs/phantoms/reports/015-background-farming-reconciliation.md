# Goal 015 — Background farming reconciliation bounded completion

## Status

`BLOCKED`

Все семь bounded completion findings и исходные Goal 015 gates реализованы и
проходят тесты. Полный production corpus audit, однако, не нашёл ни одной
допустимой exact anchor/NPC пары для успешного normal-solo background farming.
Объявлять полный SUCCESS при нулевом production pair count нельзя.

## Summary

- Materialization claim после успешного `beforeMaterialize` получает ровно один
  terminal success/abort callback на всех проверенных status/failure paths.
- Abort идемпотентно возвращает совпадающее durable состояние в `READY`/`DEAD`;
  несовпадение переводит его в `INCONSISTENT`.
- Shutdown materialization ждёт operations, identity leases, transactions,
  retained leases и `MATERIALIZING`; store transition разрешён в `STOPPING`.
- Background state schema v2 хранит compact mutable projection и paperdoll
  proofs; canonical hash покрывает все заблокированные item rows.
- Shot/resource validation использует текущие item templates/handlers, commerce
  supply facts, weapon crystal/type и summon facts.
- WARM recovery имеет bounded exact town teleport, cancellation/timeout и
  проверку canonical destination до store.
- Real login fail-closed проверяет durable background state после захвата
  штатного `REAL_LOGIN` lease.

## Changed files

- Production: `PhantomSystem.java`, разрешённый seam `GameClient.java`,
  background package, три materialization lifecycle files.
- Tests/build: `PhantomBackgroundSuite.java`, `PhantomTestLauncher.java`,
  `build.xml`.
- Verification/docs: verifier 015, architecture contract, roadmap, этот report
  и `015-background-farming-reconciliation-review.md`.
- Не менялись Player, Item, Inventory, Attackable, loaders, schema, config,
  datapack, geodata, другие хроники или исторические verifiers.

## Architecture decisions

- State schema увеличена с v1 до v2; v1 читается и детерминированно повышается
  при следующей canonical записи.
- Mutable IDs выводятся только из explicit goal/authority: shots, summon
  resource, все допустимые current NPC drops и существующие tracked stacks.
- Полная inventory identity не сериализуется в component: SHA-256 охватывает
  все locked rows, compact projection хранит только изменяемые rows и
  paperdoll identity proof. Canonical load/slot facts считаются по всем rows.
- Одна операция сохраняет main/subclass, HP/MP/CP, position, items и auto-get
  skills в одном `autoCommit=false` MariaDB batch с `VERIFY_PENDING`.
- Recovery сохраняет exact canonical coordinates/vitals; EXP/SP/items не
  выдаются и death loss не сбрасывается бесплатно.
- Real login не зависит от in-memory tick lease: любой durable non-
  `MATERIALIZED` state отклоняется, read/verification failure также отклоняется.

## DB, configs and fixtures

- DB: только `l2jmobiush5_phantom_test`.
- Seed: `15001501`.
- Schema/migration/config changes: нет.
- Compact inventory evidence: не менее 100 unrelated non-stack objects,
  payload не больше 4096 bytes, exact stack/non-stack delta, typed concurrent
  conflict и 50 ACTIVE/BACKGROUND transitions.
- Production audit evidence: единственная exact пара
  `22859@giran.farming.22859`; excluded immediate/timed drop IDs:
  `8600–8614`, `10655–10657`, `13028`; supported pair count: `0`.

## Commands and results

- Семь новых focused targets: PASS после точечных исправлений.
- Исходные шесть focused targets: PASS.
- `ant phantom-background-completion-test`: PASS, 13 suites, 40/40,
  3 min 24 s.
- Supported cumulative static set
  (`phantom-static-verify`, `014a`, `015`): PASS.
- Standalone historical `phantom-static-verify-008`: expected FAIL на current
  multi-goal history; build заменяет его cumulative preservation echo. Старый
  verifier не изменялся и не входит в текущий `verify`.
- Единственный final `ant verify`: PASS, 9 min 9 s.
- Standalone `ant jar`: PASS, 15 s.
- Post-commit verifier 015 twice byte-identical: выполняется после commit и
  фиксируется во внешнем handoff.

## Performance and safety

- 100,000 deterministic model evaluations: PASS.
- 10,000 duplicate reconciliations: PASS.
- Background production не создаёт worker/thread/future и не пишет per-tick
  logs.
- Mojibake markers и escaped Cyrillic в изменённых text files проверяются
  verifier 015 раздельно.

## Deviations and limitations

- Полный SUCCESS невозможен: в разрешённом production corpus отсутствует
  поддерживаемая farming-пара.
- Topology/datapack mutation для создания такой пары находится вне scope.
- Party, spoil, manor, raid, instance, PvP, buffs/vitality/premium/event,
  Goal 016/017/025 не начаты.
- Goal остаётся одной capability; Goal 015A/015B не создавались.

## Git and handoff

- Branch: `feature/phantom-world`.
- Required parent: `d41950922f6ceec53aca0326e6210e45353e0bc0`.
- Commit subject: `fix(phantoms): complete background reconciliation gate`.
- Commit SHA: будет передан во внешнем handoff; SHA нельзя записать внутрь
  собственного commit.
- Push: PENDING.
- Next step: отдельное разрешение на production topology/datapack pair, затем
  повтор production audit и independent review Goal 015.
