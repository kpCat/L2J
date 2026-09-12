# Resume9 context

Published required parent: `f01401d79d5f41aac87cd8425b78a2f00abbf417`.

Resume8 is BLOCKED_ENVIRONMENT, not a product failure. It proved:
- matrix 26 PASS + 2 NOT_RUN_BLOCKED;
- production/build/config/data/DB-guard changes 0;
- final-domain, scale/endurance and rollback already PASS;
- isolated candidates, JAR reads, double ant test, Goal014, Goal039 static,
  preflight and DB guard can PASS;
- verify runtime/DB tail progressed to late historical static phase.

Terminal failure:
`phantom-static-verify-016`.

Goal016 verifier uniquely identifies the historical implementation and completion
commits but then reads PACKAGE_MANIFEST/payload hashes from current working tree.
This couples historical integrity to Windows `core.autocrlf`.

Neighbor patterns are already correct:
- Goal017 uses `git show AcceptedCommit:path`;
- Goal018/019 use accepted target commit bytes on descendant HEAD;
- Goal020c1/c2 use accepted/target commit bytes;
- Goal022c1/c2 use historical target commit bytes.

No `.gitattributes` exists in repository, so checkout EOL is environment
dependent. Resume9 should fix the verifier authority, not chase checkout modes.

Historical hashes remain exact raw hashes of accepted Git blobs.
