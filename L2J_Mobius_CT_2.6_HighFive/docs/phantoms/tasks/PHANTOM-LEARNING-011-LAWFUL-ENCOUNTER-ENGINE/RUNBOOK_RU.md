# RUNBOOK_RU011 — что проверять после Codex

1. В отчёте **разделены** количества `discovered`, `headlessReady`, `liveAdmitted`, `cut`, `paid`, `terminal`, `matchedPairs`. Это не взаимозаменяемые показатели.
2. Найти новую строку в `docs/phantoms/COMBAT-AI-ROADMAP.md`:011 №5/13, осталось8.
3. Проверить, что истории 009/010 не изменены, а TEST DRAFT14/Adam903/223 cold readback совпадает. Тренировочных FIT/credits011 не было.
4. Проверить `G3`: существуют live-compatible Solo и Party предложения. Если 0 — GameServer не должен тратить starts.
5. Проверить `G4`: реальные Solo/Party **завершённые пары**, а не только тестовый спавн, сырой урон или один `batch.complete`.
6. В отчёте есть точная таблица отказов по SOURCE/LEVEL/FACTION/GEO/REQUEST/OWNER, включая все старые категории010.
7. TEST в STOPPED/OFF, owned PID0, TCP-порты свободны, чужой сервер не тронут; новая ветка не создана.
8. Код находится в `feature/phantom-world` на GitHub, с обычным push, diff и независимым readback.
9. Пара/пара десятков боёв не доказывают превосходство модели. Game verdict отдельно.
10. Если `PARTIAL`, не просить бесконечно увеличить budget; сначала установить воспроизводимую root cause и изменившийся контракт.
