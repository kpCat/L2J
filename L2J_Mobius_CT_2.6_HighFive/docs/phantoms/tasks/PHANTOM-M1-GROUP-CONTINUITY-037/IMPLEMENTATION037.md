# TASK037: сохранение долга live epoch

STOP_AUTHORITY=TASK037_CONTRACT. M1=OPEN; REAL_FINAL=NOT_RUN.

Read-first: AGENTS/README, пакет037, workflow/masterplan, PhantomVisibleAutoPlay,
PhantomBackgroundDecision, PhantomHistoricalBackgroundService/Planner, native evidence,
stock AutoPlay, lifecycle store и ближайшие fixtures032/026/036. Отдельных code-map/pattern
файлов не найдено; используется SOURCE_MAP.tsv. Новая архитектура и внешние providers не нужны.

Установленный текущий дефект: после stop/replan внутри одного native lifetime новый
Session начинал useful debt заново. Trace148: damage=0, epoch466898325989400,
usefulSince466951570425400→467042072712100. Это отдельный дефект adapter; он не объявляется
доказанной единственной причиной всех пяти исторических FAIL.

G37/G38 на исходном Jar воспроизводят сброс часов на20с без native damage. Первый вариант
G38 использовал чужую запись revision и корректно получил guard rejection; этот невалидный
setup сохранён отдельно. Исправленный fixture использует существующий factual local replan/CAS.
Оба валидных RED:0/2, один и тот же ожидаемый assertion о useful debt.

Минимальная правка одного production файла PhantomVisibleAutoPlay:
stop отзывает прежние registrations и помечает Session inactive; same Player+epoch сохраняют
долг и ресурсное состояние при следующем start, включая смену goal/revision. Новый Player/epoch
не наследует прежнее состояние. Existing terminal lifecycle beforeStore/materializeAborted и
beforeMaterialize удаляют retired Session. Отдельный map, новый service или combat engine не добавлены.

Проверяются exact registries после resume, неизменность damage, исходный90с deadline и удаление
Session terminal lifecycle. Guard нового037t расширен в существующем Headless environment;
старые lane exclusions сохранены. Compilation PASS; GREEN и финальные сцены ещё не заявлены.

История036:27 PASS. Пять остальных последние sampled живые; region delta по World.SHIFT_BY=11
у28=[2,3,0], у40/57/106/173=[1,3,0] от неподвижного observer. Prewarm допускает максимум2,
couldKnow1. У28/40 также записан presence.no_current_local_demand. У57/106/173 конкретный
межкадровый producer удаления пока не доказан.173: первый PHASE_DEADLINE, REGEN120с, MP
достаточна, positive damage отсутствует. Current probe подтверждает касты на уже мёртвую
общую цель, но исходный173 не содержит аналогичного target trace; это ограничение доказательства.

Protected inventory035/native drain036/core/evaluator не меняются. Исторический raw и все
ошибки capture/helper сохраняются. Runtime starts3/6, production Jar builds0/4, full scenes0/3.
