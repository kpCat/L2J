# CLASS CATALOG CONTRACT — Goal 013

Every PlayerClass is represented with both Java enum parent and datapack
skill-tree parent.

Required special cases:

- class IDs 132 and 133 are distinct Male/Female Soul Hound facts;
- Inspector 135 is a special nonterminal Kamael stage;
- Judicator 136 is terminal;
- terminal count is reconstructed, not hardcoded without parity proof;
- current subclass restrictions come from VillageMaster/CategoryData/config.

Capability truth has three levels:

```text
INTRINSIC → class rule has evidence
LEARNED   → actor knows evidence skills
READY_NOW → current equipment/resources/state pass canonical checks
```

Target scope is explicit. Party, clan, servitor and cubic utilities are not
collapsed into one Boolean support flag.
