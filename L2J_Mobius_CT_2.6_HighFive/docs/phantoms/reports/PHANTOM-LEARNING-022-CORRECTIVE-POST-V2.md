# Goal022 — corrective POST-V2

Тот же Goal022. Owner gate **REQUIRED**; Goal023 не начат. Production V4 **OFF**, SUPERIORITY=NOT_EVALUATED.
Baseline `0cf5681cf156219a43930bbdeb283e126c2d7ccc`, branch `feature/phantom-world`.
**CORRECTIVE022_POST_V2_READY_FOR_OWNER_REVIEW**. G0–G7 PASS; owner gate REQUIRED. Source commit `2cf50de22c95381b9b5e64087a1299a92faccd05` опубликован ordinary push/exit0; независимый GitHub readback подтвердил HEAD, direct parent, exact15 diff и все15 full bodies/blob SHA. Факты — в `CORRECTIVE-POST-V2/RESULT_CORRECTIVE.json`; docs-only follow-up фиксирует этот readback, его окончательный SHA сообщается владельцу отдельно.

## Причина и исправление

Сохранённый глобальный V2 authority безусловно применял старые Minutes15/BootLimit1/previousCampaign к следующему Start; независимый сеанс не задавал cursorStart.
Добавлены `ActionValue022NewCampaign` и `ActionValue022PrepareOnly` в существующий CLI. Одноразовый V2 и самостоятельный TEST Start теперь имеют отдельные admission paths.
Используются прежние mutex, ownership/process guards, create-only receipts и atomic `Write-TrainJson`; Java codec/боевой код не менялись.
Новый source проверяет неизменённые authority/claim, все три boot receipt, sealed состояние V2, config, physical committed HEAD/model и authenticated preflight cursor.
Скопированный ledger проходит canonical Java `head`, `commit` к тому же HEAD (проверка существующего cold), `verify-prepared`, `proposal`.
Переносятся все candidate/ledger/cold/prepared seals с exact SHA; global event/episode keys и сегменты не очищаются.
Новый config фиксирует provenance, cursor4, previous HEAD/model/config/preflight, отдельные deadline и boot budget. Исторический V2 остаётся3/3; новый счётчик0.
Публикация active pointer происходит последней. При сбое admission receipt и неполный root сохраняются, прежний pointer доступен, автоматического cleanup нет.
Источник без своих новых committed rounds проверяет сохранённый cursor через закреплённый предыдущий source; fallback0/2 отсутствует.
Start с recovery/foreign identity и Resume с истёкшим budget/deadline отказывают до broker. Pause/Stop не меняют срок.

## Read-first и scope

Прочитаны root/module AGENTS, corrective TASK/CONTEXT/ARCHITECTURE/ACCEPTANCE/SAFETY/TEST_MATRIX, три целевых PS, V2 note и выборочные result fields.
Дополнительно прочитаны нужные ownership, ledger/curriculum API, workflow contract и bounded TEST-native candidates API по фактическому proposal stderr.
READ_SET определён в CONTEXT; отдельного READ_SET.md, вложенных AGENTS для training/task нет. Повторный поиск не выполнялся.
Локальные аналоги: V2 create-only claim; immutable reservation/campaign config; canonical cold→commit; существующий30-case PS runner.
Сохранены PowerShell5/Java25, архитектура TEST022, production OFF. C#/UI/Java/SQL/Geo/библиотеки/старые reports не менялись.
Bounded exception: три code/test paths + существующий corrective-пакет, его focused test/result и один report — ровно15 файлов одной TEST022 family; exact allowlist обязателен.
Начальный dirt содержал untracked пользовательские файлы; tracked scope исходно чистый, index пустой. Чужие файлы не staging/restore/cleanup.

## Gates и evidence

G0: fetch origin и независимый GitHub connector подтвердили baseline HEAD и parent `9dbf16ee2e0529d13e51758a7698ccd389af8c2a`; relevant scoped diff исходно пустой.
G1: V2 authority/claim/config и historical boot pins проверяются read-only; третий receipt закреплён отдельно; V2 retry/четвёртый boot REJECT.
G2: реальный CLI готовит отдельный480min TEST plan; без opt-in REJECT; подготовленный и RUNNING повторные Starts идемпотентны.
G3: parent216 exact, updates216/keys302/sequence2/cursor4, следующий proposal MODEL_V1; immutable ledger переносится побайтово.
G4: prepare-only проходит реальный bootstrap на собственной metadata-копии без Geo/game/SQL data; останавливается до broker; own native/SQL0, portsFree.
G5: claim missing/wrong, spoof authority, stale active206, HEAD/model/cursor tamper, incomplete reservation, foreign controller/native PID, stale source, исторический receipt tamper и четвёртый boot REJECT.
G5 также проверяет corrupt cold→сохранённый old pointer+orphan, повтор incomplete admission REJECT, Status во время preparation, concurrent fresh Start и duplicate prepare, Pause/Stop/deadline.
G6: существующие30 checks и15 negative cases GREEN/exit0; positive/race/rollback/legacy cases тоже PASS;1944 исходных файла byte-identical. Status/Report read-only, 020/021 dispatch unchanged.
G7 PASS/exit0: exact15 scope, полный diff, diff --check, две encoding проверки, scoped commit и ordinary feature push. GitHub: HEAD2cf50de, parent0cf5681, ahead1/behind0, exact15 diff,15 полных файлов/blob matches; direct parent отдельно прочитан через GitHub Git commit API.

