# Источники координатора

Все repo paths ниже прочитаны через GitHub на exact `0205d04bc7763fafcbb776e6da8887c1f2912d8b`. Их содержимое — основа
source-фактов ROOT_CAUSES/PATCH_GUIDE. Локальные JVM/TEST здесь не запускались;
proof остаётся задачей Codex. Runtime результаты принадлежат сохранённым evidence021,
не повторному запуску координатора.

- task021/RESULT.md — ограничения native proof,166.4s,19samples,8retained entries.
- task021/EVIDENCE021.json:1–220 и1970–2140 — seq7/age604→167199, REGEN deadline,
  CAST/target0/autoplayfalse, exact object/epoch.
- PhantomVisibleAutoPlay.java:1–260,270–end — native healthy checks, session lifecycle,
  Policy.acquire, configure, recovery budgets.
- AutoPlayTaskManager.java:1–280,370–end — nulllease stop, native target/pickup loop,
  exact expected-policy register/stop.
- AutoUseTaskManager.java:1–240,405–end — own loop and registry.
- PlayerNativeEvidence.java:1–330 — phase expiration, overflow, target/reward ordering.
- PlayerNativeWork.java:1–260,520–end — exact owner/context, callbacks/finally.
- PhantomNativeWorkScope.java:1–320 — first failure, DRAINING, checkpoint/self-drain,
  bounded outstanding/worker snapshot.
- PhantomMaterializedPlayer.java:30–160 — lifecycle/owner types, allowed existing leases.
- Q00255_Tutorial.java:1–160 — kill listener18342; onKill semantics ещё требуют локального
  просмотра до условной правки.
- commons/threads/ThreadPool.java:1–240 — stock pool/wrapper API; READ ONLY.

Exact source link pattern:
https://github.com/kpCat/L2J/blob/0205d04bc7763fafcbb776e6da8887c1f2912d8b/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/...

JDK25 diagnostic syntax:
https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
Thread.print -l показывает стеки и java.util.concurrent locks. JFR profile имеет
больше overhead, чем default; только короткий bounded run, не обещание «нулевойнагрузки».

Нет доказательства, что Q00255 exception — root selected110 stall; нет доказанного
wait-for графа из021; неизвестный root не превращается в утверждение.
