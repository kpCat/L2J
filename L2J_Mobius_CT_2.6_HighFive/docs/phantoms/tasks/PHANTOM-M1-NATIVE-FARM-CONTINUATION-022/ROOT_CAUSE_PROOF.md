# TASK022 causal proof

Round1: реальные AutoPlay700ms/AutoUse300ms invocations на untouched base при healthy same-epoch checkpoint SEALED. S03 после1600ms: обе точные регистрации сняты. После успешного OPEN `running=false`. Причина: оба pool loops при `PhantomPolicy.acquire()==null` вызывают exact stop, не различая temporary checkpoint и revocation. S04 stale-stop/successor и final revocation на базе PASS. Это подтверждённая общая boundary-loss, но причинность frozen selected110 в retained021 пока UNKNOWN.

S09 RED: native damage/kill A → target B → late reward A даёт cycle0, тогда как reward-before-target даёт1. Sensor проверял цикл лишь в selected(). Исправление оставляет bounded target state до полного набора native facts и следующей выбранной цели. Никаких добавленных rewards/reset.

S08/S10 RED: deadline/cap/time regression дают одинаковый overflow без first reason. Sticky флаг сохраняется; добавляется только точный first reason/time, сроки120s и cap16 неизменны.

TASK021 seq7 age167199 CAST/REGEN: cached reason не доказывает продолжающийся execution. Retained logs не содержат exact selected110 registration stop/first incident. Tutorial onKill18342 exception без owner не доказан как причина selected110 и не патчится.

Composed TEST: первый human-fighter fixture дал реальные melee damage46/kill1/reward1/cycle1, без cast; это INVALID ROOT RED. Mage setup без всех auto-get skills дал CANONICAL_MISMATCH before materialization. Его exact orphan74255 row0/char268435465 создан21:21:10.456, единственный goal.runtime21:21:10.550 совпадает с raw failure; char отсутствовал после stock TEST cleanup. Automatic review сначала запретил deletion по charId; независимый SELECT подтвердил exact provenance, затем existing repository.delete(74255,0) выполнен. Temporary restore code удалён из suite после выполнения. Неизвестные TEST differences не удалялись.

Три initial TEST jcmd dumps: frozen invocation/deadlock не воспроизведён; driver361/361, scopeOPEN. Полный connected root остаётся UNKNOWN до natural client episode.

Round2 S04 RED: после замены session с тем же goal прежняя policy.acquire(player) всё ещё получает ordinary ActionLease. Exact stop уже защищён expected-policy, но acquire проверял только goal. Минимальный fix проверяет точную current session/policy до acquire и повторно после ActionLease, плюс epoch. Обычная REAL ветка без policy не затронута.
