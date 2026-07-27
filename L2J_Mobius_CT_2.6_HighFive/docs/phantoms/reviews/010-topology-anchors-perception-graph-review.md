# Независимое review Goal 010

## Verdict

```text
Goal 010 architecture direction: ACCEPT
Goal 010 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010A: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

## Проверенный baseline

```text
Commit: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Parent: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Branch: feature/phantom-world
Push/remote: exact
Production topology SHA-256:
f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
```

Архитектурное направление, versioned topology, factual corpus, immutable
indexes, explicit profile registry и bounded one-hop perception приняты.
Revert не требуется.

## Обязательные findings

1. Profile update мог разрешить point по старой generation и закоммитить
   membership после topology swap.
2. Perception event не владел exact generation до scheduler delivery, поэтому
   old-generation recipient мог получить сигнал после reload.
3. Successful reload не пересобирал memberships всех сохранённых points и не
   invalidated provider-owned sources до swap.
4. Topology unregister не гарантировал финальные withdrawals
   `topology.local_chat`, `topology.combat` и `topology.targetability`.
5. Inactive targetability после unregister могла не выполнить withdraw.
6. Cleanup failure не имел явного retryable состояния, а source sequence
   требовала защиты от overflow.

## Требуемое закрытие

Goal 010A ограничена generation/signal ownership и focused regressions. Она не
меняет topology XML/loaders, navigation, decision, scheduler semantics,
config/schema и не начинает Goal 011/012.

Goal 010A не может принять себя самостоятельно. После реализации требуется
отдельное независимое review; до его verdict Goal 011 остаётся `BLOCKED`.
