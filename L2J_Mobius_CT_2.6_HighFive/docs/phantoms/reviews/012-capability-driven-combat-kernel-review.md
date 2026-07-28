# Независимое ревью Goal 012 — capability-driven combat kernel

## Вердикт

```text
Goal 012 architecture direction: ACCEPT
Goal 012 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 012A: REQUIRED
Goal 013: BLOCKED
Goal 014: NOT_STARTED
```

## Принятый handoff Goal 012

```text
Commit: 8143cb7f89d348854fc469a0955b22405f23e9b6
Parent: 003604b4f7bda2a8d224d0adcf6349c088154e10
Push/remote: exact
Combat core: 47/47 ×3
Ownership: 17/17 ×3
Real integration: 12/12 ×2
Performance: 1/1 ×2
Final verifier: 112/112 ×2, byte-identical
Verifier SHA-256:
9EC6EF14E662BF6BEAF33356F985A99F7AFCF321A3E75548B2974C4ABD22BB1E
```

## Принятое архитектурное направление

Bounded combat session, canonical `Player`/AI actions, actor lease, plan token,
shared worker, explicit target scope, серверный pickup, death/respawn facade и
production inertness сохраняются. Revert Goal 012 не требуется.

## Обязательные findings

Goal 012A обязана закрыть только следующие границы:

- dispatch имеет явный accepted handle/result; `null`, rejection и `Throwable`
  не публикуют ложное владение worker;
- dispatch и `STOPPING` упорядочены одним gate; scheduled-not-started callback
  отменяется, worker claim всегда освобождается top-level `finally`;
- ActionLease не закрывается при ошибке canonical cleanup; ownership остаётся
  bounded retryable до подтверждённой очистки;
- exact action descriptor различает принадлежащие session `ATTACK`, `CAST` и
  `PICK_UP`, не отменяя foreign action;
- loot success требует положительного inventory/object evidence actor;
- selected skill допускает только hostile one-target route без
  PvP/suicide/special, а cast повторно проверяет exact session mode;
- respawn владеет exact plan token, запрещён при active/cleanup session и
  повторно сверяется после actor acquisition и с `STOPPING`.

До независимого принятия exact child commit Goal 012A Goal 013 остаётся
`NOT_STARTED / BLOCKED`; Goal 014 не начата.
