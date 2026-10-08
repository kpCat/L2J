# Явное изменение приёмки: CONTINUITY_V2, не переписывание TASK026/027

Причина: прежний фиксированный набор из8 обязан был весь период оставаться в одном
native epoch и каждому получать награды в tail120. Это strict stationary farm test,
но не достаточная и не всегда уместная проверка живого мира со смертью/фоном.
В TASK028 вводится ДО новых запусков дополнительная state-aware continuity оценка.
Пользователь принимает её отправкой GOAL. Старые критерии и старые FAIL не меняются.

1. `LEGACY_STRICT_FARM` старым evaluator считать отдельно при восьми входных actors.
   Если их6/7, legacy eight gate NOT_APPLICABLE_COUNT, НЕ8/8. Файлы evaluator026/027
   read-only; never change threshold to green. Все его проигрыши остаются в RESULT.
2. Новый baseline после фиксированного60с enrollment включает ВСЕХ естественно
   видимых materialized actors до лимита8, минимум4. Никакой сортировки по удачным
   counters. Если видимых меньше8, фиксировать whole-world materializations и
   отсутствие/причину остальных capacity slots. Не объявлять6/7 автоматически поломкой,
   но ignored eligible/demanded actors и unexplained absence остаются дефектом.
3. До исходов выбираются два подходящих alive primaries: profileId по возрастанию,
   разные пары сцен по возможности. Нужны>=5 полных cycles/kill/reward с EXP/SP у
   каждого в ОДНОМ доказанном epoch-сегменте; не суммировать до/после смерти.
4. Для остальных baseline actors —>=2 cycles у каждого либо законченный lifecycle
   с>=2 cycles нового epoch после возврата. REST/REPLAN сами по себе не заменяют cycles.
5. У здоровых farmers max interval без useful progress<=90с и native rewards в
   последние120с. Законный REST<=45с входит в этот debt; слова «отдыхал» не исключают
   время. Для смерти/absence отдельный bounded lifecycle, не натягивать farm PASS.
6. Epoch transition допускается ТОЛЬКО с complete lineage: death/retention→SEALED
   receipt→absence→exact new owner. Это `RECOVERED_CONTINUITY`, не same-epoch farm.
   Missing snapshot/identity/gap — UNPROVEN/FAIL, нельзя восполнить соседними samples.
7. Все исходные IDs026 и две группы027 сохраняются в отдельном longitudinal отчёте:
   какие native/canonical состояния/причины отсутствия; не требовать их телепорта
   к observer и не включать невидимого ранее персонажа в фарм denominator молча.
8. Baseline pin до rewards. После baseline никого не заменять, даже dead/idle.
   Отдельный увеличенный отчёт всех materialized actors не подменяет local cohort.

Overall `CONTINUITY_V2_PASS` требует все baseline logical actors accounted и все их
обязательства закрыты. Это не «разрешим пяти ботам быть idle, раз три фармят».
Полная длина каждой сцены360–420с, max telemetry gap5с, стабильный frozenSHA.
Ни7/8 на322с, ни новое4/4 на коротком куске не являются завершённой сценой.
