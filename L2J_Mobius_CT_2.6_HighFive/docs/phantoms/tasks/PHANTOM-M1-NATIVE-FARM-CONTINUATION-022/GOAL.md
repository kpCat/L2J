# GOAL022

Required base: `0205d04bc7763fafcbb776e6da8887c1f2912d8b`
Branch: `experiment/m1-candidate007-observe008`
Модель: GPT-6.1 Sol. Reasoning: High. Новый Codex-диалог, без субагентов.

Вернуть продолжение штатного видимого фарма, не только запуск AutoPlay:
`production decision → native target/move/cast/attack → kill → native EXP/SP → applicable loot → next target`.

Весь связанный native-continuation slice входит в одну задачу: freshness/потеря выполнения, Phantom-регистрации AutoPlay/AutoUse, точные native work tickets/callback completion, post-kill continuation, достоверность evidence, stop/drain тех же действий. Новый симптом внутри SOURCE_MAP не требует нового ZIP. Каждый semantic fix — только после собственного RED; итоговая проверка одна связанная вертикаль.

Важная поправка к прошлой гипотезе: в evidence021 `decisionSequence=7` не менялся 166 секунд, а возраст `autoplay_running` достиг 167199ms. Это старое reasonKey, не live-утверждение adapter.running(). В native snapshot одновременно casting=true, intention=CAST, target=0, autoPlay=false. Сначала установить, где перестало выполняться продолжение.

Не сбрасывать overflow: baseline REGEN deadline равен since+120 секунд; PlayerNativeEvidence помечает просроченную фазу UNPROVEN. Не считать overflow автоматически переполнением буфера.

Цель runtime: минимум 5 полных непрерывных farm cycles одного natural Player, одного object/epoch, реальные EXP/SP и следующая цель. Native fixture дополнительно проверяет pickup/autoloot и multi-actor isolation. Никаких выдач наград, ручных атак или принудительного выбора цели в connected proof.

Бюджет: ориентир 90 минут активной работы, жёсткий предел 120 минут, включая 15 минут резерва на безопасный cleanup/publication. Ожидание ответов пользователя не считается. До 3 причинно обоснованных semantic repair rounds; максимум 2 connected episodes по 300 секунд. Нового pre-fix ручного входа по умолчанию нет — есть evidence021 и native TEST.

В конце: exact-path commit + normal push при GREEN/BLOCKED/FAILED. M1=OPEN. Не приписывать пользователю команду STOP, если причина остановки — task contract или budget.
