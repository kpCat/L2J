# Context — Goal 013B

## Git lineage

- last independently accepted baseline before Goal 013:
  `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- Goal 013:
  `ca50ea28f233e41343035977c55c98129e5d113a`
- Goal 013A:
  `06929a2973ca2450688d413b4d58de034194053f`
- branch:
  `feature/phantom-world`

## Accepted technical direction from Goal 013A

The following are not reopened:

- data-driven class/capability variants;
- exact action skill per variant;
- `INTRINSIC / LEARNED / READY_NOW`;
- skill item/charge facts;
- no tactical policy in catalog;
- typed servitor/pet/cubic facts;
- cubic absent body;
- full owned-equipment paging;
- active/main/subclass truth;
- CP as an independent Player runtime resource;
- no per-phantom thread/task/Future.

## Remaining defect

Current progression sequence performs:

```text
canonical item mutation
→ runtime SP mutation
→ Player.addSkill(skill, true)
→ in-memory reconciliation
→ SUCCESS
```

But current server internals make durability best effort:

- `Player.storeSkill` catches DB errors;
- `Player.storeCharBase/storeCharSub` catch DB errors;
- `Item.updateDatabase` catches DB errors.

Therefore return values and in-memory postconditions cannot prove durable
conservation.

## Why Goal 014 remains blocked

Goal 014 will add supplies, buying and selling. It must not be built while an
existing progression operation can consume an item/SP without a durable skill.

Goal 013B closes only this transaction boundary. It does not add commerce.
