# TASK059 — следующая длительная feature-кампания

## 1. Идентификатор
`PHANTOM-M3-M7-CAMPAIGN-059`. Один новый Local-диалог в существующем OWN наD. Без субагентов. Это продолжение разработки после058, не продолжение закрытия М1.

## 2. Цель
Довести М3 и immediate-SP learning до законченного результата; затем завершать М4, М5, М6 и оставшуюся М7, сколько позволит14ч. Не обещать все milestones заранее. Приоритет завершённых вертикалей над количеством затронутых подсистем.

## 3. Зависимости
BASE из GOAL; принятое М2 из RESULT058; исходные критерии ACCEPTANCE058/DESIGN_PLAN058 и MILESTONES038. Этот пакет задаёт порядок и конкретные открытые сценарии, не отменяет невыполненные требования предшественника. JDK25, Ant, Windows PowerShell5.1, отдельные058 schemas, штатные datapack/геоданные.

## 4. Контекст
058 передал М2 SERVER_VERIFIED и PARTIAL М3–М7; selected final101/101 не означает полный М3–М7. Известны unsupported «стой здесь», конфликт несохранённого SP, незавершённая победа Kamaloka, смерть TEST human в Rift, только tactics-проверка raid. Не переносить эти исходы в BUG без causal review. М1/G4/G6 — отложенные риски, не очередной gate059.

## 5. Read-first
Пакет+локальные инструкции+RESULT058; затем ≤12 initial product/test файлов по SOURCE_MAP, адресно по symbols. Большие файлы читать диапазонами, не многократно целиком. На подготовку ориентир≤30мин; если нет результата — диагностировать первый конкретный input, не проводить массовый аудит всего репозитория. Дополнительные exact files разрешены по реальной цепочке вызова.

## 6. Архитектура
Semantic intent → command authorization/current party owner → existing navigation/combat action lease → native effect → durable state/result → truthful reply. Existing party route/tactics остаются едиными; не вводить второй companion engine. Memory и предпочтения не меняют gameplay legality. Learning использует согласованный durable boundary, не простой bypass сравнения SP.

## 7. Scope
Необходимые High Five production/tests/config/data/SQL для открытых М3–М7, устранения непосредственных зависимостей и поставки. Новые небольшие typed classes допустимы при отсутствии соответствующей ответственности; нельзя копировать целые subsystems под новыми task-именами. Central Player/Party/store менять только при установленном разрыве с ordinary-human controls.

## 8. Out of scope
М1-кампания, G6, видео, ожидание владельца, миграция, чистка старых данных, scale10000, runtime LLM/API, клиентские изменения, Zariche, универсальный solver всех квестов, М8–М10. Существующие экономика/offline/QoL не теряются; пожелания о кристаллах/шмотках/красках/AA/кусках и200–1000 торговцах в разных городах остаются backlog, не новый блокер059.

## 9. Порядок реализации
DESIGN_PLAN P0→P1/P2→P3→P4→P5→P6→P7. P1 М3 и P2 SP оба приоритетны. Разрешается поменять их местами при конкретной зависимости. Поздний независимый кусок допустим при конкретном blocker раннего, но не заявлять зависимый milestone готовым поверх непроверенного общего owner.

## 10. Конфиги
Документированные immutable bounds команд/hold/follow/chatter. Не заменять старые XML руками без version/parser compatibility. Конфликт client port059 заранее разрешён AUTHORITY; старые057/058 не менять. Новая feature в production не должна зависеть от058/059 profile IDs, тестового имени, каталога `.phantom-local` или приватного overlay.

## 11. Производительность
Shared bounded pulse; no thread/SQL poll per bot, no catalog rebuild/world scan per tick. Инициатива речи и повтор команды ограничены существующими cooldown/budgets. Снимать минимальные факты результата, а не миллионы мелких файлов. Если требуется прежний strict recorder, сохранять его512MiB и предусмотреть shutdown tail до переполнения; тримминг обязательных событий не PASS.

