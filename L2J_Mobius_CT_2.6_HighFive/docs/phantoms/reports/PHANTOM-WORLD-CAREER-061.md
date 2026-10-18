# RESULT061 — текущая кампания

**Статус: IN_PROGRESS / CORE_INCOMPLETE.** Обязательные A/B пока не доказаны; М7 не закрыта. Этот единственный RESULT обновляется по завершённым вертикалям.

- Начало: 2026-10-18T17:10:31Z; deadline: 2026-10-19T07:10:31Z. Бюджет не сбрасывается после compaction.
- OWN: `D:\Codex\worktrees\phantom-dev\L2J_Mobius`, только High Five; BASE `18a5c70839a7b1a4f7ca2c57c717820b9c6c26db`; detached HEAD разрешён. Целевая ветка `experiment/m1-candidate007-observe008`.
- Пакет отсутствовал в OWN: шесть утверждённых документов и manifest скопированы из MAIN после совпадения всех SHA256. MAIN не изменялся.
- Прочитаны локальный `Agents.md`, README, master plan, workflow/package/release contracts, пакет061, RESULT060 и адресные owners. Корневые AGENTS и H5 README.md не найдены. SOURCE_MAP061 присутствует. Дополнительные code-map/pattern файлы не требуются для этих адресных изменений.
- Аналоги: exact runtime ownership058/059/060, штатные Start/Status/Stop, существующий пассивный observer и operator readiness APIs; career reuse исследуется через Learning/Supply/QuestInstance.
- Java25/Ant/WinPS5.1/MariaDB сохранены; новые библиотеки/движки не добавлены. Утверждённое исключение по числу файлов: неизменный пакет7 файлов плюс текущие связанные source/report/matrix, без расширения продуктового scope.

## Выполненное и причинные факты

1. Initial OWN HEAD и remote равны BASE; tracked High Five изначально clean. Старые untracked artifacts сохранены и не добавляются.
2. Initial exact058a Status: Game/Login PID0, JAR hashes проверены, своих listeners нет; Java процессов нет. Synthetic OFF; исходные population32/ACTIVE8/materialized16 сохранены.
3. One-use consistent stopped backup058a: native single-transaction, чужих соединений0, записей/импорта0; 783501 байт, SHA256 `3BE64CEAD20386AB526DF4BC14D1724448CF54A9A7667602864F57E8DBA15E1B`.
4. Реальный START.cmd из вложенного WinPS5.1/внешнего CWD дал RED до запуска JVM: Get-FileHash отсутствовал. Read-only probe воспроизвёл наследование PS7 module path и выбор несовместимого Utility7 вместо штатного WinPS Utility3.1. Runtime теперь явно импортирует Utility из собственного PSHOME; настройки ОС не меняются.
5. После исправления настоящий START.cmd поднял ordinary Game32428/Login29484 без test-controller/recorder; свежий AUTONOMOUS observer, actualNetworkSession=false. Настоящий STOP.cmd завершился exit0 с STOCK_STOP_CONFIRMED обоих PID. Это проверка запуска/сохранения, ещё не A.
6. Первый fresh ordinary058a: desired12/eligible5/admitted5, independent worldMaterialized0 и World sample0; catch-up956→958 интервалов, native_context.required:coalesced. Нулевой счётчик не объявлен причиной.
7. Старые060 receipts: round1 270с, PID28276, sample0, desired14/eligible5/admitted5; round2 120с, PID31044, sample0, те же admission counts. В обоих pendingCatchup15, native_context.required. Отсутствующие per-profile locality факты060 не выдумываются.
8. Добавлен bounded read-only срез существующих operator APIs каждые30с: до8 текущих local cohort и один nearest, admission/readiness/committed position. Старые динамические поля очищаются перед новым срезом. Диагностика не создаёт цели, Player или прогресс.
9. Current diagnostic JAR `E53CBC110CE0A83B8F0FC7B1CDC67DD51FFDF4D68DF6B5D72F03A86168A1B093`: ordinary Game32776/Login26212. Первый содержательный срез: local cohort0, nearest profile14 readiness COMPLETE/current, admitted, humanLocality=false; committed(-90954,248118,-3568), viewpoint(-71350,258050,-3104). Это подтверждённое отсутствие READY/calendar-online соседей у текущей точки; пригодный positive input ещё не выбран.

## Проверки и оставшееся

- `ant -Dbuild=<OWN/H5/.phantom-local/contract058a/task061/build> jar compile-tests`: exit0, 30с; два прежних runFinalization deprecation warnings.
- Existing `phantom-localplay-pilot-contract-test`: 15/15, exit0. Запрет DOCTYPE проверен отрицательным контролем; ожидаемый parser diagnostic не скрыт.
- Self-review текущих двух source изменений: ownership/обычные guards сохранены, observer только читает, выдача ограничена9 профилями/30с; stale dynamic keys очищаются. Критических замечаний в этом узком изменении нет. Это self-review без субагентов по прямому решению владельца.
- mojibake-маркеры в изменённых файлах проверены: 11 файлов, 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: 11 файлов, 0 совпадений.
- Interim helper ошибся именем metadata поля local-play.codeSha; ошибка и preimages сохранены, исправлена только exact metadata, без DB writes/reimport. Итоговый release будет из clean committed input.
- Captured tool caller повторно ожидает после обычного startup; native JVM RUNNING подтверждены независимо. Причина этой capture boundary ещё исследуется; полный пользовательский CMD lifecycle пока не объявлен PASS.
- A/W1/W2, production career producer, полный native Q401, restart/conservation, KEEP111 и финальная061 поставка остаются обязательной работой. Другие классы/40/76 пока не проверены.
- Raw/checkpoint: `.phantom-local/contract058a/task061/`; одна матрица: `docs/phantoms/tasks/PHANTOM-WORLD-CAREER-061/ACCEPTANCE061.tsv`.
- Ни diagnostic Attach/jcmd/jstack/jmap, ни force kill, ни protected release/DB/config/client mutations не выполнялись. Штатный Stop использовался только для exact собственной058a.

Git-команды использовались по AUTHORITY061§6 и прямому запросу: address-scoped status/diff, rev-parse и read-only ls-remote. Exact-path commit/normal push фиксируются следующей записью после проверки.
