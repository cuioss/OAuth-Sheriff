# Command Configuration

## ./mvnw -Ppre-commit clean install

### Last Execution Duration
- **Duration**: 477000ms (7 minutes 57 seconds)
- **Last Updated**: 2025-10-27

### Acceptable Warnings
- `[INFO] /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/target/generated-sources/annotations/de/cuioss/sheriff/oauth/core/json/_JwkKey_DslJsonConverter.java: Einige Eingabedateien verwenden nicht geprüfte oder unsichere Vorgänge.` (unchecked operations warning in generated DSL-JSON code)
- `[INFO] /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/target/generated-sources/annotations/de/cuioss/sheriff/oauth/core/json/_JwkKey_DslJsonConverter.java: Wiederholen Sie die Kompilierung mit -Xlint:unchecked, um Details zu erhalten.` (follow-up message for unchecked operations)
- `[INFO] /Users/oliver/git/OAuth-Sheriff/benchmarking/benchmarking-common/src/test/java/de/cuioss/benchmarking/common/metrics/MetricsTransformerTest.java: /Users/oliver/git/OAuth-Sheriff/benchmarking/benchmarking-common/src/test/java/de/cuioss/benchmarking/common/metrics/MetricsTransformerTest.java verwendet nicht geprüfte oder unsichere Vorgänge.` (unchecked cast warnings in test assertions)
- `[INFO] /Users/oliver/git/OAuth-Sheriff/benchmarking/benchmarking-common/src/test/java/de/cuioss/benchmarking/common/metrics/MetricsTransformerTest.java: Wiederholen Sie die Kompilierung mit -Xlint:unchecked, um Details zu erhalten.` (follow-up message for unchecked operations)

## ./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests

### Last Execution Duration
- **Duration**: 180000ms (3 minutes)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `WARN [io.mic.cor.ins.MeterRegistry] (vert.x-acceptor-thread-0) This Gauge has been already registered (MeterId{name='http.server.active.connections', tags=[]})` (Quarkus Micrometer warning about duplicate gauge registration)
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` (Netty library using deprecated Unsafe methods)
- `WARNING: sun.misc.Unsafe::arrayBaseOffset` (Follow-up details for Unsafe deprecation)
- `WARNING: A restricted method in java.lang.System has been called` (Brotli4j native library access)
- `WARNING: java.lang.System::loadLibrary` (Follow-up details for restricted method)
- `WARNING [de.cui.she.oau.cor.IssuerConfig] JWTValidation-134: IssuerConfig for issuer ... has claimSubOptional=true` (Intentional test configuration for non-standard token validation)
- `WARNING [de.cui.she.oau.cor.pip.val.TokenClaimValidator] JWTValidation-112: Missing recommended element: expectedAudience` (Test configuration without audience validation)
- `WARN [org.keycloak.storage.datastore.DefaultExportImportManager] Referenced client scope ... doesn't exist` (Keycloak realm import warnings - expected during initial setup)
- `WARN [org.keycloak.models.utils.RepresentationToModel] Referenced client scope ... doesn't exist. Ignoring` (Keycloak realm import follow-up warnings)

## ./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark

