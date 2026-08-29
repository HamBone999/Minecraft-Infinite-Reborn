# Pinned inputs

The jars themselves are **never committed** — see `../docs/DISTRIBUTION.md`. You supply your
own copies and the build verifies them against the hashes here.

| File | What it pins |
| --- | --- |
| `client-jar.sha1` | the `Infinite.jar` the client patches were generated against |
| `server-jar.sha1` | the upstream server jar the `patches/` apply to |
| `decompiler.lock` | decompiler and exact flags |

A hash mismatch means the patches will misapply. Fail the build there rather than letting
half of them apply.
