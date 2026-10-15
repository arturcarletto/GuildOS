# GUILD-020 - Activity chart browser validation

Date: 2026-10-15. Original implementation: [PR #22](https://github.com/arturcarletto/GuildOS/pull/22), branch `feat/guild-020-activity-chart`, commit `cc7eab2`, merged as `94f9b505da18fdce27df6f1595a4221d6d3cf4d9`.

[Frontend CI after merge](https://github.com/arturcarletto/GuildOS/actions/runs/38674837115) and [Backend CI after merge](https://github.com/arturcarletto/GuildOS/actions/runs/38674837090): **PASS**, event `push`, branch `main`, exact merge SHA. The original implementation's browser validation was NOT RUN; the checks below are new evidence and do not retroactively change that historical result.

## Environment and limits

The real React application was served by the README's Vite development server, bound to loopback. Windows PowerShell's npm wrapper dropped the forwarded `--host` argument, so the equivalent Vite CLI was invoked directly. Chromium 141.0.7390.37 and Playwright 1.62.1 were already installed in the execution runtime; no browser/testing dependency was added to GuildOS.

All `/api/v1/**` responses were intercepted in the browser context, including synthetic session/guild selection metadata. This is a **real-browser frontend check with a simulated API**, not a real login, backend, PostgreSQL, Discord or authorization integration test. Fixture names and ids are synthetic; no token, cookie or real member information was captured. UTC time was fixed in the browser to make range assertions reproducible.

## Results

| Scenario | Status | Direct evidence |
| --- | --- | --- |
| Last 24 hours | PASS | Request spans 24 complete UTC hours; 24 bars; SVG count matches fixture total |
| Last 7 days | PASS | 168-hour request, eight UTC calendar dates, two partial edge days |
| Valid custom interval | PASS | 31-day request accepted; 32 UTC dates rendered |
| Invalid interval over 31 days | PASS | Validation alert; no additional analytics request |
| Partial UTC dates | PASS | Edge markers and two exact-value rows identify partial coverage |
| Chart position and indicators/table | PASS | DOM ordering asserted; card, SVG and hourly table reviewed in screenshot |
| Labels and exact counts | PASS | SVG title/description, axes, UTC labels; expanded values sum to fixture response total |
| Keyboard interaction | PASS | Enter opens native details; arrow keys scroll the focused plot |
| 320 px | PASS after fix | Document width remains within viewport; keyboard scroll stays inside plot; screenshot inspected |
| 375 px | PASS after fix | Same width/scroll checks and screenshot inspection |
| 390 px | PASS after fix | Same width/scroll checks and screenshot inspection |
| 430 px | PASS after fix | Same width/scroll checks and screenshot inspection |
| 768 px | PASS after fix | Same width/scroll checks and screenshot inspection |
| Desktop 1365 px | PASS | Default, 7-day and 31-day chart captures inspected |
| Maximum-range horizontal scrolling | PASS | All 32 bars exist; mobile capture shows the end of the range after scrolling |
| Loading | PASS | Loading status visible; chart absent until the held response resolves |
| No activity | PASS | Empty response keeps the existing empty state and omits the chart |
| All counters zero | PASS | Existing all-hours-empty state; finite zero-chart geometry |
| Activity without created messages | PASS | Explicit no-message text; other activity remains in the original table |
| Error and retry | PASS | Controlled 500 state; retry recovers to the chart |
| Rapid period change | PASS | Held old request resolves after current data; current SVG description remains unchanged |
| Rapid guild change | PASS | Navigate through guild list to synthetic guild B; late guild A result cannot replace current output |
| JavaScript exceptions | PASS | Browser `pageerror` collection empty |
| Frontend/backend communication | BLOCKED | No backend listener on 8080; Docker compose cannot reach the Docker engine |
| Real Discord data | BLOCKED | No authorized test Discord session/environment available; none attempted |
| Manual screen-reader audit | NOT RUN | Accessible roles/descriptions and keyboard behavior checked; no dedicated screen-reader session |

The browser runner's final `visual-fixed/summary.json` contains 18 automated PASS entries, 15 PNG paths and no page errors. The table above separates the checks into individual user scenarios and adds the manual screenshot review and unavailable integration checks.

## Mobile defect and scoped correction

The first correctly routed pre-fix browser run passed the 24-hour data and exact-values checks, then **FAIL**ed at 320 px: `document.documentElement.scrollWidth = 1066`, `innerWidth = 320`. The failure capture showed the entire content column expanding to the chart/table width.

The correction adds only `min-width: 0` to the existing `.app-content` grid item in `layout.css`. This lets that item shrink below its intrinsic content width, so the existing chart and table scroll containers handle their own overflow. No `overflow: hidden` or `overflow-x: hidden` rule was added to body/root, no columns were removed, and no data was clipped to fake a passing page-width check.

After the correction, the real-browser checks assert document width no greater than viewport width plus one pixel at all five narrow widths, and prove that ArrowRight changes the plot's scrollLeft. Screenshots were visually inspected for wrapping, hierarchy, controls, labels and retained table content. Horizontal scrolling is intentional for the chart and table.

The corrected render is on `chore/guild-021-dependency-security`; the CSS correction is not in main until its PR is merged. GUILD-020's merge and post-merge CI remain valid, while the discovered mobile defect and its follow-up are preserved here.

## Evidence artifacts and reproducibility

The temporary runner and captures are retained outside the repository in the local evidence directory returned with this delivery. No screenshot or temporary browser script is committed. Relevant artifacts include:

- `visual-after/failure.png`: pre-fix 320 px overflow.
- `visual-fixed/desktop-24h.png`, `desktop-7d.png`, `desktop-31d.png`.
- `visual-fixed/mobile-320.png`, `mobile-375.png`, `mobile-390.png`, `mobile-430.png`, `mobile-768.png`, `mobile-31d.png`.
- `visual-fixed/empty.png`, `zero.png`, `other.png`, `error.png`, `retry.png`, `loading.png`.
- `visual-fixed/summary.json`: complete results and browser version.

The runner drives the actual dashboard URL, mocks only the exact local `/api/**` prefix, selects the Activity tab, checks range parameters and totals, interacts with presets/custom dates/details, resizes the viewport, and releases delayed responses after period/guild navigation. An earlier harness attempt matched `src/api` module requests and timed out; it was corrected and is not counted as product validation.

The retained artifacts are local evidence, not a portable browser test installation. To reproduce, use an already available Playwright/Chromium installation and the retained runner with a loopback Vite server. A future real integration check requires the README's PostgreSQL/backend setup and a separately authorized Discord test session; fake credentials cannot substitute.
