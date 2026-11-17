# Goal022 — corrective POST-V2: следующая самостоятельная TEST-кампания

**Это продолжение существующего PHANTOM-LEARNING-022, НЕ Goal023.** Текущее состояние: native Goal022 доказал 188→206→216, два sealed эпизода; отдельный owner gate FULL пока не закрыт. Требуется устранить конкретную блокировку нового `Start` после использованного `GOAL022_CONTINUATION_V2` и не потерять курсор/lineage. Производственный V4 остаётся OFF.

## 1. Base / права / лимиты

- Git: `https://github.com/kpCat/L2J`, ветка `feature/phantom-world`; проверенный baseline `0cf5681cf156219a43930bbdeb283e126c2d7ccc` (17.11.2026). Сначала fetch/status/HEAD/parent; если relevant files изменились — аудировать scoped diff, не перетирать новые изменения.
- Локальная репа `D:\Codex\repos\L2J-phantom-world`; модуль `L2J_Mobius_CT_2.6_HighFive`. Работать в безопасном разрешённом checkout на `feature/phantom-world`; dirty MAIN и старые source/test-копии не трогать.
- **GS starts = 0, SQL starts = 0, native rounds = 0, восьмичасовая кампания = 0.** Никакого нового runtime approval. Выполнять только PowerShell/focused offline проверки на отдельном owned TEST fixture.
- Бюджет: до 55 мин инженерной работы; две связанные известные причины (V2 gate и cursor) устранить в одной работе, плюс до двух прямых причинных локальных блокеров с focused RED→GREEN. Если safety нельзя доказать — `BLOCKED`, а не ослабление gate.

## 2. Проверенная root cause

`TrainingV4.ps1::Invoke-TrainActionValue022` при создании новой кампании безусловно реагирует на `train022/continuation022-v2.json`: требует `Minutes=15`, `BootLimit=1` и соответствие `previousCampaign`. Этот файл навсегда остаётся после использованного V2, поэтому новый штатный Start на 480 минут блокируется. Кроме того, новая независимая кампания копирует ledger, но без `cursorStart` сбрасывает курсор skill-shortlist на 0; последний доказанный курсор **4**. См. `CONTEXT.md` и `ARCHITECTURE.md`.

## 3. Сделать в одном corrective

1. Ввести **явный выбор нового самостоятельного TEST-сеанса** (рекомендуется `-ActionValue022NewCampaign`) в существующем `Training.ps1`, прокинуть в `TrainingV4.ps1`. Старые `ActionValue020/021`, `Status/Report/Pause/Resume/Stop` должны остаться совместимыми. Повторный `Start` активной кампании по-прежнему идемпотентен.
2. V2 authority/claim применимы **только к своему однократному V2-переходу**. Проверить, что V2 использовано, история в согласованном STOPPED/AUTO_STOPPED, pinned boot receipts3/3 целые. Обычная новая TEST-кампания не получает V2-исключение и не может «съесть» V2 второй раз. `Start` без явного new-campaign opt-in после завершённой V2 должен отказывать, не запускать GS.
3. Новая кампания наследует **verified committed HEAD и модель 216**, глобальные event/episode keys, сегменты, immutable cold/seal artifacts. Источник нового `cursorStart` — последний подтверждённый committed source/curriculum cursor (на нынешнем fixture 4), не число раундов и не старый V2 cursor2; отсутствующий/подменённый курсор = fail closed.
4. У новой кампании свой `campaignId`, новый deadline (только по новой явной команде), собственный boot-budget и собственный счётчик. Прежние 3 boots остаются в истории V2, нельзя снова запускать внутри V2 или обнулять её счётчик. Новая кампания не означает безлимитный GS-цикл и никогда не стартует в ходе corrective.
5. Предоставить безопасный **prepare-only** режим нового Start (допустимо `-ActionValue022PrepareOnly`) через тот же ingress, что использует реальный Start; он готовит полностью проверенный private TEST fixture, останавливаясь **перед broker/GS/SQL**. Тестировать на отдельной owned копии/fixture, не переключать исходный `active022.json` владельца. Доказать, что параметры нового будущего сеанса (`Minutes=480`, bounded boot limit) доступны, но не запускать его.
6. При переходе обновлять `active022.json` только после успешной проверки физического ledger/cold/cursor и новой конфигурации; при неуспехе прежняя active campaign остаётся доступной. Сохранить прошлый root и его byte hashes; не делать cleanup/удаление старой кампании.
7. Дополнить точечные PowerShell-тесты, короткий русский operator note, отчёт и offline evidence. Никаких production policy/Java combat changes ради этого исправления.

## 4. Приёмка и публикация

`ACCEPTANCE.md` и `TEST_MATRIX.md` обязательны: **проверять именно реальную ветку кода**, а не отдельный mock `if`. Убедиться: parent216 exact `90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6`, global updates216 / keys302 / cursor4, no altered events. Это read-only проверка физических private artifacts Codex, а не гарантия их доступности в GitHub.

Только exact allowlist: `Training.ps1`, `TrainingV4.ps1`, `Test-Training022.ps1`, файлы этого corrective-пакета, `docs/phantoms/reports/PHANTOM-LEARNING-022-CORRECTIVE-POST-V2.md`. Оставлять исходные `RESULT.json`, `CONTINUATION_RESULT.json`, `CONTINUATION_V2.md`, `DELIVERY.json` и immutable TEST истории неизменными. При строго доказанном прямом блокере можно расширить максимум на один TEST-only файл, с обоснованием в отчёте; production Java/SQL/Geo не трогать.

В отчёте указать конкретную причину, RED/GREEN, реальные команды/exit codes, GS0/SQL0, исходные и итоговые SHA, состояние historic V2, свежий checkout status, дифф, elapsed, остаточные риски. `git diff --check`, mojibake/escaped Cyrillic, scoped add/commit, **обычный push** в `feature/phantom-world`, независимый GitHub HEAD/parent/diff/full bodies. Без автоматического 8h Start и без ручного объявления `ACCEPTED_FULL022` — приёмка остаётся за владельцем.
