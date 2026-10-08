# Статусы TASK026 и M1

TASK_RESULT=GREEN только если FARM_A/FARM_B, relevant REGRESSION, CALLBACK_DRAIN,
WHOLE_GROUP_PERSISTENCE, SAME_DB_RESTART все PASS. Не использовать «цель достигнута»
как M1_CLOSED. Имеются product RED — FAILED; недоступный scope/env — BLOCKED.

## Natural scenes A/B
Один frozenSHA,360–420s каждая, stock GameServer/scheduler/NPC AI, sampling<=5s.
Primary два назначены заранее. Каждый primary:>=5completed cycles, native EXP/SP>0,
самостоятельный переход к следующим целям, same epoch, no unexplained maxIdle>90s,
реальное продолжение (reward/complete cycle) в final120s. Подтверждённые bounded
resource pauses не являются generic excuse на PASS и не меняют эту проверку.

Вся выбранная cohort в отчёте; неprimary тоже повторяет полезные native действия,
не менее2complete cycles и tail progress, либо его separate lifecycle outcome явно
доказан, но full SAME_EPOCH_FARM gate такого actor остаётся FAIL. Не уменьшать denominator.
Случайная смерть не оправдывает простой; death-recovery проверяется отдельно.
Первый SELECT nativeCounters без producer доказательства не доказывает корректный kill.
Loot: truthful lawful pickup/autoloot with inventory witness; нет выпавшего/допустимого
loot — NOT_APPLICABLE с источником, а не выдуманные предметы. Loot всей группы учитывается.

## Ownership/accounting
N02/S12 разобраны с preserved old RED, same fixture paired и named original/direct
entry. Никакого fake killSequence/overflow reset. Полный provider-held earned callback
до sealed capture; отсутствие первого healthy-stop timeout, не только повторный stopPASS.

## Persistence
R02 all initial actors sealed native→SQL exact, включая retained/dead actors. Full
inventory/skills, completed context, pending0. Два actual sameDB restart exact при
измерении до обычного progression. Cold452 PASS сохраняется без manual finalize.

## M1 server final
M1=WAITING_FINAL_CLIENT только когда дополнительно R03/R04(если применимо),
DEATH_RETURN_FARM, SOFT_RETURN_BACKGROUND_REMAT, no pop-in under human locality и
остальные final server gates действительно PASS на финальном SHA. Для новой narrow
farm task разрешено TASK_GREEN при этих дополнительных NOT_RUN, но тогда M1=OPEN
и следующий residual plan точен. M1_CLOSED без final real-client acceptance запрещён.

По неподтверждённой причине — UNKNOWN. Не превращать неизвестность в уверенный
диагноз или конфиг-обход. Package self-tests не считаются server gates.
