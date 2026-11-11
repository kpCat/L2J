# Учебные данные, вознаграждение, перенос знаний и «достаточно»

## Что даёт право учиться
Сохранять настоящий предбоевой snapshot V3, предложенные legal actions (не только выбранный), exact action chosen, source, version, actor/party/session/target identities и timestamps, короткий хвост действий. После действия сохранять реальные outcomes. Attribution строго owner+epoch+generation+skill+target+effect. При рассинхроне, no proof, изменения партии, stale target — mark UNKNOWN, не обучать. Event keys idempotent и уникальны; недопустим повторное зачисление.

`NATIVE_DAMAGE`/`NATIVE_HEAL`/`SYNTHETIC`/`TEACHER` — разные provenance, не смешивать как доказательства настоящей игры. Документировать, что нет counterfactual для неприменённого действия. Обучение по одной исполненной альтернативе не доказывает, что другая была хуже. Рано делать сильные заявления о «+40% DPS» по обычному MSE.

## Reward для Heal
Не награждать за сам факт каста и максимальную сырую сумму HP. Учитывать verified useful HP (не overheal), настоящий расход MP/time/items, угрозу/риск смерти союзника, потери команды в продолжении и штраф за ненужную повторную кастовку. Штраф и bonus за предотвращённую смерть использовать только на честной episode-level оценке: не изобретать несостоявшуюся альтернативную смерть. Точное правило начисления reward описать и протестировать, чтобы модель не делала больнее ради будущего лечения.

Для DAMAGE сохранить старый verified HP и реальные затраты. Для контроля/стан/рес — пока только future label contracts с UNKNOWN outcomes.

## Нельзя потерять знания
Переход 12.5КБ V1 → V3 не значит «перенести байты в новое место». Требуется новый fingerprint и, где уместно, distillation учителя (с provenance=TEACHER), replay из предыдущего dataset с пометкой недостающих V3-признаков UNKNOWN. Веса V1 остаются immutable и доступными rollback. Данные 002 не перезаписывать. Новые эпизоды хранить как rich facts, а не только float[128].

## Что считать реальным прогрессом
Отдельные независимые actor/scene splits. Минимум сравнивать new V3 с: (1) original stock healer priority, (2) константной reward-моделью, (3) текущей принятой моделью, если совместима. Не обучать на AUDIT, не перебирать много seeds с повторным AUDIT. Живые итоги без unsafe actions важнее красивого MSE. `ACCEPTED_TEST` только после audit + legal native behavior; PROD promotion только позднее и отдельным решением владельца.

## Когда достаточно обучать (для 004)
Не фиксировать «10 млн» как обязательный набор. Автоматический отчёт по категориям: level difference, class family, skill family, jewel/weapon/armor group, buffs/debuffs, distance/facing, allies/enemies, terrain and action type. Нужны paired/triple factor coverage + targeted rare scenarios, weighted random, held-out unseen scene. Stop/retarget когда независимая performance стабильно не улучшалась несколько серий (плато) либо уверенность/coverage недостаточна: менять сценарии, не крутить тот же спот бесконечно. В 003 заложить эти метаданные, не реализовывать полный scheduler на миллионы боёв.