## 12. Конкурентность/lifecycle
HOLD/RESUME, party takeover/leave и learning привязаны к точным identity/classIndex/epoch/group generation и текущему owner. Не отменять чужой cast/move и не терять earned descendants. Команда устаревшего лидера не оживает после reconnect. `synchronized(player)` достаточен только если доказано, что участвующие writers соблюдают тот же протокол; проверить gain, save/autosave, learning и subclass switch.

## 13. БД
058a продолжает накапливать earned state. Новые поля/схема — versioned additive migration с backup, old-payload decode и repeat-safe upgrade. Единожды экспортировать окончательный stopped058a в новую release059a. Без копирования защищённой release058a в качестве постоянно сбрасываемого fixture. Отсутствие reset не мешает создавать отдельный объявленный TEST actor для следующего эпизода.

## 14. Тесты
Causal RED обязателен для исправления; работающий компонент не ломать ради ритуала RED. Каждый новый action проверять до реального native эффекта, не только ISSUED/cast-start/plan text. Pure тест разрешает/проверяет логику, native composition — wiring, ordinary package smoke — поставку, пользовательское QA остаётся отдельно.

## 15. Команды и cadence
Реальные modes/seeds брать из `PhantomTestLauncher`, source fixtures и сохранённого `final-selected058b.tsv`, не угадывать. WinPS5.1 и известный JDK25 из рабочей среды. Ordinary актуальный GameServer.jar первым в classpath, test classes не перекрывают product. После semantic change обновлять собственный test JAR перед native проверкой; финальный чистый committed build один на стабильного кандидата.

Во время разработки — failed target и affected suites, не полный101 после каждой строки. В конце один current selected baseline101 + новые/затронутые проверки по фактической dependency map; повтор всего набора только если менялся общий owner после его прогона. Не удалять failed requirement из набора ради зелёной цифры.

## 16. Приёмка
ACCEPTANCE содержит конечные сценарии и честные статусы. CASE_PASS не равно MILESTONE_SERVER_VERIFIED. Новый M3 complete требует покрытия всего оставшегося S3, не только HOLD. Новый M7 complete требует реально объявленного покрытия карьеры, не одного уровня/навыка. Незавершённый этап не блокирует безопасную поставку проверенных частей.

## 17. Отчётность
Приватные `.phantom-local/contract058a/task059/CHECKPOINT059.md`, `ACCEPTANCE059.tsv`, собственные logs/results059. Сохранять startUTC/deadline/remaining, текущий semantic SHA/JAR/lane/PID, last effect, первый blocker, следующую команду; обновлять каждые30–45мин и перед compaction/долгим run. FINAL `docs/phantoms/reports/PHANTOM-M3-M7-CAMPAIGN-059.md` — один читаемый отчёт≤180 строк плюс ссылки на конкретные raw. Не скрывать проваленные native scenes за selected PASS.

## 18. Commit/push и поставка
После законченного изменения с focused GREEN/self-review — exact commit и normal push, затем продолжать. Все product/test helpers, нужные для повторения, tracked; секреты/raw/JAR не вGit. UTF-8; mojibake и escaped Cyrillic проверять отдельно. Обновить текущую milestone-матрицу и компактную retention map, не размножать отчёты после каждого fix. Новый059 package не перезаписывает057/058.

## 19. Watchdog и остановка
Каждые3–5мин процесса: event/cycle/XYZ/HP/MP/EXP/SP/goal/nextDue/owner/JAR, ресурсы JVM/БД/recorder, фактический прогресс. Два пустых замера — диагностика с учётом законного ожидания, не автоматический kill или объявление deadlock. Через10–15мин без новых фактов — другая гипотеза; около45мин без сужения — конкретный blocker и независимая работа.

На первом длинном encounter вычислить грубый ETA по полезному DPS/regen, ресурсам и script lifetime. Если его не хватит, диагностировать до бессмысленного многочасового ожидания. Не уменьшать HP/рейты/таймеры, не пересоздавать повторно ту же сцену без отличающегося проверенного входа.

Раньше14ч можно завершить, если всё разрешённое выполнено, достигнут резерв/внешний лимит либо действительно нет безопасного полезного шага. Один патч, один FAIL, QA_PENDING или прежний G4/G6 не достаточны. Перед финалом не начинать новый большой subsystem; завершить начатое, протестировать и доставить пригодный кандидат.
