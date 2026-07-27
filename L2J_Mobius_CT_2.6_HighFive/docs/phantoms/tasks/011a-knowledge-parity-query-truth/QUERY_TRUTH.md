# QUERY TRUTH — Goal 011A

Optional filter states are distinct:

```text
null field       -> filter not requested
supplied field   -> exact candidate set, possibly empty
```

Any requested empty set yields an empty page.

Public area results use lightweight summaries without nested spawn points.
Target results include at most 64 area summaries. Exact points are available
through the separately paged spawnFacts API.
