# PHANTOM-LEARNING-019 — PARTIAL

**PARTIAL019_OFFLINE_LEARNED_NATIVE_UNPROVEN. Manual gate: REQUIRED.** Реальное обучение и холодное восстановление работают. Единственный GameServer boot остановился до CUT: `V4_NO_LAWFUL_SOLO_OPENING`. FULL019 не объявлен; Goal020 не начат.

## Реальный результат

Из сохранённой native016 cohort `autonomous009-learning016-g2-v1-c000001` прочитаны **247 отдельных оплаченных действий, 27 актёров / 27 эпизодов**. Проверены pre-CUT seal/manifest, физические SHA bindings, hashed schedule, deterministic episode ID, reserved slot/block/split/cohortSha/owner, post-drain settled016 SHA, canonical terminal codec и независимый native JSON: observation bytes SHA, action/HP/MP/time/owner suffix. Текстовое представление Map не используется как canonical codec: порядок Map зависит от JVM.

TRAIN: **161 реальный SGD update**; heldout: **86**, без градиента. Label — индивидуальный `TacticalReceiptV3.reward`; EXP/SP и terminal reward не входят в V4 objective. Уже сохранённые terminal envelopes используются только как доступный typed источник отдельных rows. Party casts без такого replay в этой поставке не импортированы: их нельзя объявлять обученными.

Компактная immutable модель: **128 коэффициентов**, action features + контекстные взаимодействия с known masks, Huber gradient clip 0.25, SGD 0.05 с нормировкой, bounded weights и batch ≤256. SHA до: `6eb5cb07c1fc722a13e0616331d16ea78a45f310603796dc49c739e1a61fc364`; после: `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`.

Для двух исходных законных DAMAGE alternatives `[1230,1275]` old scores `[0,0]`, new scores `[-0.0016417800306151521,-0.0015866459845373462]`. Stock и итоговый выбор — **1275**, reason `MARGIN`: разница меньше неизменного порога 0.025. Влияние на оценки доказано; смена ранга/исполненного действия **NOT_PROVEN**.

Checkpoint сохранён atomic temp→rename; отдельный JVM восстановил те же bytes/SHA/score. Повторный импорт после cold restore: **0 новых updates**. Tamper/truncation отклонены. Heldout-only checkpoint сохраняет event/split ledger при updates=0 и отвергает последующую смену episode split. OFF, missing model, critical и timeout возвращают stock. HEAL execution/ranking не расширялся; MP/skill/target/Geo и native dispatcher остаются штатными.

## Единственная native-проба

Физическая TEST-копия `train019`, отдельные loopback порты **33359/18019/19319**, существующие `Runtime`/`CombatService`, настоящее materialization сохранённого class110 Solo actor. Платные эффекты не фабриковались; выдачи навыков, изменения HP/MP и создания цели не было. После materialization штатный поиск допустимой цели вернул пустой набор. **MODEL_CONSULTED=0, MODEL_REORDERED=0, NATIVE_EXECUTED=0, EFFECT_PAID=0.** Повторный GS запрещён бюджетом и не выполнялся.

GS starts=**1**, boot/run/stop и SQL cleanup **48 125 мс**. Process exit=0 оказался следствием штатного Shutdown после main exception, а не успехом. Добавлен regression RED→GREEN: runner теперь требует положительный model-bound native proof, один exit0 недостаточен. Старые stdout/stderr/safety artifacts пробы не переписаны.

## G0–G8 и сохранность

|Gate|Результат|
|---|---|
|G0|PASS: SOURCE fast-forward до f7638db7, feature/phantom-world; MAIN только read-only, TEST dirt сохранён.|
|G1|PASS: 247 authenticated native016 action rows из27episodes; fake/foreign cohort и split отказ.|
|G2|PASS: 161 SGD update, изменены веса/SHA/оба control scores.|
|G3|PASS: separate-JVM cold exact, replay0, tamper/truncation и heldout split guards.|
|G4|PASS_OFFLINE: explicit TEST-only V4 hook, default OFF, законные DAMAGE scores и stock fallback; native влияние не наблюдалось.|
|G5|FAIL: V4_NO_LAWFUL_SOLO_OPENING до CUT; повторный GS=0.|
|G6|PASS: 12 103 historical files exact до/после; исходная SQL data, V3/Adam/TEST2/DRAFT14/pointer и016 не изменены; OFF/own0/listeners0.|
|G7|PASS: targeted019 и jar; каждый offline ≤120с, native48.125с, суммарные проверки <6мин.|
|G8|POST_PUSH_CHECK: exact scoped delivery и независимый GitHub HEAD/parent/diff/full-body readback фиксируются в итоговой передаче; manual REQUIRED.|

Исторический logical SQL183 SHA из017: `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`. Новый SELECT183 не выполнялся: исходная SQL не запускалась; вместо этого доказана побайтовая неизменность её физических файлов до/после. Новая SQL — отдельная копия. Shared MariaDB, MAIN/PLAY, защищённые releases и Studio не запускались и не изменялись. Geo/protected manifests и исходный runtime JAR входят в preservation pins; исходные game data/geodata использованы только для чтения/копирования.

## Проверки и поставка

`phantom-learning019-test`: exit0, первоначально37с; после review fixes72с, offline1910мс и отдельный cold JVM. Final delivery offline851мс/cold exit0. `ant ... jar`: exit0, final18с. `Test-Training.ps1 -ActionValue019`: RED на ложном native success → GREEN, GS0/SQL0. PS parse и `git diff --check`: exit0. Пять прежних compiler warnings в unrelated tests (ExtendedOpenOption/System.runFinalization) сохранены. Полный suite/кампании/FIT/EVAL/promotion не запускались. Единственный review нашёл2 Important; оба закрыты focused offline regression за один fix pass.

Изменены только три V4 класса, TacticalPolicyV3, два TEST файла, build.xml, три существующих training scripts и документы019. Это bounded exception:10 code/build/script files плюс разрешённые task/report artifacts, одна подсистема learning. README/AGENTS/workflow/status и указанные локальные аналоги прочитаны; вложенные AGENTS/code-map/pattern-файлы для этого scope не найдены.

Git разрешён текущей задачей: `symbolic-ref --short HEAD`, `rev-parse HEAD`, `status --short`, `worktree list` для baseline/scope; `fetch origin feature/phantom-world`, `merge --ff-only origin/feature/phantom-world` по START_HERE; scoped `diff`/`diff --check`, exact `add`, `commit`, обычный `push origin HEAD:feature/phantom-world`, `ls-tree` для независимой blob сверки. Без force/reset/clean/stash и новых веток/worktrees. Commit SHA и GitHub readback сообщаются после публикации, без самоссылочного hash цикла.

- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Для закрытия G5 потребуется отдельное решение владельца о новом native бюджете и заранее подтверждённой lawful target admission. Автономная ночь, обученный HEAL/Tank/CONTROL/PvP и игровое преимущество не готовы и не заявлены.
