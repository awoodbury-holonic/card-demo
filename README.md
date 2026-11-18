# CardDemo generated application

This is the current assembled target of the AWS CardDemo
conversion: one Spring Boot backend and one React browser application. This
repository holds the contents of hss2's generated `output/` directory at its
root. This document describes the output that is actually present and how to
validate it. All commands below run from the repository root unless stated.

## Prerequisites

- Java 21
- Maven 3.8.7 or newer
- Node.js 20.19 or newer, or Node.js 22.12 or newer, with npm

## Run the backend

From the repository root, enable the source-data adapters needed by the interactive
application:

```bash
mvn -Dspring-boot.run.arguments="--carddemo.import.customerdata.enabled=true --carddemo.import.accountdata.enabled=true --carddemo.import.carddata.enabled=true --carddemo.import.cardxref.enabled=true --carddemo.import.trantype.enabled=true --carddemo.import.tcatbal.enabled=true --carddemo.import.discgrp.enabled=true --carddemo.batch.posttran.enabled=true --carddemo.batch.intcalc.enabled=true --carddemo.batch.combtran.enabled=true --carddemo.import.trancatg.enabled=true --carddemo.import.pautdb.enabled=true" spring-boot:run
```

The backend listens on port 8080 (`http://127.0.0.1:8080`); add
`--server.port=<port>` if another instance already holds it. It uses the in-memory database
`jdbc:h2:mem:carddemo`, and Flyway applies the generated migrations on every
start. The database is discarded when the JVM exits.

