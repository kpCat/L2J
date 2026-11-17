# Следующий отдельный Goal024 — ускорение без потери safety

Сейчас `TrainingV4.ps1` вызывает `Invoke-TrainActionValue020` на каждом раунде; `PhantomLearning020V4Native.nativeOnce()` поднимает и закрывает `GameServer.main()` на один эпизод. Это структурная причина высоких накладных расходов. Исторические TWO successful native Goal022 process times: 89.808 и 89.615 s; сами эпизоды 44.865 и 36.328 s. Разность включает старт, preparation и shutdown, не представляет точный отдельный startup benchmark. Первая отказанная попытка: 43.803 s до CUT, credit0.

Goal024 должен отдельно реализовать **два и более независимых lawful native эпизода в одном GS lifetime**, с самостоятельными episode/owner/generation IDs, fresh actor/target, after-drain receipts, safe rearm, bounded stdout/queue, без двойного learning-credit; после пачки — offline fit/cold и quality guard023 before next batch. Если нативный Server не допускает safe rearm, отказ и измеренный blocker вместо обхода.

Не требовать обновления весов *внутри* каждого GS lifetime: это подразумевает опасную in-process замену модели и нарушит отдельную cold-JVM границу. Мини-батч из нескольких эпизодов с frozen candidate и последующее offline incremental fit — предпочитаемый безопасный вариант. V4 256-sample batch/4096-event cap + global ledger / rollover сохранить. Отдельные согласованные native boots ≤2 на приёмку Goal024; no 8h smoke.

Не начинать Goal024 в этом диалоге Codex или при завершении Goal023 автоматически. Перед Goal024 обязательна проверка owner gate Goal023.
