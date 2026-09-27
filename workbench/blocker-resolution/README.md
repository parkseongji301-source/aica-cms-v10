# 5C-1B diagnostic tools

**Archived investigation, not a V10 operations entry point.** The intentionally unsafe default-compaction branches exist only to reproduce the H2 bug on their allowlisted disposable copies. For 5C-2 use `scripts/cutover/cutover.py` and `docs/PHASE5C2_FINAL_RUNBOOK.md`; never use these probes, the diagnostic H2 patch, or the old 5C-1 classpath for cutover or normal operation.

Read `docs/PHASE5C1B_BLOCKER_RESOLUTION.md` first. These tools use **disposable, allowlisted DB copies only**. Never use them to migrate the original or disable its application guard.

Evidence pointer: `.cache/phase5c1b-current.txt`. Frozen RC classpath: `B/classpath.txt`; it depends on the preserved 5C1 bundle. Sources and compiled tools are archived with the evidence. Compile with the local JDK 17 and `-encoding UTF-8`. Java filesystem access on this Windows/OneDrive machine needs the authorized unsandboxed tool execution context.

- `LifetimeProbe`: path/history/physical-state observations; extra observations can affect reproduction timing.
- `ExactProbe`, `EvidenceProbe`: instrumented investigative variants; not the primary reproduction proof.
- `BareProbe`: reproduces the previous `CutoverAudit` flow without extra observation queries or process delays. Only the allowlist/options and final PID metadata differ. Uses the original audit's fingerprints, V8 preservation checks, validate/repeat checks.
- `Compare-Reconnect.ps1`: 24 fresh copies, default → compaction disabled → diagnostic upstream commit → default again. Refuses to overwrite previous files. Output uses a fixed artifact name: archive an existing run before deliberately starting a new experiment.
- `JdbcOnlyProbe`: negative control; simple SQL updates alone did not reproduce loss.
- `PathProbe`: readonly relative/absolute path and mistaken-extension comparisons.
- `RuntimeObserver`: SELECT-only attach agent for the test JVM, including live hash through H2's existing locked channel. Not an application API, deployment agent, or backup tool. A live hash can change during a background write; authoritative cold hashes are collected after shutdown.
- `Server-Cycles.ps1`: fresh original backup copy, existing V4~V10, 4 normal server lifetimes per location, API draft update and external readonly checks. Fixed test port 8096; exact source allowlist; all DB writers use `AUTO_COMPACT_FILL_RATE=0`.
- `runtime_check.py`: reads page 1/post 33/publication, changes one page 65 draft title, checks persistence/publication isolation. No new CMS model or fixture schema.
- `LockProbe`: another JVM must fail with H2 90020 while the owner runs.
- `Rollback-Check.ps1`: preserves the test V10 file, restores the fresh V3 backup to that **test path**, runs the frozen V3 runtime on 8097, compares all data afterward.
- `Precedence-Check.ps1`: competing nonexistent env/JVM paths vs the exact CLI copy URL; restores shell environment; no wrong file creation.
- `verify_results.py`: file-only assertions across the saved evidence, frozen application hashes, original DB hashes, matrix, restarts, rollback, path and lock tests. No DB connection.

The official H2 2.3.232 source JAR is saved in `B/tools`, from Maven Central. `B/tools/diagnostic-h2-patch` adds only `mvStore.commit()` after compaction, based on upstream PR #4249. It is a causality control, **never packaged or used by the real server JAR**. The server workaround leaves the H2 binary unchanged.

All test servers are stopped at completion. Original V3 stays untouched. Old scripts still use their old options; passing this experiment does not make them automatically safe for cutover. Do not silently rerun a failed DB's missing migrations to conceal the regression.
