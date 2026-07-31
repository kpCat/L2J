# Goal 019 architecture

```text
immutable text + immutable context
→ normalization
→ indexed lexical/pattern candidates
→ typed slot candidates
→ read-only authority validation
→ deterministic ranking
→ structured understanding result
```

An accepted intent is an interpretation, not permission to act. Goal 020 must
revalidate every action through the authoritative subsystem.

Semantic aliases are language facts. Their targets exist only when current pinned
authority confirms them. The pack cannot create a new item, NPC, content, role,
capability, class or location identity.

Ranking order:

1. total integer score;
2. exact evidence count;
3. required slots completed;
4. fewer fuzzy/ambiguity penalties;
5. lexical intent key and canonical slots.

Near-ties inside the configured margin clarify rather than use lexical order as
truth. All indexes are immutable and built once; parsing never scans all server
entities or aliases.
