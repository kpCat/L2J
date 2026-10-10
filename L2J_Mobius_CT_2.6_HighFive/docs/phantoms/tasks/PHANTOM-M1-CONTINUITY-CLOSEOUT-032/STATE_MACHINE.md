# Наблюдаемая цепочка без новой persistent-машины

ACTIVE/CURRENT_PLAN → native gameplay
       │
       ├─ resource insufficient → stock REST → stock STAND → текущий gameplay
       │   (тот же прогресс не обнуляется; лимиты остаются)
       │
       ├─ target/route unsuitable → точный failure/goal/epoch
       │   → stop нового исполнения → pre-work recovery
       │   → безопасная локальная альтернатива → atomic plan CAS
       │   → runtime reload / exact handoff → gameplay
       │
       ├─ protocol/foreign/stale → fail closed + точная причина
       │   (нет автоматического разбанивания)
       │
       ├─ death → существующий native recovery027 → новый epoch + lineage → gameplay
       │
       └─ REAL demand gone → existing soft retention → finish earned callbacks
           → owned FINALIZE → World absent → background operation receipts
           → current demand returns → native load/current state/new epoch → gameplay

Измерять выход из каждого перехода, а не только факт входа. «REPLAN/REST есть» не PASS.
Обычный snapshot runtime может отставать; связывать состояние с моментом решения.
Видимый healthy native gameplay не должен зависеть от готовности background FARM.
Но unresolved owned-store/actual identity conflict остаётся обязательным fence.
