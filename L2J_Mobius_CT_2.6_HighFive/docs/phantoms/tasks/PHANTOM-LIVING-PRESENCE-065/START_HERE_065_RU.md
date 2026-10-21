# TASK065 — собственная остановленная поставка

Статус CORE065: PARTIAL. Native materialization/calendar gates пройдены; ordinary earned gate ограничен strict paid-shot policy при отсутствии shots/adena. Ресурсы не выдавались, политика не обходилась. Подробности — RESULT065 и ACCEPTANCE065.tsv.

Candidate: `D:\PhantomWorld\Living-Presence-065-candidate`. Команды из этой папки: `STATUS.cmd`, `START.cmd`, `STOP.cmd`. Остановка штатная; при отказе ownership guard сначала сохраните diagnostics. Итоговое состояние и SHA перечислены в `BUILD_INFO065.json`, `SOURCE_BUILD_INFO065.json` и `DELIVERY_SHA256_065.tsv`.

Исходный code commit: `410fc7a6b6bcab9882d32fdd5a807ab7f42095ec`. GameServer SHA256: `5BF7B416BD2E8234570405E010B2DF5662FCB10A505F614C740D874AB6833142`. Финальный documentation HEAD указан в отчёте публикации; JAR привязан именно к code commit.

Своя база: `l2jmobiush5_phantom_release_065a`, MariaDB `127.0.0.1:3308`. Собственные порты: login2145, game7845, registration/control9145. Population24, ACTIVE target8, hard cap16; FRESH age0 для новых назначений. Synthetic OFF. Данные входа обычного аккаунта `task065player`, персонаж `Task065View`, находятся только в `private/OWNER_LOGIN065.txt`. Уровень1, access0; никаких GM-прав.

Автономный observer использует сохранённую исходную точку обычного персонажа. Это пассивный виртуальный viewpoint, без сетевой сессии и без создания Player наблюдателя. Он не доказывает клиентскую видимость. Явный passive selection профиля20 добавлен перед вторым START только для diagnostics; игровые inputs не менялись.

В `EVIDENCE065` находятся выбранные receipts и отчёт. Все203 GeoData-региона — физические проверенные копии. Старые057–064 релизы, их базы и исторические отчёты не переоценивались.

## Два коротких будущих owner QA сценария

1. Запустить только этот candidate, войти обычным аккаунтом, пройти по исходной области и рядом с её границей. Проверить реальное появление соседей и переход observer в REAL с текущим PID/incarnation. SLEEPING профиль и занятый защищённый actor не обязаны освобождать слот. Записать время, profile/object IDs и actual World/client результат.
2. Выйти обычным logout, дать жизни продолжиться несколько минут, выполнить `STOP.cmd`, затем `START.cmd` на той же базе. Проверить сохранение identities, заработанного EXP/предметов и новое полезное действие. Завершить `STOP.cmd`; проверить свободные порты и отсутствие своих JVM.

`OWNER_CLIENT_QA_DEFERRED`: владелец ещё не выполнял эти сценарии. Никакие результаты native fixtures или пассивного observer не заменяют этот статус.
