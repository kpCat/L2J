# Goal016 verifier contract

Accepted/descendant mode must:
1. find exactly one direct completion child with the existing exact subject;
2. prove completion ancestor of HEAD;
3. set that completion commit as authoritative verification commit;
4. read manifest and payload bytes binary-safely from Git object database;
5. compare exact raw SHA-256 against existing manifest;
6. perform existing strict UTF-8/content/safety checks on those same bytes.

Working-development mode at implementation HEAD keeps working-tree reads.

Do not normalize EOL. Do not allow two hashes. Do not edit historical manifest.

Recommended implementation:
- `Read-CommitBytes(commit, relativePath)` using ProcessStartInfo + redirected
  stdout BaseStream + MemoryStream;
- `Read-VerificationBytes(relativePath)` selecting working tree only in
  working-completion mode, otherwise accepted completion commit;
- `Read-Utf8Strict` and `Get-Sha256` both delegate to that byte reader.

Preserve every existing Goal016 graph/scope/content safety assertion.
