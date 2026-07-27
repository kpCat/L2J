# DROP ORDER — Goal 011A

Authoritative ordinal means exact runtime list index.

```text
groupOrdinal = NpcTemplate.getDropGroups() index
itemOrdinal  = DropGroupHolder.getDropList() index
```

Ungrouped death and spoil use their exact list indices with groupOrdinal -1.

Snapshot sorting is allowed only after these explicit ordinals are copied.
Changing an ordinal changes the source hash; shuffling outer fact collections
without changing ordinals does not.
