# Архитектура: отделить V2 claim от жизненного цикла обычных TEST-кампаний

## Где ошибка

Текущий `Invoke-TrainActionValue022` проверяет **наличие глобального** `train022/continuation022-v2.json` вместо того, чтобы проверять соответствие одноразового разрешения **конкретному переходу** из V1 в V2. После успешного V2 файл законно сохраняется, но следующий Start попадает в устаревшие ограничения (15 минут, 1 boot, `previousCampaign`). При этом `cursorStart` для нового независимого Start не задаётся, значит shortlist повторяется с нуля, несмотря на сохранённые модели.

## Договорённый вариант реализации

1. Тип перехода определяет **явный operator intent** плюс проверенное состояние источника, а не только наличие V2-файла. При попытке *первого* V2 continuation (`sourceCampaignId == authority.previousCampaign`) — сохранять старые жёсткие условия/claim и исторический boot=3. При попытке следующего обычного independent session — требовать явного `NewCampaign`, read-only подтвердить неизменённые V2 authority+claim и привязку фактически завершённого V2 к ним; не применять V2 budget к новой кампании.
2. Independent campaign рождается только от **последнего verified committed ledger HEAD** текущей `active022`-кампании. Убедиться, что HEAD/physical candidate/codecs/segments/cold корректны, и что в исходной кампании нет owned процессов и незавершённых reservations с недостоверным credit. Полный ledger переносить create-only с SHA; ни event keys, ни episode split, ни weights не очищать. Нет source HEAD — BLOCKED до запуска.
3. Следующий cursor определяется из последнего **committed** раунда его authenticated `preflight020.json -> candidates[0].nextCursor022` и итогового candidate step. Для актуального V2: `cursorStart=4`. Не выбирать fallback 0, не брать V2 historical cursor2, не считать длину списка файлов эквивалентом cursor. Неизвестное/повреждённое = FAIL_CLOSED. Если код уже имеет безопасный общий helper — использовать его, не клонировать логику.
4. В новую private кампанию переносить immutable HEAD lineage с readback, фиксировать `previousCampaign`, `previousHead`, `previousModelSha`, `cursorStart` и provenance в новом versioned config/receipt. Старый V2 config/claim не изменять. Новый session boot counter начинается с 0 **для нового session**, при этом историческая сумма=3 остаётся доступной в отчётности; новый запуск внутри старой V2 остаётся запрещён. Создание отдельного session не само по себе лицензирует GS.
5. `active022.json` pointer менять только под существующим mutex и после проверки нового root/ledger/config. Не писать новый pointer при отказе source gate; повторный запуск после частичного подготовительного шага должен обнаруживать и безопасно завершать/отказывать, а не создавать два writer и не удалять успешный предшественник.
6. Поддержать безопасное offline `PrepareOnly` **на отдельном частном TEST fixture/доказуемой копии метаданных, без копирования гигабайт Geo/SQL** с точно тем же переходом, что у штатного Start, но прекращением до `Start-Train022Broker`. Обязательны наблюдаемые `GS=0`, `SQL=0`, `ownedProcesses=0`, предыдущая owner active pointer byte-identical. Параметр `Minutes480` в тесте должен означать только frozen future plan, не разрешённый текущий runtime.

## Не менять

`ActionLearningV4`, `ActionContinuationV4` codec, native 020/021/022, принятые модели 188/206/216, scoring, collision/exploration policy, game configs, Geo/SQL, V3 и production. Не переходить к оптимизации GS throughput в этом corrective; это отдельная последующая архитектурная тема.
