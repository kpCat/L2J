# PHANTOM-PLAYTEST-PILOT-001 — результат

Текущий gate: `READY_FOR_CLIENT_BINDING`. Один отдельный настоящий персонаж `TestAdmin` может выполнить вход и `.playtest arm` после выдачи краткоживущей команды. Account, objectId и session ещё не известны и будут штатно закреплены во время arm. Connected GREEN пока отсутствует.

## Реализация и проверка

- Default OFF в source; private file-mailbox с user-only ACL, одноразовый consent, привязка к реальному GameClient и owned GameServer incarnation, typed native actions, последовательный journal, аварийный stop/off и ограниченный batch runner. Fake GameClient, GM и новые QoL-привилегии не добавлены.
- `ant phantom-localplay-pilot-test`: contract 5/5, guarded native TEST 3/3. Targeted regression: chat observation 2/2, party server 10/10, humanized behavior 6/6, conversation chat 5/5. Тестовая БД разрешена штатным gate; это не доказательство подключённого клиента.
- PowerShell parser, два segment XML, fresh/repeated user-only ACL и atomic record проверены. Clean detached `ant jar` из `e918f755167cd2a63c45f4938b7f291d60c13d59` успешен; JAR SHA-256 `CC7EC666A0662B7916B900B8EC1122932798956D2DE29FCD0484EE71DA77FC4B`. Exact-file deployment с private backup и проверкой hash выполнен. Текущий LoginServer PID `22512`, GameServer PID `7776`; pilot enabled, новый `error0.log` пуст.
- Effective 10k/runtime значения сохранены: Population 10000, Active 64, MaterializedCap 128, PulseMs 100, `FRESH_LOCAL_PROVISIONED`. `Build-LocalPlay.ps1`, full verify, прямой SQL и изменение PLAY/бюджетов не применялись.
- Диагностика AFK/flood: `NO_SERVER_IDLE_POLICY_FOUND`, `IDLE_CAUSE_UNCONFIRMED`. Для `/loc` и `.phantomstatus` не найден punitive mute/flood route; общий flood protector не менялся. Узкие найденные дефекты касались Java 8 script syntax, проверки точности Windows start time, кодировки PS 5.1 и private ACL; они исправлены и повторно проверены.
- Изменённые файлы проверены отдельно на mojibake-маркеры и escaped Cyrillic; найденных совпадений нет. Exact scope и whitespace проверены. Чужие файлы оставлены без изменений.

## Граница доказательства

`RESULTS.tsv` пока имеет только заголовок. `CONNECTED_SERVER` и `CLIENT_OBSERVED` появятся лишь из двух фактически выполненных сегментов на одном привязанном клиенте. Пилот не доказывает автономность чата, party, combat или progression фантомов и не подменяет их pilot overrides. При отсутствии клиента итог остаётся `READY_FOR_CLIENT_BINDING`.

## Git

Из явно разрешённого task bounded inspection использовались `git rev-parse HEAD`, `git branch --show-current`, `git status --short`, `git diff --name-only`, `git diff --check`, `git diff --cached --name-only`, `git diff --cached --check`, `git show` для exact-path/HEAD/scope. По разрешённому exact-path commit/push создана серия кодовых коммитов `ddc7c5a`, `a215460`, `5cf60c8`, `cc11a12`, `e918f75`, `2bc9d71` и выполнен обычный `git push origin feature/phantom-world`. Точные пути staging сверялись с allowlist; broad restore/reset/rebase не выполнялись. Финальный evidence commit будет отмечен после записи.
