# DIRECTOR_CLOSURE017 — исправление DONE016 без повторного FIT

## Найденный дефект

В `Training.ps1` после корректного cold `checkpoint016.json` с reason `NO_FIT_INSUFFICIENT_VALID_DATA` устанавливается `journal.phase = 'DONE016'`. Затем `Save-TrainCycleState013` (`TrainingOwner.ps1`) валидирует `^[A-Z_]{1,32}$` и отвергает цифры. Результат 016 — `TRAIN013_STATE_VERSION_OR_PHASE`, директор остался в `FIT`; native evidence и frozen model не потеряны.

## Узкое исправление

- Предпочтительно version-aware проверка конечного состояния. Если для разрешённой frozen016 кампании и подтверждённого checkpoint допустим точный `DONE016`, валидатор должен принять **только его**, а не все строки с цифрами и не произвольную неизвестную фазу.
- Для legacy 013/014/015 нельзя расширять доступные состояния.
- Восстановление cold `FIT` при наличии подтверждённого `fit-result.json`, FIT reservation, `checkpoint016.json` и unchanged selected/Adam должно уметь **без нового Java train/SQL credit/GS** завершить уже достигнутый NO_FIT путь один раз.
- Исторический `director` и failure journal016 не переписывать как будто тот запуск был успешным. Если невозможно сделать безопасный append-only closure без изменения истории, оформить отдельный immutable `repair-closure017` с full source refs и финально оставить исторический PARTIAL, не подделывать `DONE`.
- Статус должен быть `COMPLETED_NO_FIT` или эквивалентный явно отрицательный исход, не `MODEL_IMPROVED` и не `READY_FOR_PRODUCTION`.
- Не повторять `fit-reserved016` и SQL transaction, не делать дополнительный optimizer update. Повторная попытка записи closure = идемпотентный readback, не новый кредит.

## RED→GREEN случаи

1. Валидный конкретный `DONE016` для frozen016 + `checkpoint016.NO_FIT` — ранее RED по regex, GREEN после исправления.
2. Похожий `DONE017`, неизвестный `SOME999`, `DONE016` в legacy campaign — отказы.
3. Исходный `FIT` + точная reservation/result/checkpoint + unchanged hashes → clean one-time completion without Java/SQL/GS.
4. Missing/change checkpoint, optimizer/selected, unknown pending, foreign owner/nonce, drifted source/config, nonzero fits — stop, **не** писать DONE.
5. STOP/RESUME after attempted completion, повторный вызов не создаёт новый FIT или новый цикл.
6. Никаких вызовов EVAL, promotion, GAME, live generation.

## Примечание

Само исправление DONE016 необходимо, но оно **не даст достаточности данных**; нельзя требовать FIT для подтверждения исправления дирекции. Контрольный критерий — честное завершение с нулевым FIT и проверенным frozen checkpoint.
