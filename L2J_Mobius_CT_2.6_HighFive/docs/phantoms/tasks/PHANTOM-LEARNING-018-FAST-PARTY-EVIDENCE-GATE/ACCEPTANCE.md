# Приёмка018

| Gate | Требование | Failure / допустимый выход |
|---|---|---|
| A0 | GitHub HEAD, branch/scope, TEST owner/SQL/Geo/protected только нужный короткий preflight | BLOCKED → без native |
| A1 | Отчёт *первого доказанного* native invalidation у class94 либо явный MISSING_EVIDENCE | STOP_DIAGNOSIS допустим |
| A2 | Доказанная причинная ошибка → один законный RED→GREEN фикс без reward semantic change; если неизвестно — код не менять | Не выдавать искусственный FULL |
| A3 | Не более 1 native Party smoke, только при существующем bounded маршруте, exact actor/target/owner/timing | NOT_RUN допустим по GO/NO_GO |
| A4 | При получении positive — хотя бы 1 новый законный Party terminal receipt; сохранённые016 не повторять и не перепечатывать | FULL только при положительном доказательстве |
| A5 | Без новых FIT/EVAL/model/SQL credits; исторические original bytes/183 SQL/model/Adam/pointer12/Geo/protected и чужой dirt сохранены | FAIL при изменениях |
| A6 | Цель25min, предел35min, безопасное завершение owned processes/ports; краткий отчёт до100строк | STOP при исчерпании |
| A7 | При наличии diff — ordinary scoped push, независимый GitHub HEAD/parent/diff/full changed source; manual gate REQUIRED | FULL без подтверждения запрещён |

Честные статусы: `FAST018_CAUSE_PROVEN`, `FAST018_NATIVE_PARTY_REWARD_PROVEN`, `STOP018_NO_CAUSAL_PROOF`, `BLOCKED018_OWNER_DECISION`, `PARTIAL018`. Не обещать trained model, меняются только контроль доказанности и (опционально) узкая TEST диагностика.
