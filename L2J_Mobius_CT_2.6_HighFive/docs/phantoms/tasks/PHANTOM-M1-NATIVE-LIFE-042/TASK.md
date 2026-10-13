# TASK042 — native continuity: естественные группы и продуктивный уход в фон

## 1. Идентификатор
`PHANTOM-M1-NATIVE-LIFE-042`, только L2J Mobius High Five, новая узкая задача поверх опубликованного результата TASK041.

## 2. Цель
Закрыть по текущему действительному игровому кандидату G2 (две независимые естественные группы) и G4 (полная продуктивная native→background→native жизнь того же профиля), не меняя исходных контрактов М1. Не объявлять этим полное закрытие M1.

## 3. Зависимости
Опубликованный `a5674811479d087ec7f67ca62921ffa85d298263`; текущий 041 `CURRENT_STATE041.md`, исходные 039–041 результаты, byte-exact evaluator028, существующие native/ownership семантики035–041. READ-FIRST: `Agents.md`, workflow standard, 041 acceptance/scenarios. Проверить доступность входного пакета в MAIN и перенести task docs в OWN как отдельные tracked paths, не изменяя MAIN.

## 4. Контекст
C24 natural group8/8 PASS, C8 группа FAIL из-за276. G4 productive soft-away ещё не доказан. G3 C8 конкретно PASS, но весь G3 пока открыт. 041x73/72 не являются задачей042 и сохраняются.

## 5. Обязательный предварительный аудит
Сначала read-only HEAD/remote/status, список чужих staged/untracked, идентичность собственных042 процессов/DB и защищённого040a; просмотреть C8/C24 actual outcomes, code/JAR/config/evaluator, source owners and currently supported scripts. При base mismatch или наличии чужой own runtime — безопасно зафиксировать, без reset/force/overwrite.

## 6. Архитектура
Один native Player/AI/AutoPlay, существующие Phantom planners/controllers, GeoEngine и authority/store CAS. Soft release по законным locality/pin правилам; earned work законно drain/commit, затем штатный background producer и reentry. Нет второго scheduler или ещё одного арбитра.

## 7. Scope
Существующие owners в `SOURCE_MAP.tsv`; ограниченные source corrections по причинно доказанным дефектам, краткие focused tests и обычные игровые сценарии на новых own042a/042b. Разрешён test-only collector/runner, но без новых протоколов и разрастания evidence.

## 8. Out of scope
Crash72/73, control of emergency termination, сетевой перехват/патч клиентского процессора/порты040a, Zariche, G5 cross-boot кампания, M2–M7, переписывание navigation/AI и экономика/карьера.

## 9. Изменения
Установить истинный статус ухода276; при дефекте — RED/negative/source fix/GREEN/native re-test. Затем prospectively fixed cohort и полная S_LIFE. Не заменять failed actor и не выбирать новую группу только потому что старая не прошла. Если root cause не установлен — UNKNOWN, а не workaround.

## 10. Конфиги
Рейты, population, materialized/active caps, геометрия, observer radius, временные границы 45–90–120, Synthetic525s и исходные правила остаются прежними. Новые собственные exact042 schema/ports записать в manifest, до запуска проверить отсутствие конфликтов; original040a/041final конфиги read-only.

## 11. Производительность
Не поднимать ring/disk/record caps; по возможности закрытые существующие compressed batches. Не делать SQL, FileChannel.force, ожидание/анализ телеметрии внутри gameplay callbacks. Ранний no-target recovery сохраняет debt90 и ранее принятые отрицательные cases.

## 12. Конкурентность/lifecycle
Single-writer, profile/epoch ownership, native earned completion, soft vs hard pin, no two simultaneous world/background owners, no new copy of profile. Стоковый graceful stop только на точно собственных042 JVM. Никаких остановок protected040a/041/общей DB службы.

## 13. БД
Исключительно новые042 схематические копии из immutable verified source, не live-040a и не 041 recovery. Не выполнять SQL-heal/reseed/reset после начала эпизода, не переименовывать старые tables/profiles. Для stopped-cut — consistent read-only SQL после отсутствия writers, actual after-commit lineage/full items/skills/PNC.

## 14. Проверки
`ACCEPTANCE.md` P0–P6: независимые natural6–8, исходный evaluator, actual productive LIFE, текущий93/93, lifecycle11/11, негативные tests затронутой функции и full native cut своих normal episodes. Исторические C8/C24 и текущие новые findings показывать отдельно.

## 15. Команды
Не изобретать несуществующие Ant targets: определить реальные из `build.xml` и прежних verify scripts в OWN. Выполнить актуальную jar-first matrix и focused suites, перечислить точные команды/exit и SHA финального JAR. Изменённая игровая семантика инвалидирует соответствующие старые PASS и требует нового current-JAR positive.

## 16. Приёмка
Только `ACCEPTANCE.md`. TASK042 PASS означает G2/G4 PASS на текущем candidate, а не M1=CLOSED. Если отдельный gate не PASS — статус частичный с точной причиной, без переноса других PASS.

## 17. Отчёт
Минимум `CHECKPOINT042.md`, `GATES042.tsv`, `RESULT042.md`, `REVIEW042.md`, компактный проверенный `TASK042-review.zip`. В отчёте различить repair of game vs repair of observer/collector vs legitimate domain failure.

## 18. Commit/push
Только точные OWN paths по review allowlist; ordinary commit/normal non-force push на `experiment/m1-candidate007-observe008`; после публикации local=remote, tracked clean. Зарегистрировать изменения UTF-8/mojibake/escaped Cyrillic, не коммитить секреты, приватные БД/raw/JAR.

## 19. При блокировке
Не обходить platform safety и не запускать альтернативный процесс для заблокированного действия. Сохранить результаты, штатно завершить собственное, опубликовать полезный безопасный результат и статус BLOCKED с точной причиной; М1 остаётся OPEN. Не запускать следующий task автоматически.
