# TASK060 — связанная жизнь и развитие

Статус кампании: IN_PROGRESS. BASE=`afa843a9681b9026a38cc62ab9376114626b0a44`. OWN=`D:\Codex\worktrees\phantom-dev\L2J_Mobius`. Начало `2026-10-18T01:59:05Z`, deadline `15:59:05Z`; резерв финала с `14:11Z`, уточняется по измеренной поставке.

Текущий законченный owner: autonomous learning producer в существующем Decision. Корень `farm.background` сохраняется; дочерний `learning.intent` хранит root revision, actor, class/index, цель, поле возврата и terminal/backoff. Existing Navigation/paid Progression исполняют WALK и обучение. Нового AI/timer, выдачи SP или обхода native guards нет.

Policy: `data/phantoms/life/high-five-autonomous-learning-v1.xml`, version1; recheck30s, journey8min, SP reserve0, cache<=128. Неизвестная версия/элемент и выход за hard bounds отвергаются. Party/HOLD, actor/class/root revision и текущая native eligibility повторно проверяются.

P0: собственные058a/058t проверены. Stopped baseline058a сохранён, SHA256=`3C8711D233C5C2002E3A0A498ADA587A8CD3B0B0E56DF54C7C1A57A9AFEB80F1`; совпадает с исходным экспортом059. Нет reset/reimport, orphan698–705 не изменены. Защищённые057–059 не запускались и не изменялись.

Evidence находится в `.phantom-local/contract058a/task060` и `.phantom-local/contract058t/reports/task060`, старые FAIL сохранены. Ownership continuation RED11/12→GREEN12/12. Native owner/revision/HOLD/policy GREEN2/2 (`autonomous-learning-guards-green060-1`). Build jar+265 test sources SUCCESS, две прежние removal warnings. `autonomous-learning-chain-green060-2`: GREEN3/3, Game SHA256=`BDCEF5CFF8CDB7CFE292B5F8AF107FE0A7EDCB771298CE80D70DB7245C7BC9D7`. Четыре stock20035 убийства: native EXP+2420, SP0→100; product-selected PowerStrike3:1→3:2 стоил100 SP; WALK к Trainer30029 и обратно, native save/reload после первого урока, затем damage/MP effect текущего3:2 и итоговый save/reload. Root сохранён, дочерний intent восстановлен, повторной оплаты нет. Тест ещё вручную запускал четыре combat sessions, поэтому полная LIFE-CHAIN/AutoPlay/GK/relocation этим не заявлены.

Причинные FAIL: отсутствовал producer; cached offer не учитывал root revision; новый XML первоначально попал в каталог progression с другим строгим root-контрактом; TEST negative-preparation нарушала baseline earned-SP witness. Исправлены соответствующие владельцы/fixture, guards не ослаблены. Отдельный прежний chain FAIL сравнивал возврат с координатой до боя, а product сохраняет фактическое поле после боя; evidence сохранён.

Следующий основной шаг: общая production farm/learning/return/farm композиция и второй класс, затем supply/GK/offline/Pet/career/group по утверждённому плану. Полные М4–М7 не приняты. М1/G4/G6 остаются отложенными. Новый060 candidate ещё не поставлен; owner QA pending.

Самопроверка: source review и exact scope; mojibake-маркеры в изменённых файлах проверены; escaped Cyrillic в изменённых файлах проверены. Git inspection разрешена AUTHORITY060§7; exact commits/push выполняются по завершённым шагам, без force/веток/reset. Итоговый HEAD/remote и перечень точных git-команд будут закреплены при поставке.