The USRSEC importer is enabled by the generated configuration. Of the other
flags above, five enable the customer, account, card and card-cross-reference
imports and the seven transaction types (TRANTYPE) shown by
`/admin/transaction-types`. `carddemo.import.tcatbal.enabled` loads the
transaction category balances, and `carddemo.batch.posttran.enabled` runs the
daily transaction posting (POSTTRAN, CBTRN02C) once, after the imports: it
posts the 300 packaged daily transactions, so the transaction list and the
account balances have data (262 posted; 38 are rejected as over the credit
limit, as in the source). Its rejects file is written to `dalyrejs.dat` in the
working directory unless `carddemo.batch.dalyrejs.path` names another.
`carddemo.import.discgrp.enabled` loads the disclosure groups (interest rates),
and `carddemo.batch.intcalc.enabled` then runs the interest calculation
(INTCALC, CBACT04C) once, after the posting, as the JCL orders them: it adds
each account's interest to its balance and zeroes its cycle totals (except the
last account, which the source never rewrites), and writes the interest
transactions to the system transactions file `systran.dat`
(`carddemo.batch.systran.path`), dated by `carddemo.batch.intcalc.parm-date`
(default `2022071800`, the JCL's PARM). As in the source, those transactions
reach the transaction list only by the combine step (COMBTRAN):
`carddemo.batch.combtran.enabled` runs it after the interest calculation,
merging `systran.dat` into the transactions.
`carddemo.batch.tranrept.enabled` (with `carddemo.import.trancatg.enabled`
for the category names) then writes the transaction detail report (TRANREPT,
CBTRN03C) to `tranrept.dat` (`carddemo.batch.tranrept.output`), 133-byte
host-code-page lines, for the processing dates from
`carddemo.batch.tranrept.start-date` to `carddemo.batch.tranrept.end-date`
(defaults 2022-01-01 and 2022-07-06, the JCL's). A report requested from
the website (`/reports/new`) is submitted as the source submits it, as the
TRNRPT00 job to the JOBS queue; the internal reader takes it off the queue
and runs the same report with the requested dates, writing the same file
(`carddemo.intrdr.enabled`, on by default; `carddemo.intrdr.poll-ms`,
default 2000). `carddemo.batch.creastmt.enabled` (with
`carddemo.import.customerdata.enabled`) writes the account statements
(CREASTMT, CBSTM03A) after them: `statement.dat` (80-byte plain-text records)
and `statement-html.dat` (100-byte HTML records), in the host code page
(`carddemo.batch.creastmt.stmtfile`, `.htmlfile`). The importers read the packaged classpath resources under
`src/main/resources/seed-data/`; the application does not need
`../baseline` or a `carddemo.project-root` override to find those datasets.

### Development credentials

The packaged USRSEC data contains these development accounts. Usernames are case-insensitive on every web page and API; passwords are
case-sensitive and must match exactly (including spaces). For example,
`admin001` with `PASSWORD` works; `ADMIN001` with `password` does not. This
is an approved web-only departure from COBOL sign-on, which uppercases both.

The generated backend owns this policy: `UserIdentityPolicy` supplies identity
lookup and exact password comparison, while migration V28 makes the user ID
column and its unique key case-insensitive. Stored username spelling is retained;
creating another account that differs only in case returns `DUPLICATE`. V28
rejects an existing database with case-only duplicate accounts rather than
merging them. Such accounts must be resolved before that database can upgrade.

| Role | User IDs | Password |
|---|---|---|
| Administrator | `ADMIN001` through `ADMIN005` | `PASSWORD` |
| User | `USER0001` through `USER0005` | `PASSWORD` |

A direct authentication smoke check is:

```bash
curl -i -c /tmp/carddemo-cookies \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"ADMIN001","credential":"PASSWORD"}' \
  http://127.0.0.1:8080/api/authentication
```

The expected JSON outcome is `AUTHENTICATED` with destination `/admin`. The
request fields are `identifier` and `credential`, the same ones the sign-on
screen sends; any other field names are rejected with HTTP 400.

Account `00000000050` is present in the packaged account data and is intended
to be a representative authenticated inquiry:

```bash
curl -i -b /tmp/carddemo-cookies \
  http://127.0.0.1:8080/api/accounts/00000000050
```

This request must return HTTP 200 before the generated application is accepted.
See **Current validation status** below for the latest observed result.

The paged list endpoints (`/api/users`, `/api/transactions`) require the
`startKey`, `direction` (`FORWARD` or `BACKWARD`) and `anchorMode`
(`INCLUDE_ANCHOR` or `EXCLUDE_ANCHOR`) query parameters; without valid values
they return HTTP 400. For example:

```bash
curl -b /tmp/carddemo-cookies \
  'http://127.0.0.1:8080/api/transactions?startKey=&direction=FORWARD&anchorMode=INCLUDE_ANCHOR'
```

To run the packaged backend:

```bash
mvn package
java -jar target/carddemo-backend-0.1.0.jar \
  --carddemo.import.customerdata.enabled=true \
  --carddemo.import.accountdata.enabled=true \
  --carddemo.import.carddata.enabled=true \
  --carddemo.import.cardxref.enabled=true \
  --carddemo.import.trantype.enabled=true \
  --carddemo.import.tcatbal.enabled=true \
  --carddemo.import.discgrp.enabled=true \
  --carddemo.batch.posttran.enabled=true \
  --carddemo.batch.intcalc.enabled=true \
  --carddemo.batch.combtran.enabled=true \
  --carddemo.import.trancatg.enabled=true \
  --carddemo.import.pautdb.enabled=true
```

To run the posting as a batch job instead, without the web server (it exits
with code 4 when any record was rejected, as the source sets RETURN-CODE 4):

```bash
java -jar target/carddemo-backend-0.1.0.jar --spring.main.web-application-type=none \
  --spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionPostingJob \
  --carddemo.import.accountdata.enabled=true --carddemo.import.cardxref.enabled=true \
  --carddemo.import.tcatbal.enabled=true
```

The other converted batch programs run the same way, by job name. Where a
program also has a `carddemo.batch.<name>.enabled` flag, that flag instead
runs it once at web-server startup, after the jobs that run before it:

| Program | Job name | Needs |
|---|---|---|
| CBACT02C, CBACT03C, CBCUS01C (listings) | `cardFileListingJob`, `cardCrossReferenceListingJob`, `customerFileListingJob` | the packaged seeds |
| CBTRN01C (daily transaction verification) | `dailyTransactionVerificationJob` | `--carddemo.import.accountdata.enabled=true --carddemo.import.cardxref.enabled=true` |
| CBTRN02C (posting) | `dailyTransactionPostingJob` | as above; exits 4 when it rejects records |
| CBACT04C (interest) | `interestCalculationJob` | the posting's files, so run it in the same process after the posting (the startup flags above do this) |
| CBACT01C (account file distribution, READACCT) | `accountFileDistributionJob` | `--carddemo.import.accountdata.enabled=true`; writes `outfile.dat`, `arryfile.dat` and `vbrcfile.dat` (`carddemo.batch.readacct.outfile`, `.arryfile`, `.vbrcfile`) as host records: packed decimals, and RDW-prefixed variable-length records |
| CBEXPORT (customer data export) | `customerExportJob` | the customer, account, cross-reference and card imports (and the posting's transactions, if exported after it: `--carddemo.batch.export.enabled=true` runs it at startup after the posting and interest); writes `export.dat` (`carddemo.batch.export.output`), 500-byte records |
| CBIMPORT (customer data import) | `customerImportJob` | `--carddemo.batch.import.input=<export file>`; writes `customers.dat`, `accounts.dat`, `xrefs.dat`, `transactions.dat`, `cards.dat` and `errors.dat` (`carddemo.batch.import.<name>`) |
| COMBTRAN (combine the system transactions into the transaction file; a JCL utility step, no COBOL) | `combineTransactionsJob` | the interest job's `systran.dat` (`carddemo.batch.systran.path`); at startup with `--carddemo.batch.combtran.enabled=true`, after the interest |
| CBTRN03C (transaction detail report, TRANREPT) | `transactionDetailReportJob` | the posted transactions, `--carddemo.import.trantype.enabled=true --carddemo.import.trancatg.enabled=true`; the range at `--carddemo.batch.tranrept.start-date`/`.end-date`; writes `tranrept.dat` |
| CBSTM03A with CBSTM03B (account statements, CREASTMT) | `accountStatementJob` | the customer, account and cross-reference imports and the transactions (after the posting, and the interest and combine if run); writes `statement.dat` and `statement-html.dat` |
| PAUDBUNL (pending-authorization unload) | `pendingAuthorizationUnloadJob` | `--carddemo.import.pautdb.enabled=true` (the IMS database as shipped, into PAUTSUM0 and PAUTDTL1); writes `pautdb-roots.dat` (100-byte roots) and `pautdb-children.dat` (the root key, then the 200-byte child) (`carddemo.batch.paudbunl.roots`, `.children`); at startup with `--carddemo.batch.paudbunl.enabled=true` |
| PAUDBLOD (pending-authorization load) | `pendingAuthorizationLoadJob` | the two unload files (`carddemo.batch.paudblod.roots`, `.children`, the same defaults); segments already present are reported and kept; at startup with `--carddemo.batch.paudblod.enabled=true` |
| DBUNLDGS (pending-authorization GSAM unload) | `pendingAuthorizationGsamUnloadJob` | as PAUDBUNL; writes `pautdb-roots-gsam.dat` and `pautdb-children-gsam.dat` (200-byte children, no key) (`carddemo.batch.dbunldgs.roots`, `.children`); at startup with `--carddemo.batch.dbunldgs.enabled=true` |
| CBPAUP0C (purge expired authorizations) | `pendingAuthorizationPurgeJob` | `--carddemo.import.pautdb.enabled=true`; `--carddemo.batch.cbpaup0c.parm` (default the JCL's `00,00001,00001,Y`: expiry 0 days, debug on, which deletes every authorization); at startup with `--carddemo.batch.cbpaup0c.enabled=true` |
| COBTUPDT (transaction type maintenance, MNTTRDB2) | `transactionTypeBatchUpdateJob` | `--carddemo.import.trantype.enabled=true --carddemo.batch.mnttrdb2.input=<file>`: 53-byte host-code-page records (A/U/D/* + type + description); exits 4 on any error |

## MQ request/reply endpoints

D-MQ replaces the three MQ programs with authenticated synchronous HTTP calls.
Sign in using the cookie example above, then send the original character
record as `Content-Type: text/plain`. Responses are `text/plain`; retain
trailing spaces when reading them.

| Program | Endpoint | Request | Reply |
|---|---|---|---|
| CODATE01 | POST /api/mq/date | ignored | 1000 characters: system date/time plus spaces |
| COACCT01 | POST /api/mq/account | INQA followed by an 11-digit account key (remaining request filler optional) | 1000 characters; source labels and signed-display account amounts, or the source invalid-request text |
| COPAUA0C | POST /api/mq/authorizations | eighteen comma-separated request fields | 64 characters: card, transaction ID, auth-time ID, response, reason, edited amount, comma and one final space |

Authorization CSV fields, in order: YYMMDD, HHMMSS, card number, authorization
type, card expiry, message type, message source, processing code, decimal
amount, merchant category, acquirer country, POS entry mode, merchant ID,
merchant name, city, state, zip, transaction ID. Short numeric processing/POS
fields are zero-filled to 6/2 digits. Approval writes a pending detail and
updates its summary; declines use the source 05/3100 or 05/4100 behavior.
The customer, account and cross-reference import flags above supply lookups;
`carddemo.import.pautdb.enabled` supplies existing pending authorizations.

Each HTTP request is a fresh source task. The source's unreset reply pointer
and previous-detail amount therefore do not leak between requests. Negative
reply amounts retain the fixed leading-minus picture. Source activity, fraud
and expiry checks that are absent in the COBOL are also absent here. Business
lookup failures and duplicate detail keys are logged; the old queue adapters
and tables are retained but are not consumers for these endpoints.

## Run the browser application

In another terminal:

```bash
cd ui
npm install --no-package-lock
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173/`. Vite binds
the `localhost` name, which may resolve to IPv6 `::1` only, so
`http://127.0.0.1:5173/` can refuse the connection. The generated
Vite configuration proxies `/api` to the backend on port 8080. Using
`--no-package-lock` is intentional because lock files are not committed for
this generated project.

For a production build:

```bash
cd ui
npm install --no-package-lock
npm run build
```

## Automated checks

From the repository root:

```bash
mvn test
mvn package
```

The generated output has no `src/test` tree, so `mvn test` currently proves
only that the backend compiles; it runs no tests.

The application's test suite (`tests/`, run with `tests/run-local
--all-groups`) lives beside `output/` in the hss2 CardDemo corpus and is not
part of this repository. See `tests/README.md` there.

## Output structure

The current generated tree contains these principal areas:

```text
.
├── pom.xml
├── src/main/java/                   Spring Boot application and capability packages
│   ├── com/cardemo/                 Capability packages (account, auth, batch, card, ...)
│   ├── application/                 CardemoApplication, UserIdentityPolicy, access policy
│   ├── controller/                  Additional REST controllers
│   ├── service/                     Report submission adapter
│   └── adapter/                     JDBC gateways, seed imports, messaging adapters
├── src/main/resources/
│   ├── application.properties       H2, Flyway, and importer configuration
│   ├── db/migration/                Generated database migrations
│   └── seed-data/                   Packaged fixed-width source datasets
└── ui/
    ├── package.json
    ├── vite.config.js               React build and backend development proxy
    ├── src/application/             Authenticated application shell
    ├── src/screens/                 Source-derived screens
    ├── src/generated/registry.js    Assembled screen registry
    └── src/api.js                   Shared JSON transport
```

Every Java source declares a package under `com.cardemo`, but not every file
sits in the matching directory: the files under `application/`, `controller/`,
`service/` and `adapter/` declare `com.cardemo` packages (for example,
`application/CardemoApplication.java` is `com.cardemo.CardemoApplication`).
javac accepts this; search by package name rather than by path.
`src/main/java/com/cardemo/CardemoApplication.java` is an empty
package-declaration stub.

Batch programs are composed into Spring-managed services and JDBC
import/export adapters in the same application. Converted integration and queue
responsibilities use the generated application facilities rather than separate
program executables.

## Traceability and governance

Do not infer traceability from the target tree alone. The traceability and
governance records live in the hss2 repository and its CardDemo corpus, not in
this repository:

- `.hss2/trace_entries/` contains hss2's persisted trace data for the pipeline.
- `docs/carddemo-governance/contracts/` contains governed unit contracts and
  their retained evidence.
- `output/codeintent/` contains the CodeIntent handoff package.
- `output/trace/assembly-manifest.json`, plus per-unit replay directories, is
  an output of the governed atomic assembly path. It is not present in the
  current output and must not be claimed until the assembler emits it.

Acceptance requires a complete source-unit inventory, allowed RuleStore
decisions, bounded evidence, valid parametric source traces, connectivity and
physical-render checks, and source/rule ownership for every assembled target.
The model application may guide target shape but is not provenance.

## Current validation status

### Repository validation (2026-11-18)

Validated against this repository as checked out, with Java 21.0.13, Maven
3.9.9 and Node.js 24:

- `mvn package` built `target/carddemo-backend-0.1.0.jar`. With the documented
  startup flags, Flyway applied all 35 migrations (through
  `V35__report_submission.sql`) and the application started.
- The startup posting rejected 38 of the 300 daily transactions
  (`dalyrejs.dat`, 38 × 430 bytes) and posted 262. The interest calculation
  wrote 50 system transactions to `systran.dat`, and the combine step merged
  them.
- `ADMIN001`/`PASSWORD` and lowercase `admin001` signed on to `/admin`;
  `ADMIN001`/`password` returned `INVALID_CREDENTIALS`; unknown request fields
  returned 400; `user0001` signed on to `/menu` and was denied `/api/users`
  (403).
- Account `00000000050` returned 200 (customer 50); an unknown account returned
  404 and a non-numeric one 400. The transaction and user lists returned data.
- `/api/mq/date` returned 1000 characters and `/api/mq/account` answered
  `INQA00000000050`.
- Logout returned 204, and a later protected request returned 401.
- `ui/` built with `npm run build`.

The application contains 471 backend files (421 Java sources, 35 migrations,
14 seed datasets and `application.properties`) and 51 UI source files.

The sections below record validations performed in hss2 against its
`output/` directory. Their evidence (trace data, CodeIntent package, review
documents, hss2 gates) is not part of this repository.

### Trace-handoff remediation validation (2026-10-13)

The current `output/codeintent/` package was produced by a resumptive warm
CardDemo run after correcting target-to-source trace generation. This was not a
new full application-regression or byte-reproduction checkpoint; the 2026-10-09
run below remains the latest full generated-application validation.

- The CodeIntent handoff stage completed with status 128 and a successful
  disposition after scanning 2,052 trace partitions and all generated targets.
- The package contains 1,154 producer records, 165 rule-level trace bindings,
  19 precise statement bindings, zero imprecise statement bindings, 44 Scope
  Ledger classification rules and 8,765 unresolved scopes. Its closed-shape,
  artifact-reference, binding-parent and precise-statement validation passed.
- Source fan-out is retained rather than collapsed. For example,
  `CBACT01C:ACCOUNT-RECORD#61` maps COBOL lines 85-221 to the JDBC repository,
  account import service and ACCTDAT migration as three distinct target sites.
- Precise target attribution is retained at statement level. For example,
  `CBACT02C:<anonymous main>#13#w1` maps target lines 37-41 in
  `CardFileListingJob.java` to COBOL line 72 only.
- CodeIntent onboarding run 13 succeeded with 865 artifacts, 1,155 normalized
  producer records and 495 relational trace rows. Live Trace Explorer checks
  confirmed that all three fan-out targets render and that clicking the SQL or
  Java target selects the correct target artifact and exact source range.
- The focused handoff-delegate test, full Engine checks and full App checks
  passed. The CodeIntent suite passed 275 of 277 checks; its two remaining
  failures are pre-existing repository checks for root Markdown inventory and
  revision-custody preflight, not trace-generation or rendering failures.
- The legacy CardDemo handoff golden still expects seven absent CASH00
  statement bindings. The current package passes structural validation and
  real CodeIntent ingestion; the obsolete golden expectations remain to be
  reconciled separately rather than fabricating unsupported bindings.
- The quality gate passes with 484 backend files, 52 UI files and 34
  migrations. There is still no `output/trace/assembly-manifest.json`.

The CodeIntent descriptor, current handoff and source-custody files under
`output/codeintent/` are tracked generated output. Their producer records also
remain retained in `.hss2/trace_entries/` for reproducible handoff generation.

### Latest full application validation (2026-10-09)

Validated after a full native regeneration with
`tools/hss2 -v --clear -P .corpora/aws-carddemo`, on the paired
`story/hss2-923-remaining-conversion` branches. All six output writers
completed, as did the new CodeIntent export stage; all ten application trace
kinds contain all 43 source units. The run took about 67 minutes after integrating
hss2 upstream/dev `099e62f25`. The reproduction gate proved the application
byte-identical to commit `120dc290`. No generated output was hand-edited.
The approved web identity policy above is included.

- Maven packaged the backend; the runtime gate passed 68 HTTP
  checks and 22 independent MQ checks. Transactions posted at startup are
  visible, and regular users are denied administrator APIs.
- The batch gate passed the 204-line log baseline, all eleven independent
  source/seed oracle scenarios and failure controls. PAUT unload/reload is
  byte-exact over 21 roots and 202 children. Fraud writes to AUTHFRDS and
  PAUTDTL1 commit or roll back together, including duplicate-key updates.
- Headless Chromium passed 46 browser checks, including pending-authorization
  paging, detail navigation/bounds, fraud toggles and Back to the same account.
  Sign-on also verifies a mixed-case username with exact password case.
- The idiom gate passed with one metric improved; no baseline reset was needed.
- Ten native trace checks verify the identity-policy, MQ and screen templates.
  Trace evidence lives under `.hss2/trace_entries/`; there is still no
  `output/trace/assembly-manifest.json`.
- The quality gate passed with 484 backend files, 52 UI files and 34 migrations.
  Dependency/build directories are excluded from its application inventory.
- The CodeIntent package was emitted under `output/codeintent/`. At this
  checkpoint downstream CodeIntent ingestion had not yet been tested; the
  newer trace-handoff validation above supersedes that limitation.
- Tools checks passed. Full hss2 `make -j8 check` stops at an unrelated upstream
  ALC graph-export file-ordering test; no ALC change is part of this work.
- Alerts retain the previous source-analysis/template-literal debt. Latest
  upstream additionally recognizes a COBTUPDT typed SQL insert with an incomplete
  business/style mapping (one new error and four net warnings); that program
  renders identically to the previous output. No identity-policy alert is added.
  See hss2 `docs/carddemo-identity-policy/REVIEW.md` for this deferred coverage gap.
- Unused generated tasklets, ports and queue adapters remain. Summary/detail
  navigation reads an account's full child set; bounded SQL paging is deferred.
  These checks do not assert large-account scalability or eliminate all
  survey/generic-rendering debt.

The continuation's findings, resolved review issues and detailed evidence are
in hss2's `docs/carddemo-remaining-conversion/REVIEW.md`. The identity-policy
change and its full validation are recorded in `docs/carddemo-identity-policy/REVIEW.md`.

## Historical upstream validation

The following record describes upstream regeneration commit `a5a60b43`
specifically. Its open findings apply to that snapshot.

Validated on 2026-10-05 against output regenerated by
`tools/hss2 -v --clear-all -P .corpora/aws-carddemo` with hss2
`feature/corpora-fixes` @ 77195c002 and this repository @ 98cbc30a. That
version re-includes `CBACT01C`. `--clear-all` is required for that change
because a plain `--clear` keeps the persisted `.hss2/project_manifests.h2`.

- The pipeline traced 43 source units in every trace kind, including
  `CBACT01C`. The run took 1621 s until the output and every trace kind
  were complete, with a peak resident memory of 3.9 GiB.
- `mvn test` passed; it runs no tests (see **Automated checks**).
- `mvn package` built `target/carddemo-backend-0.1.0.jar`. The packaged
  backend started on Java 21, and Flyway applied all 27 generated migrations,
  including `V1__acctdat.sql`, which creates `ACCTDAT`.
- With the documented import flags, these API flows passed:
  - The authenticated request for account `00000000050` returned HTTP 200,
    with the account joined to customer 50 (Aniya Von). An unknown account
    returned 404 and a non-numeric account id returned 400.
  - `ADMIN001` sign-on (destination `/admin`), lowercase `user0001` sign-on
    (destination `/menu`), and rejection of a wrong password
    (`INVALID_CREDENTIALS`).
  - Session context, card list and card detail, the paged user list, and
    user create, read, duplicate rejection and delete.
  - Logout returned 204, and later protected requests returned 401.
- The paged list endpoints (`/api/users`, `/api/transactions`) require the
  `startKey`, `direction`, and `anchorMode` query parameters. Without them
  they return HTTP 400. The transaction list is empty because the documented
  import flags do not enable a transaction importer.
- Open: `/api/users` is reachable by the regular user `USER0001`; it should be
  administrator-only.
- Open: every `*ScreenPort` expected field count changed with the
  `--clear-all` re-survey (18 screens; for example `CACTVWA` 186 -> 297).
  The previous counts match the source: the `CACTVWAI` input map in
  `cpy-bms/COACTVW.CPY` has 186 elementary items. The current hss2 survey
  derives BMS field geometry differently, which is a survey regression. No
  other class references the ScreenPort classes, so runtime behavior is
  unchanged.
- `output/ui` built with Node.js 20.19.2 (`npm install --no-package-lock`,
  then `npm run build`). The Vite dev server served the application and
  proxied `/api` to the backend. The UI was not exercised in a browser.
- `git diff --check` reports trailing whitespace in the generated output,
  mostly tab-only lines in `ui/src/generated/`.