До исправления реальный Start на V2-копии дал `TRAIN022_CONTINUATION_PREVIOUS_CAMPAIGN`; RED runner exit1: `post-V2 CLI switches missing`.
Дополнительный RED выявил отсутствие явного REJECT на RECOVERY_REQUIRED; corrected. Fixture-only ошибки cohort metadata, private ACL и test-process nonce исправлены без ослабления production gates.
Независимый read-only review выявил foreign native PID, empty-session cursor, missing-source bootstrap188 и третий V2 receipt; все исправлены, bounded rereview: Critical/Important0.

## SHA и команды

Model188 `90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6`.
Model206 `99ebcd8fe606ab54379f59dc411fec0f493b8d388009b0f564eec548f76ba537`.
Model216 до/после `90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6`.
HEAD216 до/после `1ff7547d9d4571673e1e5bfa58a1f3ac3799e4d95f587a32db9ae39622df2f69`.
Authority до/после `1039643EA733AB14989D32F56B10E76CA67FA941051B89AB7A814D06ABB44BDA`.
Claim до/после `E571C195D130EB755F1097EAFEC9F3CFEFC94B47AB1BD133DE61CBE293182783`.
V2 config `CECDAE6E7DCA60436C74865B18B9C65D44233FAEEEECCD2FF40E3BF0D6A7D160`; third boot `E0F819A3D74FEBAB33A512E0D86CF5F31D8E863BACF8BA7824A926EBCC93E351`.
Private evidence: `.phantom-local/logs/PHANTOM-LEARNING-022-CORRECTIVE-POST-V2/`; raw/weights/nonces не публикуются.

Focused command из repository root:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training022.ps1 -CorrectiveHistoryRoot $readOnlyOwnerHistory -EvidenceRoot $freshOwnedEvidence
```
PowerShell runner запускает только offline Java verifier. GameServer starts0, SQL starts0, native rounds0, 480min starts0. Full Ant verify/performance/native/production trials NOT_RUN.
git использован по прямому TASK/user разрешению: `status --short --branch`, `status --porcelain=v1 -uno`, `symbolic-ref --short HEAD`, `rev-parse HEAD HEAD^ origin/feature/phantom-world`, `worktree list`, `fetch origin feature/phantom-world`.
Также exact-path `diff --name-only <baseline> -- <three PS paths>`, `diff --stat -- <three PS paths>`, `diff -- <three PS paths>`, `diff --check -- <allowlist>` и `diff --cached --name-only/--check/--stat`, `ls-tree -r HEAD -- <allowlist>`.
Публикация: `git add --pathspec-from-file=<SCOPE_ALLOWLIST.txt>`, scoped `git commit -m <message>`, `git push origin HEAD:feature/phantom-world`; для docs-only readback follow-up — `git add -- <этот report> <CORRECTIVE-POST-V2/RESULT_CORRECTIVE.json>` (ровно2 exact allowlist paths). Force/history rewrite нет.
Mojibake-маркеры в изменённых файлах проверены:27 markers ×15 файлов, совпадений0, exit0 проверки (rg1 означает отсутствие совпадений).
Escaped Cyrillic в изменённых файлах проверены: все6 regex ×15 файлов, совпадений0, exit0 проверки.

## Ограничения

Это offline corrective, не доказательство480min устойчивости или качества combat AI. Физические private artifacts доступны локально; GitHub содержит только код, документы и sanitized hashes.
Source script TrainingV4 намеренно обновлён corrective; исторический native build/owner/catalog pins остаются строгими. Старые config/authority/checkpoints не переписываются.
Future native runtime требует отдельного решения владельца. Manual gate REQUIRED; ACCEPTED_FULL022 не объявлен.
Внезапное отключение питания и directory metadata durability не тестировались; orphan admission требует ручного анализа. Elapsed до source readback — около31мин (точные секунды в result), внутри55мин; окончательный docs-only HEAD/readback сообщается владельцу после публикации.
