# GUILD-021 - Frontend dependency security audit

Audit date: 2026-10-15. Baseline: `origin/main` at `94f9b505da18fdce27df6f1595a4221d6d3cf4d9`.

## Outcome and evidence boundary

The baseline had 15 affected-package entries in npm audit: 6 moderate, 7 high and 2 critical. These counts are package findings, not a count of distinct CVEs. After the scoped remediation, full and production-only audits report zero known vulnerabilities. This is a registry/advisory snapshot, not proof that the application or dependencies are free of all security defects. No exploit against GuildOS was demonstrated.

| Audit | Moderate | High | Critical | Total | Exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Baseline `npm audit --json` | 6 | 7 | 2 | 15 | 1 (findings collected successfully) |
| Baseline `npm audit --omit=dev --json` | 2 | 0 | 0 | 2 | 1 (findings collected successfully) |
| Remediated full audit | 0 | 0 | 0 | 0 | 0 |
| Remediated production-only audit | 0 | 0 | 0 | 0 | 0 |

## Package inventory, paths and exposure

The installed versions below were read from the original lockfile and confirmed with `npm ls --all`. Classification uses the actual dependency path and current application configuration.

| Package | Installed before | Severity | Classification | After |
| --- | --- | --- | --- | --- |
| `@vitest/mocker` | 2.1.9 | moderate | transitive; test/dev | 4.1.11 |
| `baseline-browser-mapping` | 2.10.42 | moderate | transitive; build | 2.11.28 |
| `brace-expansion` | 1.1.15, 5.0.7 | high | transitive; lint | 1.1.21, 5.0.12 |
| `browserslist` | 4.28.5 | high | transitive; build | 4.29.3 |
| `esbuild` | 0.21.5, 0.25.12 | moderate | transitive; test/build | 0.25.12 |
| `js-yaml` | 4.3.0 | high | transitive; lint | 4.3.2 |
| `nanoid` | 3.3.15 | high | transitive; build | 3.3.20 |
| `postcss` | 8.5.16 | high | transitive; build | 8.5.30 |
| `react-router` | 6.30.4 | moderate | transitive; runtime | 7.18.4 |
| `react-router-dom` | 6.30.4 | moderate | direct; runtime | 7.18.4 |
| `source-map-js` | 1.2.1 | high | transitive; build | 1.2.2 |
| `tinypool` | 1.1.1 | critical | transitive; tests | removed |
| `vite` | 5.4.21, 6.4.3 | high | direct + transitive; dev/build/test | 6.4.4 |
| `vite-node` | 2.1.9 | moderate | transitive; tests | removed |
| `vitest` | 2.1.9 | critical | direct; tests | 4.1.11 |

### @vitest/mocker

`vitest > @vitest/mocker`. Standalone redirect-mock plugins can expose files when reachable; GuildOS does not enable these plugins or browser/UI mode. Advisories: [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9).

### baseline-browser-mapping

