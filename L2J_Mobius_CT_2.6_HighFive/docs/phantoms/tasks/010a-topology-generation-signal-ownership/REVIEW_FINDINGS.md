# REVIEW FINDINGS — Goal 010

- Old-query profile update can commit stale node membership after reload.
- Old-generation event can submit relevance after the new snapshot is active.
- Successful reload clears all profile resolution instead of re-resolving owned
  points.
- Inactive targetability does not withdraw after topology unregister.
- Explicit unregister leaves local-chat, combat and targetability sources until
  TTL and is not ordered against precomputed event delivery.
