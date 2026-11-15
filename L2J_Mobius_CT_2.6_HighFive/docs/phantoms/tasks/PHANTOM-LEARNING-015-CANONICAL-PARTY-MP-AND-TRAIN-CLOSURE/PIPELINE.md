# Планирование TRAIN без дорогостоящих бесполезных ожиданий

## Existing native sequence
`discoverSources → planCells → acquiredScenes → admitTarget/Scene → admitHeadlessCell → A/B prepareAcquired (roster materialization/natural recovery/Party admission) → sealed cohort → GS/CUT → receipts → FIT`.

Открытая проблема: Stage `admitHeadlessCell` инициирует две реальные arm-подготовки, часто каждая по 134 секунд, хотя canonical MP-budget затем отказывает. Эти отказы повторяются в будущих candidate-предложениях при одинаковых зафиксированных фактах.

## Предлагаемое изменение
На стабильном кандидате и до тяжёлой части `admitHeadlessCell`:
1. Приобретённые skills, native mechanics, selected shortlist (все варианты) и exact target/candidate pinned. Stateless canonical necessary-only MP preview; capture evidence source/config/roster/target/loadout SHA.
2. `PROVEN_IMPOSSIBLE`: записать отказ с причиной `NECESSARY_PARTY_MP_UNREACHABLE` и доказательством bound, **не** выполнять roster materialization/recovery и этот matched pair; всё равно учитывать candidate in sampling/denominator.
3. `UNKNOWN/MAYBE`: полный штатный A/B headless — никакого ускорения ценой ложного допуска.
4. Каждый следующий candidate не выбирается по известному исходу прошлой EVAL; равномерный coverage (не только лёгкие Solo), минимум две разные класса, Party+Solo. Нельзя делать таинственный skip selected failure logs или reset seed ради зелёных данных.
5. `full seal` только на минимум3 *различных* законных cells (episodeBudget51), минимум2classes, Solo+Party, минимум2 **фактически доступные** законные альтернативы в supported action family, пройденный независимый physical parity и две свежие incarnation identities.
6. Headless и live phases связаны typed immutable payload; любой изменившийся источник/JAR/Geo/target/skills/MP/config → reject/redo pre-CUT. Никакого reuse post-CUT evidence под видом preflight.

## Budget
2 readiness sessions total ≤1200s inclusive of SQL/worker/cleanup, recommend first480s+second720s, caps frozen. Один preflight/stateless unit check до первой headless; если negative cannot be conservatively proven — не тратить слоты на удачу, вынести на review. После G3 ≤3GS/60native-min; max один FIT при достаточных fresh TRAIN/TUNING/AUDIT.

## Реальные результаты
Указать отдельно `full paired cells`, `all admitted arms`, `time-to-first/third cell`, `Party MP early excludes` count/cost, `UNKNOWN` share, `late refused despite preview`, `false exclusions` count, per class/family, CPU/peakRAM and claimed relative speedup only under comparable work. Если G3 не достигнут, **не** запускать GameServer, не утилизировать historical EVAL012 как TRAIN, не подгонять difficulty, не менять native thresholds.

## Не входит
Новая trained BUFF/CONTROL/AoE, full PvP/Raid/self-play, изменение роли танков, действие Return, ранняя автоматическая установка model in GameServer, отдельный учебный сервер/LLM — за пределами 015. Рабочая production buff maintenance без изменений.
