# План исполнения Goal023

Спецификация: TASK.md, ARCHITECTURE.md, SAFETY.md, ACCEPTANCE.md.
Разрешённое исполнение G0–G8 уже запрошено владельцем; дополнительный design gate не вводится.

Архитектура: существующий TEST022 owner/controller и canonical Java verify-prepared;
отдельный QualityPolicy023, immutable решения и общий STOP без изменения ledger HEAD.
Стек: Windows PowerShell 5.1, существующий JDK25 offline verifier; HTML/CSS без HTTP.

- [x] G0: проверить feature/origin, точный READ_SET, исходные pins и чужой dirt.
- [x] G1–G4: RED→GREEN focused contract, fail-closed policy/reducer/admission.
- [x] G5: русский HTML/MD/JSON с экранированием, provenance и честными UNKNOWN.
- [x] G6–G7: existing30 + post-V2 + focused negatives, hash preservation; encoding в финале.
- [ ] G8: exact scope guard, commit/push, независимый GitHub readback, owner REQUIRED.

Точные code paths (от tools/phantom-runtime/training):
`TrainingV4.ps1`, `TrainingQualityV4.ps1`, `Test-TrainingQuality023.ps1`;
также разрешённый TEST-only `PhantomLearning022V4Trainer.java`: read-only history membership.
Документы: эта task family и единственный одноимённый отчёт в reports.

Review focus: недоверенный status/JSON не разрешает обучение; отсутствие окна не плато;
перезапуск/новая кампания не сбрасывают quota; старые private artifacts read-only;
HTML не исполняет строки из JSON. Synthetic reducer proofs не являются native proof.

Writer audit: controller под lifetime lock; Start/Resume под BaseRoot mutex;
Java prepare/cold/commit — единственный ledger writer; status/report — atomic replace;
новые policy/decision/STOP — create-only. В hot battle loop новых writers нет.

Read scope: initial12 complete. Additional5: ARCHITECTURE/SAFETY/ACCEPTANCE/TEST_MATRIX/
PROGRESS_CONTRACT. Bounded exception: PHANTOM_CURRENT_STATUS (обязателен AGENTS),
EFFICIENCY_HANDOFF (обязательный handoff), Test-PostV2.ps1 (нужно выполнить G6).
Task-local README/code-map/pattern и вложенные AGENTS не найдены; повторного поиска нет.
Baseline 8926e4da5b59d9b9a376dbac8fce05c8fe3a934f; fetch выполнен; tracked dirt отсутствует.

Ruling: comparator не имеет принятого authenticated V4 producer — runtime всегда
QUALITY_UNPROVEN/INSUFFICIENT_EVAL; supported reducer проверяется только синтетически.
Цена ограничения: оценка реального преимущества требует отдельной задачи и owner gate.

Review: независимый reviewer нашёл два Important. Оба исправлены:
1. Historical verify-prepared не допускает advanced HEAD. Новый verify-history проверяет
canonical samples и fingerprint membership в полном текущем ledger; без повторного fit.
2. Missing receipt снижал quota. Теперь проверяются orphan rounds, непрерывность reservation
parent/step и commit sequence, final canonical HEAD относительно frozen start boundary.
Focused tests воспроизвели missing-tail RED и проверили два genuine historical commits
при HEAD216, source negatives и неизменность HEAD при повторном чтении.
Stop/drain не зависит от успешного rendering; integrity fallback не сертифицирует status JSON.
Minor про неверный заголовок integrity fallback исправлен вместе с этим блоком.
Declined to judge: empty. Deferred minors: none.

Bounded scope exception: exact17 = 4 code/test + 13 docs. Исходный package9 публикуется
без изменения байтов; manifest8 проверен. Scope — SCOPE_ALLOWLIST.txt; чужой dirt не staged.
Дополнительные точечные reads после первого patch: canonical native adapter/continuation,
TEST Java command, build.xml и manifest нужны для historical verification и pinned build.
Полных чтений файлов >1000 строк нет; pre-patch repository searches=3.

## Операторская подготовка (только offline build)

Запускать из фактического корня репозитория; pinned learning022-build не перезаписывать.

```powershell
$module = Join-Path (Get-Location) 'L2J_Mobius_CT_2.6_HighFive'
$old = Join-Path $module '.phantom-local\learning022-build'
$out = Join-Path $module '.phantom-local\learning023-build\phantom-test\bin'
New-Item -ItemType Directory -Path $out -Force | Out-Null
javac --release 25 -encoding UTF-8 -cp "$old\phantom-test\bin;$old\runtime-libs\*" -d $out (Join-Path $module 'test\java\org\l2jmobius\tests\phantoms\PhantomLearning022V4Trainer.java')
```

Существующие Status/Report команды — в PROGRESS_CONTRACT.md. Для новых кампаний
guard обязателен; старые sealed configs не мигрируются. Новый prepare-only inherit216
не расходует GS/SQL boots. Quality STOP наследуется через общий latch и frozen lineage;
owner unlock автоматически не реализован. Status/Report обновляют HTML/MD/report JSON,
status JSON остаётся последним snapshot контроллера. Production V4 OFF.

Goal024 handoff: несколько независимых lawful episodes за один GS lifetime, frozen candidate
на всю пачку, offline fit/cold после drain; отдельные ownership IDs и no replay credit.
В Goal023 эта работа не начиналась; owner gate REQUIRED.
