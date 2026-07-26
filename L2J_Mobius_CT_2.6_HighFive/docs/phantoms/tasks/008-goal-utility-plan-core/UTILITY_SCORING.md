# UTILITY SCORING — Goal 008

Requirements gate candidates before scoring.

```text
score = floor(sum(score_i * weight_i) / sum(weight_i))
score_i: 0..1000
weight_i: 1..1000
```

Highest score wins; exact tie uses ASCII candidate key ascending. Max 256
candidates, 16 considerations/candidate and top-eight explanation. No random or
hash/insertion-order tie break.
