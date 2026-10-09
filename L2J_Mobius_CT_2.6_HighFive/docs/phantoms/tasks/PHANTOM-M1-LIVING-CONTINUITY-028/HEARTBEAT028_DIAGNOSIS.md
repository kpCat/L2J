# Heartbeat publication028

CANDIDATE_SCENE_E_0726_R2 ended before380s with the native first predicate
SYNTHETIC_HEARTBEAT:ACL_OR_ABSENT. Run3a273e67-c645-4aa1-b517-a07cbb8c5437,
sequence4, no MOVE issued. The sole writer had published at1791506772390ms.
Its next inspection found a correctly private inherited ACL and the expected
identity. That later observation cannot reconstruct the exact failed ACL read.
The partial capture remains FAILED; the denominator is the original8.

Own native regression HEARTBEAT_PUBLISHER_RED exercises the actual PowerShell
writer against the actual Synthetic heartbeat reader. With200 bounded publications
the native reader rejected a transient record as READ_FAILURE:IOException:Private
control file is invalid; the immediate subsequent read was valid. This proves a
publication/read race at the same boundary, without claiming the missing original
Windows exception was recovered. The protected native watchdog itself is unchanged.

The Synthetic PowerShell7 writer now publishes through same-directory rename
replacement. Destination and temporary-file ACL checks remain; no unlink, permissive
read, retry, cached heartbeat, TTL extension or extra writer is introduced.
Native publication already uses ATOMIC_MOVE/REPLACE_EXISTING. The actual local
.NET runtime exposes File.Move(String,String,Boolean), also confirmed by
[Microsoft documentation](https://learn.microsoft.com/en-us/dotnet/api/system.io.file.move).
RealClient and runtimes without that overload retain their previous path.

HEARTBEAT_PUBLISHER_GREEN_R1 passes4/4. HEARTBEAT_COMPAT_GREEN additionally tests
the actual Synthetic selection and proves that deleting the heartbeat still closes
the native session. Full-server acceptance after this change is required afresh.

STOP_E_0726_R2 completed the ordinary owned shutdown; no ownership or overflow
was reset. The second-scene native rewards remain in SQL and latest receipts.
