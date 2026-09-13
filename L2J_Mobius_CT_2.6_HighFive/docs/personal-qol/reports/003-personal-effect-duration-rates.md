# L2-QOL-003 evidence report

Дата: 2026-09-13

Branch: `feature/phantom-world`

Required parent: `7fa6789802555afb9b1962f82413e5c79b649362`

Результат: **SUCCESS**

## Scope и source audit

До правок прочитаны `AGENTS.md`, корневой `README.md`, документы Personal QoL и Phantom World, весь task package L2-QOL-003, владельцы `Skill`/`BuffInfo`/`EffectList`/`Formulas`, restore/store, `SkillTreeData`/`SkillData`, существующий config loader и test/build harness. Корневой `AGENTS.md`, module `README.md`, `docs/personal-qol/AGENTS.md`, `CONTEXT_INDEX.md` и `DEVELOPMENT_CHAT_HANDOFF.md` не найдены. Локально переиспользованы strict fail-closed config/isolation QOL-001/002, immutable settings snapshot, stock `BuffInfo` construction, canonical `SkillTreeData` ownership и текущий deterministic suite/DB guard pattern.

UI-технология в scope отсутствует: клиентский UI, server-rendered HTML и WinForms не менялись. Не менялись public schema, SQL, `Skill._abnormalTime`, `Formulas`, tick/reuse/cooldown/chance/power, Phantom lifecycle/algorithm, Goal029/Goal034 и старый Phantom freeze. Goal040 не создавался.

## Реализация

- в существующий `PersonalCharacterQoL.ini` добавлены shipped-OFF duration switch, отдельные Buff/Dance/Song multipliers и пустой override list;
- legacy QOL-002 config без новых ключей остаётся valid/disabled; частичный или malformed QOL-003 блок отключает только duration feature и сохраняет валидные QOL-002 allowlist/cross-class/crystallization settings;
- multipliers строго ограничены конечным decimal-диапазоном `0.01..100.0`; override имеет bounded формат `positiveSkillId,multiplier`, максимум 4096 символов/256 уникальных записей, immutable после разбора;
- stock `Formulas.calcEffectAbnormalTime` остаётся первым и полным владельцем расчёта; personal policy масштабирует его результат один раз при создании конкретного `BuffInfo`, не меняя общий `Skill` template;
- runtime off-path возвращает stock result до music classification; affected определяется только получателем и допускает allowlisted active subclass, но исключает ordinary Player, headless Phantom, summon/pet и NPC;
- passive, toggle, triggered, abnormal-instant, debuff, negative и не-continuous эффекты остаются stock; override не обходит exclusions;
- Song/Dance snapshot строится по актуальным полным H5 деревьям `SWORDSINGER`/`SWORD_MUSE` и `BLADEDANCER`/`SPECTRAL_DANCER`, только для реальных `SkillData.isDance()` templates; конфликтное/неизвестное music ownership остаётся stock, reload явно инвалидирует snapshot без polling/thread;
- override заменяет category multiplier; округление детерминировано, положительный результат имеет минимум 1, overflow насыщается до `Integer.MAX_VALUE`;
- positive explicit `abnormalTime` остаётся авторитетным; recast и сохранённый remaining time не масштабируются повторно.

Фактический H5 corpus: Might `1068/3` — positive Buff, base 1200 секунд; Song of Hunter `269/1` — Song, base 120 секунд; Dance of Fire `274/1` — Dance, base 120 секунд; Hex `122/15` — debuff/negative, base 30 секунд. Названия, уровни, metadata и base time взяты из текущих `SkillData` XML, а принадлежность music — из текущего `SkillTreeData`, не из имени или диапазона ID.

Restore/transfer audit нашёл три владельца положительного explicit time: `Player.restoreEffects` передаёт `remaining_time`, `SummonEffectTable` передаёт сохранённый `effectCurTime`, `AdminSuperHaste` передаёт операторский time. Во всех случаях `Skill.applyEffects` сначала создаёт `BuffInfo`, затем при `abnormalTime > 0` вызывает `setAbnormalTime`, поэтому custom/remaining time окончательно заменяет constructor duration. `StealAbnormal` аналогично создаёт новый `BuffInfo`, а затем копирует `infoToSteal.getTime()` через `setAbnormalTime`. Других transfer/copy владельцев или прямых вызовов `BuffInfo.setAbnormalTime` в production source не найдено. Focused suite защищает explicit apply, steal/copy setter, реальный relog/restore (`restored=71`) и recast.

## Test evidence

