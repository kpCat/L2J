# Independent review — Goal 009

## Verdict

```text
Reviewed commit: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Parent: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Push/remote: exact
Goal 009 architecture direction: ACCEPT
Goal 009 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 009A: REQUIRED
Goal 010: BLOCKED
Goal 011: NOT_STARTED
```

## Принятое направление

Сохраняются inert production-owned navigation service, factual lazy capability,
direct-before-A*, bounded queue/workers/cache/cooldown, generation cancellation,
late-result discard, pure progress tracker и отсутствие владения
`Player`/`Creature`/movement.

## Обязательные findings

- deadline и математически невозможный route budget проверялись после
  capability/direct backend calls;
- computed A* segments и автоматически добавленный exact destination не
  проходили door/fence-aware validation до cache/publication;
- worker claim мог остаться между monitor reservation и dispatcher decision,
  позволяя `beginStop()` обогнать фактический dispatch;
- server shutdown diagnostic показывал только materialization и скрывал
  navigation-only blocker.

Revert не требуется: findings закрываются bounded hardening в Goal 009A без
изменений `GeoEngine`, `PathFinding`, `Creature`, config, schema или future
topology. До независимого принятия Goal 009A Goal 010 остаётся заблокирована,
Goal 011 не начата.