### Last Execution Duration
- **Duration**: 290000ms (4 minutes 50 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- (No acceptable warnings documented yet)

## ./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk

### Last Execution Duration
- **Duration**: 270000ms (4 minutes 30 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `WARN [io.mic.cor.ins.MeterRegistry] (vert.x-acceptor-thread-0) This Gauge has been already registered (MeterId{name='http.server.active.connections', tags=[]})` (Quarkus Micrometer warning about duplicate gauge registration)
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` (Netty library using deprecated Unsafe methods)
- `WARNING: sun.misc.Unsafe::arrayBaseOffset` (Follow-up details for Unsafe deprecation)
- `WARNING: A restricted method in java.lang.System has been called` (Brotli4j native library access)
- `WARNING: java.lang.System::loadLibrary` (Follow-up details for restricted method)
- `WARNING [de.cui.she.oau.cor.IssuerConfig] JWTValidation-134: IssuerConfig for issuer ... has claimSubOptional=true` (Intentional test configuration for non-standard token validation)
- `WARN [org.keycloak.storage.datastore.DefaultExportImportManager] Referenced client scope ... doesn't exist` (Keycloak realm import warnings - expected during initial setup)
- `WARN [org.keycloak.models.utils.RepresentationToModel] Referenced client scope ... doesn't exist. Ignoring` (Keycloak realm import follow-up warnings)

## handle-pull-request

### CI/Sonar Duration
- **Duration**: 300000ms (5 minutes)
- **Last Updated**: 2025-10-14

### Notes
- This duration represents the time to wait for CI and SonarCloud checks to complete
- Includes buffer time for queue delays

## docs-adoc

### Skipped Files

Files excluded from AsciiDoc validation processing:

(No skipped files documented yet)

### Acceptable Warnings

Known warnings that are acceptable and should not trigger fixes:

(No acceptable warnings - validator bug for consecutive numbered lists has been fixed in cui-llm-rules v2025-10-17)

### Last Updated
2025-10-17

## docs-verify-links

### Skipped Files

Files excluded from link verification:

- `target/**/*.adoc` (Build artifacts - auto-generated)
- `node_modules/**/*.adoc` (Dependency documentation)
- `.git/**/*.adoc` (Git metadata)

### Acceptable Warnings

Links approved by user as acceptable (even if broken/non-standard):

(No acceptable warnings documented yet)

### Last Verification

- **Date**: 2025-10-17
- **Files verified**: 62
- **Total links**: 713
- **Status**: ✅ All links valid (0 broken, 0 violations)

## docs-technical-adoc-review

### Skipped Files

Files excluded from technical AsciiDoc review:

(None - all files processed successfully)

### Skipped Directories

Directories excluded entirely:

- `target/` - Build artifacts (auto-generated)
- `node_modules/` - Dependencies
- `.git/` - Git metadata

### Acceptable Warnings

Warnings approved as acceptable:

(None - all validation warnings resolved)

### Last Execution

- **Date**: 2025-10-21
- **Directories processed**: 8 (5 sequential + 3 parallel)
- **Files reviewed**: 62
- **Issues fixed**: 32
- **Status**: ✅ SUCCESS
- **Parallel agents**: 3 (oauth-sheriff-core, oauth-sheriff-quarkus-parent, benchmarking)

### Lessons Learned

All previous lessons have been applied to:
- Lessons 1-5: Applied to `~/git/cui-llm-rules/claude/marketplace/skills/cui-documentation/SKILL.md` (2025-10-27)
- Lesson 6: Applied to `~/.claude/commands/docs-technical-adoc-review.md` (2025-10-27)

(New lessons will be added here as they are discovered)

## setup-project-permissions

### User-Approved Permissions

Permissions flagged as suspicious but approved by user to keep:

(None - all suspicious permissions were removed)

### Last Execution

- **Date**: 2025-10-21
- **Changes applied**: 11 total
  - Removed: 1 (overly broad Read(//Users/oliver/**))
  - Added: 3 (2 standard permissions + Write for ask list)
  - Path fixes: 7 (user-absolute → user-relative)
  - Sorted: all lists
- **Final permission count**: 31 allow, 0 deny, 1 ask

### Permission Categories

**Bash Permissions (14):**
- Standard commands: 12 (cat, dirname, done, find, for, grep, head, sed, sort, test, uniq, wc)
- Project scripts: 2 (asciidoc-validator.sh, verify-adoc-links.py)

**Read Permissions (9):**
- Agent/Command definitions: 2 (.claude/agents, .claude/commands)
- CUI LLM Rules: 7 (claude, scripts, standards/*)

**SlashCommand Permissions (3):**
- /agents-create, /agents-doctor:*, /slash-doctor:*

**WebFetch Permissions (10):**
- Technical blogs and documentation domains

**WebSearch Permissions (1):**
- General web search capability

**Ask Permissions (1):**
- Write(.claude/settings.local.json) - requires explicit approval for security

### Security Notes

**Removed in this run:**
- `Read(//Users/oliver/**)` - Overly broad wildcard granting access to entire home directory (CRITICAL SECURITY RISK)

**Path Security:**
- All paths converted to user-relative format (`~/`) instead of user-absolute (`/Users/oliver/`)
- Prevents permission breaks when sharing across different user accounts

**Settings Write Protection:**
- `Write(.claude/settings.local.json)` placed in ask list (not allow)
- Ensures Claude cannot modify permissions without explicit user approval