`@vitejs/plugin-react > @babel/core > @babel/helper-compilation-targets > browserslist > baseline-browser-mapping`. Invalid browser queries may terminate the tool; queries come from project-controlled build configuration, not HTTP input. Advisories: [GHSA-w5vr-8v7q-w6rv](https://github.com/advisories/GHSA-w5vr-8v7q-w6rv).

### brace-expansion

`eslint > minimatch > brace-expansion; typescript-eslint > @typescript-eslint/typescript-estree > minimatch > brace-expansion`. Malicious glob patterns can exhaust CPU, memory or stack during tooling; not shipped in the browser bundle. Advisories: [GHSA-3jxr-9vmj-r5cp](https://github.com/advisories/GHSA-3jxr-9vmj-r5cp), [GHSA-mh99-v99m-4gvg](https://github.com/advisories/GHSA-mh99-v99m-4gvg), [GHSA-rgw5-rvv9-x895](https://github.com/advisories/GHSA-rgw5-rvv9-x895), [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr), [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7), [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p).

### browserslist

`@vitejs/plugin-react > @babel/core > @babel/helper-compilation-targets > browserslist`. Untrusted query/statistics data can cause memory exhaustion or prototype writes; no user-supplied stats/query ingestion was found. Advisories: [GHSA-c83g-rgw3-j3cx](https://github.com/advisories/GHSA-c83g-rgw3-j3cx), [GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g).

### esbuild

`vitest / vite-node > vite@5.4.21 > esbuild@0.21.5`. The affected development-server API permits cross-origin reads. GuildOS does not invoke esbuild serve directly; old nested copies are removed. Advisories: [GHSA-67mh-4wv8-2f99](https://github.com/advisories/GHSA-67mh-4wv8-2f99).

### js-yaml

`eslint > @eslint/eslintrc > js-yaml`. Crafted YAML merge/omap data can exhaust CPU. Current use is lint configuration, not a backend or browser YAML API. Advisories: [GHSA-5p4m-2wfm-xmqj](https://github.com/advisories/GHSA-5p4m-2wfm-xmqj), [GHSA-2883-xcg3-v3hh](https://github.com/advisories/GHSA-2883-xcg3-v3hh).

### nanoid

`vite > postcss > nanoid`. Invalid generator sizes can loop indefinitely; PostCSS controls generator calls and no frontend request reaches this tooling. Advisories: [GHSA-28wg-ghj8-5hjv](https://github.com/advisories/GHSA-28wg-ghj8-5hjv), [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8).

### postcss

`vite > postcss; previously also vitest > vite > postcss`. Crafted CSS source-map directives can disclose local .map files; risk applies to build inputs/tooling, not the served static application. Advisories: [GHSA-fxqj-rqcc-2cmp](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp), [GHSA-r28c-9q8g-f849](https://github.com/advisories/GHSA-r28c-9q8g-f849).

### react-router

`react-router-dom > react-router`. Unexpected external navigation requires attacker-controlled paths. Current links use internal routes and server-authorized guild ids. The SSR constructor issue does not apply to this declarative SPA (no SSR/hydration). Advisories: [GHSA-wrjc-x8rr-h8h6](https://github.com/advisories/GHSA-wrjc-x8rr-h8h6), [GHSA-337j-9hxr-rhxg](https://github.com/advisories/GHSA-337j-9hxr-rhxg).

### react-router-dom

`react-router-dom`. Affected navigation handling is shipped to users. Upgrade removes the reported ranges; no application route or authentication flow is redesigned. Advisories: [GHSA-jjmj-jmhj-qwj2](https://github.com/advisories/GHSA-jjmj-jmhj-qwj2).

### source-map-js

`vite > postcss > source-map-js`. Crafted indexed source maps can block the event loop. This is a build-input exposure, not an analytics API capability. Advisories: [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q).

### tinypool

`vitest > tinypool`. Worker option gadgets require an existing prototype-pollution primitive. No exploit in GuildOS was established; exposure is developer/CI workers, not browser runtime. Advisories: [GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3), [GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr).

### vite

`root vite@6.4.3; affected copies under vitest / vite-node > vite@5.4.21`. Reported file-serving/path/Windows UNC issues affect the old nested Vite copies. Root 6.4.3 was outside those affected ranges; 6.4.4 is a compatible maintenance patch and the single resolved Vite after migration. Advisories: [GHSA-4w7w-66w2-5vf9](https://github.com/advisories/GHSA-4w7w-66w2-5vf9), [GHSA-v6wh-96g9-6wx3](https://github.com/advisories/GHSA-v6wh-96g9-6wx3), [GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff).

### vite-node

`vitest > vite-node > vite@5.4.21`. Inherits the old Vite findings; removed with the old test-runner execution chain. Advisories: Inherited from Vite.

### vitest

`vitest (and its mocker, Vite, vite-node and tinypool chain)`. Critical UI/API file access/execution requires the relevant Vitest server/UI/browser surface. GuildOS uses vitest run with jsdom and does not configure that surface. This lowers current exposure but does not justify retaining the vulnerable version. Advisories: [GHSA-5xrq-8626-4rwp](https://github.com/advisories/GHSA-5xrq-8626-4rwp), [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9).
## Decisions and compatibility

- Upgrade Vitest 2.1.9 to 4.1.11. A 3.x update fixes the UI issue but does not fix the redirect-mocker advisory, whose patched v4 line begins at 4.1.11. [Vitest's v4 migration guide](https://v4.vitest.dev/guide/migration.html) confirms support for Vite 6 and the removal of Tinypool. No pool configuration, tests or assertions were weakened.
- The critical findings include [Vitest UI/API, CVE-2026-47429](https://github.com/advisories/GHSA-5xrq-8626-4rwp), and Tinypool [CVE-2026-104848](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3) / [CVE-2026-104849](https://github.com/advisories/GHSA-85c8-ppgw-ccpr). Mocker [CVE-2026-84373](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) is also fixed. These are tooling exposures, not established remote execution through the GuildOS HTTP API.
- Upgrade React Router 6.30.4 to 7.18.4. Staying on 6.30.6 would not leave the affected ranges of [CVE-2026-53669](https://github.com/advisories/GHSA-wrjc-x8rr-h8h6) and [CVE-2026-53666](https://github.com/advisories/GHSA-337j-9hxr-rhxg). The latter explicitly excludes Declarative Mode. [The v6-to-v7 guide](https://reactrouter.com/7.18.4/upgrading/v6) supports React 18 and Node 20+; the two applicable future flags were already enabled. Remove those obsolete props only from BrowserRouter and four test wrappers. Routes, navigation destinations, session handling, CSRF and authorization are preserved.
- Keep Vite on major 6, resolved at 6.4.4. Remove nested Vite 5 / esbuild 0.21.5 / vite-node instead of overriding incompatible internal packages.
- Update only the seven flagged compatible transitives: baseline-browser-mapping, brace-expansion, browserslist, js-yaml, nanoid, postcss and source-map-js. React 18, TypeScript, ESLint, Testing Library, plugin-react and all other direct package versions retain their installed baseline versions. No force fix, advisory suppression or override was introduced.
- Retain a lockfile with integrity hashes. The graph shrinks from 385 to 332 metadata entries (including optional/platform packages); `npm ci` installs 283 packages on this Windows environment, versus 292 before. These figures measure different things.

## Node and package-manager evidence

Initial environment: Node 22.12.0, npm 10.9.0. `eslint-visitor-keys@5.0.1` declares `^20.19.0 || ^22.13.0 || >=24`, so 22.12.0 was outside its supported range. The project engine now declares `^20.19.0 || ^22.13.0 || >=24.0.0`, matching that observed constraint and the selected toolchain. The minimum versions are metadata-based compatibility requirements, not separately executed version-matrix results.

Local final checks use the already-installed Node 24.19.0 runtime, with npm 10.9.0 invoked directly through `npm-cli.js`. The system installation was not upgraded. The PowerShell npm wrapper uses its adjacent Node executable even when PATH points elsewhere, so invoking it would still run Node 22.12.0.

npm 10 hit an Arborist `edgesOut` exception while resolving the old peer graph, including an isolated package-lock-only attempt. npm 11.14.1, run only from the temporary npx cache with Node 24, resolved a candidate while retaining the baseline lockfile. Only the verified package.json and lockfile were applied; final installation with standard npm 10 `ci` succeeds. No `--force` or `--legacy-peer-deps` was used. The remaining whatwg-encoding deprecation is not an audit finding.

## Verification

| Check | Status | Evidence |
| --- | --- | --- |
| `npm ci` | PASS | Node 24.19.0 / npm 10.9.0; zero audit findings; no engine warning |
| `npm run lint` | PASS | Existing ESLint configuration |
| `npm test` | PASS | 104 tests / 12 files under Vitest 4.1.11 |
| `npm run build` | PASS | TypeScript and Vite 6.4.4; repeated after the CSS correction |
| `git diff --check` | PASS | Scoped changes only |
| Full and production-only audits | PASS | Both exit 0 with empty vulnerability maps |
| Real Chromium with intercepted API | PASS | 18 checks; see [browser report](guild-020-activity-chart-validation.md) |
| Live frontend/backend/Discord integration | BLOCKED | Docker engine and backend unavailable; no authorized Discord test session |
| Backend local tests | NOT RUN | No backend or HTTP/auth contract changes; local Testcontainers requires unavailable Docker |

The mobile correction is independently scoped and committed: `min-width: 0` on `.app-content`. It does not hide body overflow or change chart/data logic. Its before/after evidence is in the browser report.

## Residual risk and review

Zero known advisories does not remove supply-chain risk or replace periodic audits. Developers need a supported Node version. Dependency updates can change router/test-runner behavior, so the existing tests, browser navigation and CI are review gates. Real Discord/backend integration remains unproven in this environment and must not be inferred from intercepted API responses. GUILD-021 remains open until review, merge and the agreed post-merge checks; no automatic merge is authorized.