Qualified candidate: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol003-rc-20260913-01`; sparse checkout содержит только High Five module, branch `feature/phantom-world`, HEAD до overlay `7fa6789802555afb9b1962f82413e5c79b649362`. Три исходно изменённых пользовательских файла и прочие untracked artifacts в candidate не переносились.

Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не выполнялся. DB-тесты использовали только allowlisted fixture `l2jmobiush5_phantom_test`; credentials в отчёт не выводились. SQL schema/data в commit не менялись.

Candidate-only materialization сохранил принятую historical EOL-рецептуру из квалифицированного QOL-002 candidate: 115 game SQL, 6 login SQL, два schema-manifest migration fixtures и два canonical-LF Goal020 semantic inputs были перенесены только для воспроизводимой qualification. Эти файлы в commit не входят.

- `ant compile-tests`: **PASS**; скомпилированы 2260 production и 149 test sources, сохранены два существующих предупреждения `System.runFinalization`.
- `ant qol-effect-duration-test`: **5/5 PASS**; проверены strict config/isolation/master OFF, pure policy/exclusions/1.0/rounding, реальные H5 Might/Song of Hunter/Dance of Fire/Hex и reload-aware ownership, self/NPC/ordinary/headless/summon recipient semantics, global-then-personal ordering, Skill immutability, override, explicit/steal-copy time, recast и relog/restore.
- `ant qol-003-affected-test`: **PASS**.
- Historical static chain 014/014a/015/016/017/018/019/020c1/020c2/022c1/022c2: **PASS**.
- Goal039 structure/static/documentation canary: **PASS**.
- `ant qol-003-verify`: **PASS**, 14 минут 21 секунда.
- Один fresh qualified `ant verify`: **PASS**, 31 минута 48 секунд; повторный qualified run не выполнялся.
- standalone `ant -q jar`: **PASS**, 17 секунд; оба финальных архива успешно прошли `jar tf`.

До qualification первый диагностический запуск полного `verify` в ещё не квалифицированном sparse candidate остановился на старом Goal020 semantic hash: перенесённые Windows-копией semantic inputs имели CRLF, тогда как historical verifier требует canonical LF. Это не было отказом QOL-003. В candidate-only materialization восстановлены ровно два документированных canonical-LF Goal020 input из принятого QOL-002 candidate; их SHA-256 совпали с зафиксированными значениями, отдельный `ant phantom-semantic-activation-test` прошёл **3/3**, после чего единственный qualified полный `ant verify` прошёл целиком.

В раннем affected run проявились два независимых исторических transient: cleanup race в `acquisition-manor-active.after-all` и вероятностный sample в `acquisition-quest-active.01`. По одному точному focused rerun подтвердили **2/2** и **4/4** соответственно; финальные aggregate и полный qualified `verify` прошли без этих отказов. Изменений в этих подсистемах нет. В логах остались только ожидаемые negative-control сообщения и два старых предупреждения о deprecated `System.runFinalization`.

Goal029 soak и Goal034 real-stack не запускались: QOL-003 не меняет Phantom lifecycle owner и focused regression этого не потребовал.

## Final artifacts

Финальные JAR из `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol003-rc-20260913-01\build\dist\libs`:

- `GameServer.jar`: SHA-256 `F3724B8DBD924363A45DECE34C87EA3881ECE5951B5EAEFC5A342E26E1D2C8C1`, 9022557 bytes, 4321 entries; `jar tf` подтвердил классы `PersonalEffectDurationPolicy`, `PersonalEffectDurationService` и `PersonalEffectMusicClassifier`;
- `LoginServer.jar`: SHA-256 `9C65C8E1CEAC0402F7BD6C1E5908CF03964064D8BF5ECD5D2ACADD0A7FE28DBC`, 313192 bytes, 200 entries.

Commit subject: `qol(003): add personal effect duration multipliers`.

Client UI: **NOT_TESTED_CLIENT_UI**. UI не менялся; client patch не требуется.

QOL-001/002/003 закрыты как SUCCESS; базовый Personal QoL завершён. Vitality/rate/premium-item идеи сохранены только в backlog и не реализованы.

Перед qualification exact overlay parity составила 15/15 task-owned files. После qualification изменён только этот evidence report; parity остальных 14/14 production/test/config/docs files сохранена. Task package, исходно изменённые пользовательские файлы, candidate SQL/EOL materialization, local DB configs и JAR не входят в финальный scope. Финальный index содержит ровно 15 разрешённых файлов; `git diff --cached --check`, strict UTF-8 и control-character checks — **PASS**.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Control characters в изменённых файлах проверены: совпадений нет.
