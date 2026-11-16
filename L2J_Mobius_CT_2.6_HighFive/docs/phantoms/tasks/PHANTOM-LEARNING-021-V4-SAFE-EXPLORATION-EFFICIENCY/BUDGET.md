# Бюджеты, контроль времени, STOP

**Предпочтение владельца:** одна полноценная задача вместо большого числа микрозадач. Никаких тестов по15мин и более, никаких часовых native-evidence campaigns. Уровень High, новый Codex-чат.

- `T+0–15min`: source+V4 179 access и точный plan of change/experimental arbiter. При отсутствии native source или owner/legal route — STOP без GS.
- `T+25–30min`: focused RED→GREEN policy/correct probability/legacy replay. Нет корректной compiled policy или зависимости вне allowlist — STOP, не начинать runtime.
- `T+45min`: либо native proof/experiment end-to-end, либо обозначить первый causal blocker и выйти из runtime без второго boot.
- Целевое полное wall **≤55–60мин**, верхний ограничитель **75мин** включая итог/публикацию. Это контроль затрат, а не гарантия FULL. Не начинать действие, которое нельзя безопасно завершить до cap; cleanup не обрывать небезопасно.
- **1 GS boot maximum**, startup/real action/drain/SQL shutdown максимум **8мин** (и не выше исходного project safety). No second attempt unless owner independently approves.
- Offline каждый тест **≤120сек**, суммарные проверки **≤12мин** включая GS; никаких soak/51/72, `ant test` full, long production smoke, Studio tests.
- Не более2 причинных локальных исправлений после первого LIVE blocker, без повторного boot; третий независимый blocker/новая архитектура → STOP.
- Никаких новых Git worktrees/branch/parallel server owners. Весь runtime изолирован и owned; cleanup + proof immutable; no background promises.
- Если safety/critical action отменила эксперимент, не расширять HP/MP/Geo/MP budget и не выбирать опасные альтернативы ради FULL.

**Фактические limits** указывать в отчёте: stage wall, offline per target, GS elapsed (boot/drain), cumulative checks, resumed/paused safely, number selected/paid/declined experimental actions.
