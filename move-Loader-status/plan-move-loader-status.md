# Move LoaderStatus Types from cui-http to OAuth-Sheriff

**Issue Reference:** /Users/oliver/git/OAuth-Sheriff/move-Loader-status/move-Loader-status.adoc

---

## Instructions for Implementation Agent

**CRITICAL:** Implement tasks **ONE AT A TIME** in the order listed below.

After implementing each task:
1. ✅ Verify all acceptance criteria are met
2. ✅ Run all quality checks (tests, build, formatting)
3. ✅ Mark the task as done: `[x]`
4. ✅ Only proceed to next task when current task is 100% complete

**Do NOT skip ahead.** Each task builds on previous tasks.

---

## Tasks

### Task 1: Verify Prerequisites and Environment

**Goal:** Ensure both repositories are in correct state before starting migration

**References:**
- Issue: Phase 0 (lines 28-55) - Branch Strategy & Prerequisites
- Issue: Phase 3 (lines 213-263) - Critical Pre-Execution Checks

**Checklist:**
- [x] Read and understand all references above
- [x] Verify OAuth-Sheriff is on `feature/integrate_cui_http_changes` branch
- [x] Verify OAuth-Sheriff working directory is clean (only `move-Loader-status/` untracked is acceptable)
- [x] Verify cui-http repository is accessible at `/Users/oliver/git/cui-http`
- [x] Verify cui-http source files exist: `LoaderStatus.java`, `LoadingStatusProvider.java`, `LoadingStatusProviderTest.java`
- [x] Verify target directory exists in OAuth-Sheriff: `oauth-sheriff-core/src/main/java/de/cuioss/sheriff/oauth/core/util/`
- [x] If target directory doesn't exist, create it and corresponding test directory
- [x] Verify no files named `Loader*` already exist in target directory
- [x] Ask user for confirmation to proceed if any prerequisite is not met

**Acceptance Criteria:**
- OAuth-Sheriff on correct branch (`feature/integrate_cui_http_changes`)
- Working directories are clean or have expected untracked files only
- All source files exist in cui-http at expected locations
- Target directories exist in OAuth-Sheriff
- No file naming conflicts detected

---

### Task 2: Copy and Adapt LoaderStatus.java

**Goal:** Copy LoaderStatus enum from cui-http to OAuth-Sheriff with package and documentation updates

**References:**
- Issue: Phase 1 (lines 56-76) - Files to Move from cui-http
- Issue: Phase 3, Step 1.1 (lines 266-282) - Copy LoaderStatus.java

**Checklist:**
- [x] Read and understand all references above
- [x] Read source file: `/Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/LoaderStatus.java`
- [x] Copy content to: `/Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/main/java/de/cuioss/sheriff/oauth/core/util/LoaderStatus.java`
- [x] Update package declaration to `package de.cuioss.sheriff.oauth.core.util;`
- [x] Update class Javadoc (line ~19) to be OAuth/JWT-specific: "Enum representing the status of a JWT/OAuth loader"
- [x] Remove reference to `{@link de.cuioss.http.client.handler.HttpHandler}` from Javadoc
- [x] Preserve all enum values unchanged: `OK`, `ERROR`, `LOADING`, `UNDEFINED`
- [x] Preserve all methods unchanged
- [x] Verify file compiles: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw clean compile -pl oauth-sheriff-core`

**Acceptance Criteria:**
- LoaderStatus.java exists in OAuth-Sheriff util package
- Package declaration is `de.cuioss.sheriff.oauth.core.util`
- Javadoc is OAuth/JWT-specific without generic HTTP references
- All enum values and methods preserved
- File compiles without errors

---

### Task 3: Copy and Adapt LoadingStatusProvider.java

**Goal:** Copy LoadingStatusProvider interface from cui-http to OAuth-Sheriff with package and documentation updates

**References:**
- Issue: Phase 1 (lines 56-76) - Files to Move from cui-http
- Issue: Phase 3, Step 1.2 (lines 284-300) - Copy LoadingStatusProvider.java

**Checklist:**
- [x] Read and understand all references above
- [x] Read source file: `/Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/LoadingStatusProvider.java`
- [x] Copy content to: `/Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/main/java/de/cuioss/sheriff/oauth/core/util/LoadingStatusProvider.java`
- [x] Update package declaration to `package de.cuioss.sheriff.oauth.core.util;`
- [x] Update Javadoc: Replace "JWT validation components" with "OAuth/JWT validation components"
- [x] Update Javadoc: Remove generic HTTP references, make OAuth/JWT-specific
- [x] Update Javadoc examples to use OAuth-Sheriff context (JwksLoader, IssuerConfig)
- [x] Update `@since` tag to match OAuth-Sheriff versioning if present
- [x] Preserve interface contract completely unchanged
- [x] Verify file compiles: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw clean compile -pl oauth-sheriff-core`

**Acceptance Criteria:**
- LoadingStatusProvider.java exists in OAuth-Sheriff util package
- Package declaration is `de.cuioss.sheriff.oauth.core.util`
- Javadoc is OAuth/JWT-specific with appropriate examples
- Interface contract unchanged
- File compiles without errors

---

### Task 4: Copy and Adapt LoadingStatusProviderTest.java

**Goal:** Copy test class from cui-http to OAuth-Sheriff with package updates

**References:**
- Issue: Phase 1 (lines 56-76) - Files to Move from cui-http
- Issue: Phase 3, Step 1.3 (lines 302-314) - Copy LoadingStatusProviderTest.java

**Checklist:**
- [x] Read and understand all references above
- [x] Read source file: `/Users/oliver/git/cui-http/src/test/java/de/cuioss/http/client/LoadingStatusProviderTest.java`
- [x] Copy content to: `/Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/test/java/de/cuioss/sheriff/oauth/core/util/LoadingStatusProviderTest.java`
- [x] Update package declaration to `package de.cuioss.sheriff.oauth.core.util;`
- [x] Update imports to use new package: `de.cuioss.sheriff.oauth.core.util.LoaderStatus` and `de.cuioss.sheriff.oauth.core.util.LoadingStatusProvider`
- [x] Preserve all test logic completely unchanged
- [x] Run test to verify it passes: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw test -Dtest=LoadingStatusProviderTest`

**Acceptance Criteria:**
- LoadingStatusProviderTest.java exists in OAuth-Sheriff test util package
- Package declaration is `de.cuioss.sheriff.oauth.core.util`
- Imports use new OAuth-Sheriff package
- All test logic unchanged
- Test compiles and passes

---

### Task 5: Update Main Source File Imports

**Goal:** Update all main source file imports to use new OAuth-Sheriff package

**References:**
- Issue: Phase 1 (lines 106-134) - Files to Update in OAuth-Sheriff (Main Source Files)
- Issue: Phase 3, Step 2.1 (lines 316-363) - Update Main Source Imports (Automated)

**Checklist:**
- [x] Read and understand all references above
- [x] Use macOS (BSD) sed syntax as environment is darwin platform
- [x] Run automated import replacement for LoaderStatus in main source:
  `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && find src/main/java -name "*.java" -type f -exec sed -i '' 's/import de\.cuioss\.http\.client\.LoaderStatus;/import de.cuioss.sheriff.oauth.core.util.LoaderStatus;/g' {} \;`
- [x] Run automated import replacement for LoadingStatusProvider in main source:
  `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && find src/main/java -name "*.java" -type f -exec sed -i '' 's/import de\.cuioss\.http\.client\.LoadingStatusProvider;/import de.cuioss.sheriff.oauth.core.util.LoadingStatusProvider;/g' {} \;`
- [x] Verify changes in these 6 files: IssuerConfig.java, IssuerConfigResolver.java, jwks/JwksLoader.java, jwks/http/HttpJwksLoader.java, jwks/key/JWKSKeyLoader.java, well_known/HttpWellKnownResolver.java
- [x] Verify JwksLoaderFactory.java Javadoc example (line 43) still resolves correctly (no import change needed, just type reference)
- [x] Verify no old package references remain: `grep -r "import de.cuioss.http.client.Loader" /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/main/java/`
- [x] Compile to verify: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw clean compile -pl oauth-sheriff-core`

**Acceptance Criteria:**
- All 6 main source files have updated imports
- JwksLoaderFactory.java Javadoc example verified
- No references to old package remain in main source imports
- All files compile without errors

---

### Task 6: Update Test File Imports

**Goal:** Update all test file imports to use new OAuth-Sheriff package

**References:**
- Issue: Phase 1 (lines 136-177) - Files to Update in OAuth-Sheriff (Test Files)
- Issue: Phase 3, Step 2.2 (lines 365-381) - Update Test Imports (Automated)

**Checklist:**
- [x] Read and understand all references above
- [x] Use macOS (BSD) sed syntax as environment is darwin platform
- [x] Run automated import replacement for LoaderStatus in tests:
  `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && find src/test/java -name "*.java" -type f -exec sed -i '' 's/import de\.cuioss\.http\.client\.LoaderStatus;/import de.cuioss.sheriff.oauth.core.util.LoaderStatus;/g' {} \;`
- [x] Run automated import replacement for LoadingStatusProvider in tests:
  `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && find src/test/java -name "*.java" -type f -exec sed -i '' 's/import de\.cuioss\.http\.client\.LoadingStatusProvider;/import de.cuioss.sheriff.oauth.core.util.LoadingStatusProvider;/g' {} \;`
- [x] Verify changes in 12 test files listed in issue section 1.3.2 (lines 136-177)
- [x] Verify no old package references remain: `grep -r "import de.cuioss.http.client.Loader" /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/test/java/`
- [x] Run tests to verify: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw test -pl oauth-sheriff-core`

**Acceptance Criteria:**
- All 12 test files have updated imports
- No references to old package remain in test imports
- All tests compile and pass

---

### Task 7: Update OAuth-Sheriff Documentation

**Goal:** Update technical documentation to reference new package location

**References:**
- Issue: Phase 1 (lines 179-188) - Documentation Files
- Issue: Phase 3, Step 2.4 (lines 397-414) - Update Documentation

**Checklist:**
- [x] Read and understand all references above
- [x] Read file: `/Users/oliver/git/OAuth-Sheriff/doc/specification/technical-components.adoc`
- [x] Find all code examples using `LoaderStatus` or `LoadingStatusProvider`
- [x] Update package references from `de.cuioss.http.client` to `de.cuioss.sheriff.oauth.core.util`
- [x] Search for these specific patterns (from issue lines 406-412):
  - `getLoaderStatus() == LoaderStatus.OK`
  - `CompletableFuture<LoaderStatus>`
  - `getLoaderStatus()`
  - `LoaderStatus.IN_PROGRESS → LoaderStatus.LOADING`
  - `LoadingStatusProvider` interface
- [x] Update each reference to use correct package context
- [x] If unclear about any documentation change, ask user for clarification

**Acceptance Criteria:**
- All package references in technical-components.adoc updated
- Code examples use `de.cuioss.sheriff.oauth.core.util` package
- Documentation is technically accurate
- No references to old `de.cuioss.http.client` package for Loader types

---

### Task 8: OAuth-Sheriff Build Verification

**Goal:** Verify all changes compile, pass tests, and meet quality standards

**References:**
- Issue: Phase 3, Step 2.5 (lines 416-429) - Build Verification
- Issue: Phase 5 (lines 557-680) - Testing & Validation

**Checklist:**
- [x] Read and understand all references above
- [x] Run clean compile: `cd /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core && ../mvnw clean compile -pl oauth-sheriff-core`
- [x] Run all unit tests: `cd /Users/oliver/git/OAuth-Sheriff && ./mvnw test -pl oauth-sheriff-core`
- [x] Verify no old package references: `grep -r "de.cuioss.http.client.Loader" /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/src/`
- [x] Run integration tests: `cd /Users/oliver/git/OAuth-Sheriff && ./mvnw verify -pl oauth-sheriff-core`
- [x] Run pre-commit checks: `cd /Users/oliver/git/OAuth-Sheriff && ./mvnw -Ppre-commit clean verify -pl oauth-sheriff-core`
- [x] If any errors occur, fix them and re-run failed step
- [x] DO NOT proceed until all checks pass

**Acceptance Criteria:**
- Clean compile succeeds
- All unit tests pass
- No references to old package found
- Integration tests pass
- Pre-commit quality checks pass
- Zero compilation errors or test failures

---

### Task 9: Commit and Push OAuth-Sheriff Changes

**Goal:** Commit and push all OAuth-Sheriff changes to feature branch

**References:**
- Issue: Phase 7, Steps 1-3 (lines 741-800) - Final Build and Push

**Checklist:**
- [x] Read and understand all references above
- [x] Run final build: `cd /Users/oliver/git/OAuth-Sheriff && ./mvnw clean install`
- [x] Verify build passes completely
- [x] Check git status: `cd /Users/oliver/git/OAuth-Sheriff && git status`
- [x] Review changes: `cd /Users/oliver/git/OAuth-Sheriff && git diff`
- [ ] Stage all changes: `cd /Users/oliver/git/OAuth-Sheriff && git add .`
- [ ] Commit with descriptive message from issue (lines 770-788):
  ```bash
  cd /Users/oliver/git/OAuth-Sheriff && git commit -m "$(cat <<'EOF'
  refactor: move LoaderStatus types from cui-http to util package

  - Move LoaderStatus enum to de.cuioss.sheriff.oauth.core.util
  - Move LoadingStatusProvider interface to de.cuioss.sheriff.oauth.core.util
  - Move LoadingStatusProviderTest to de.cuioss.sheriff.oauth.core.util
  - Update all imports in 18 files (6 main + 12 test)
  - Update Javadoc references in JwksLoaderFactory
  - Update documentation with new package references

  These types are OAuth/JWT-specific and belong in OAuth-Sheriff.
  Pre-1.0 refactoring for better code cohesion.

  🤖 Generated with [Claude Code](https://claude.com/claude-code)

  Co-Authored-By: Claude <noreply@anthropic.com>
  EOF
  )"
  ```
- [ ] Push to remote: `cd /Users/oliver/git/OAuth-Sheriff && git push origin feature/integrate_cui_http_changes`
- [ ] Verify push succeeded: `cd /Users/oliver/git/OAuth-Sheriff && git log -1 --oneline`

**Acceptance Criteria:**
- Final build passes with `./mvnw clean install`
- All changes committed with proper message format
- Changes pushed to `origin/feature/integrate_cui_http_changes`
- Remote branch verified with successful push
- Commit visible in git log

---

### Task 10: Remove Files from cui-http

**Goal:** Delete LoaderStatus types from cui-http repository

**References:**
- Issue: Phase 3, Step 3.1 (lines 430-439) - Remove Source Files

**Checklist:**
- [ ] Read and understand all references above
- [ ] Verify cui-http repository is accessible: `ls /Users/oliver/git/cui-http/`
- [ ] Check current branch in cui-http: `cd /Users/oliver/git/cui-http && git branch --show-current`
- [ ] If unclear about branch strategy, ask user whether to use `main` or create feature branch
- [ ] Delete LoaderStatus.java: `rm /Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/LoaderStatus.java`
- [ ] Delete LoadingStatusProvider.java: `rm /Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/LoadingStatusProvider.java`
- [ ] Delete LoadingStatusProviderTest.java: `rm /Users/oliver/git/cui-http/src/test/java/de/cuioss/http/client/LoadingStatusProviderTest.java`
- [ ] Verify deletion: `ls /Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/Loader* 2>&1`
- [ ] Expected output: "No such file or directory"

**Acceptance Criteria:**
- All three files deleted from cui-http
- No Loader* files remain in cui-http client package
- Deletion verified with ls command

---

### Task 11: Update cui-http Documentation - client-handlers-readme.adoc

**Goal:** Remove LoadingStatusProvider section from cui-http handler documentation

**References:**
- Issue: Phase 3, Step 3.2 (lines 441-465) - Update Documentation - client-handlers-readme.adoc

**Checklist:**
- [ ] Read and understand all references above
- [ ] Read file: `/Users/oliver/git/cui-http/doc/client-handlers-readme.adoc`
- [ ] Locate section "Loading Status" (lines 106-119 in issue reference)
- [ ] Remove entire section including:
  - `=== Loading Status` heading
  - `==== LoadingStatusProvider` subsection
  - `==== LoaderStatus` subsection
  - All enum value descriptions (UNDEFINED, IN_PROGRESS, ERROR, OK)
- [ ] Verify section removal is complete
- [ ] No replacement needed (complete removal)

**Acceptance Criteria:**
- "Loading Status" section removed from client-handlers-readme.adoc
- No references to LoadingStatusProvider remain
- No references to LoaderStatus remain
- Document structure remains valid after removal

---

### Task 12: Update cui-http Documentation - http-result-pattern.adoc

**Goal:** Remove LoaderStatus references from HTTP result pattern documentation

**References:**
- Issue: Phase 3, Step 3.3 (lines 467-495) - Update Documentation - http-result-pattern.adoc

**Checklist:**
- [ ] Read and understand all references above
- [ ] Read file: `/Users/oliver/git/cui-http/doc/http-result-pattern.adoc`
- [ ] Locate pattern matching examples (lines 65-135+ mentioned in issue)
- [ ] Remove or replace LoaderStatus references from examples
- [ ] Replace OAuth-specific examples with generic HTTP handling patterns (example provided in issue lines 476-493)
- [ ] Use generic HTTP result patterns without OAuth-specific types
- [ ] Verify examples still demonstrate HttpResult usage correctly
- [ ] If uncertain about replacement strategy, ask user for guidance

**Acceptance Criteria:**
- All LoaderStatus references removed from examples
- Examples replaced with generic HTTP patterns
- Documentation demonstrates HttpResult usage without OAuth types
- Examples are technically correct and compile-ready

---

### Task 13: Update cui-http Javadoc - HttpResult.java

**Goal:** Remove LoaderStatus references from HttpResult Javadoc examples

**References:**
- Issue: Phase 3, Step 3.4 (lines 497-503) - Update Javadoc - HttpResult.java

**Checklist:**
- [ ] Read and understand all references above
- [ ] Read file: `/Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/result/HttpResult.java`
- [ ] Locate pattern matching examples in Javadoc (approximately lines 74-86)
- [ ] Remove LoaderStatus references from Javadoc examples
- [ ] Use same strategy as Task 12: replace with generic HTTP patterns
- [ ] Ensure Javadoc still demonstrates proper HttpResult usage
- [ ] Verify Javadoc syntax is valid after changes

**Acceptance Criteria:**
- LoaderStatus references removed from HttpResult.java Javadoc
- Examples replaced with generic HTTP patterns
- Javadoc syntax is valid
- Examples demonstrate HttpResult API correctly

---

### Task 14: cui-http Build Verification

**Goal:** Verify cui-http builds correctly without LoaderStatus types

**References:**
- Issue: Phase 3, Step 3.6 (lines 515-533) - Build Verification
- Issue: Phase 5 (lines 592-616) - cui-http Testing

**Checklist:**
- [ ] Read and understand all references above
- [ ] Verify files are deleted: `ls /Users/oliver/git/cui-http/src/main/java/de/cuioss/http/client/Loader* 2>/dev/null`
- [ ] Expected: "No such file or directory"
- [ ] Run clean install: `cd /Users/oliver/git/cui-http && ./mvnw clean install`
- [ ] Run all tests: `cd /Users/oliver/git/cui-http && ./mvnw test`
- [ ] Run pre-commit checks: `cd /Users/oliver/git/cui-http && ./mvnw -Ppre-commit clean verify`
- [ ] Verify no lingering references: `grep -r "LoaderStatus\|LoadingStatusProvider" /Users/oliver/git/cui-http/src/main/java/`
- [ ] Expected: No matches (or only in comments explaining removal)
- [ ] Verify module-info.java exports unchanged: Read and verify `de.cuioss.http.client` package still exported
- [ ] If any errors occur, fix them and re-run failed step
- [ ] DO NOT proceed until all checks pass

**Acceptance Criteria:**
- Files confirmed deleted
- Clean install passes
- All tests pass
- Pre-commit checks pass
- No references to removed types found (except explanatory comments)
- Module exports verified correct

---

### Task 15: Commit and Push cui-http Changes

**Goal:** Commit and push all cui-http changes to appropriate branch

**References:**
- Issue: Phase 7, Steps 4-6 (lines 802-860) - cui-http Final Build and Push

**Checklist:**
- [ ] Read and understand all references above
- [ ] Run final build: `cd /Users/oliver/git/cui-http && ./mvnw clean install`
- [ ] Verify build passes completely
- [ ] Check git status: `cd /Users/oliver/git/cui-http && git status`
- [ ] Review changes: `cd /Users/oliver/git/cui-http && git diff`
- [ ] Stage all changes: `cd /Users/oliver/git/cui-http && git add .`
- [ ] Commit with descriptive message from issue (lines 831-846):
  ```bash
  cd /Users/oliver/git/cui-http && git commit -m "$(cat <<'EOF'
  refactor: remove OAuth-specific LoaderStatus types

  - Remove LoaderStatus enum (moved to OAuth-Sheriff)
  - Remove LoadingStatusProvider interface (moved to OAuth-Sheriff)
  - Remove LoadingStatusProviderTest (moved to OAuth-Sheriff)
  - Update documentation to remove LoaderStatus examples
  - Update HttpResult Javadoc to remove OAuth-specific patterns

  These types are OAuth/JWT-specific and only used by OAuth-Sheriff.
  Pre-1.0 cleanup for better separation of concerns.

  🤖 Generated with [Claude Code](https://claude.com/claude-code)

  Co-Authored-By: Claude <noreply@anthropic.com>
  EOF
  )"
  ```
- [ ] Confirm target branch with user before pushing (main or feature branch)
- [ ] Push to remote: `cd /Users/oliver/git/cui-http && git push origin [branch-name]`
- [ ] Verify push succeeded: `cd /Users/oliver/git/cui-http && git log -1 --oneline`

**Acceptance Criteria:**
- Final build passes with `./mvnw clean install`
- All changes committed with proper message format
- Target branch confirmed with user
- Changes pushed to remote
- Commit visible in git log

---

### Task 16: Final Cross-Repository Verification

**Goal:** Verify both repositories are in correct final state

**References:**
- Issue: Phase 7, Step 7 (lines 862-891) - Verification
- Issue: Phase 8 (lines 893-939) - Success Criteria

**Checklist:**
- [ ] Read and understand all references above
- [ ] Verify OAuth-Sheriff commit: `cd /Users/oliver/git/OAuth-Sheriff && git log -1 --stat`
- [ ] Should show new files in de/cuioss/sheriff/oauth/core/util/
- [ ] Verify cui-http commit: `cd /Users/oliver/git/cui-http && git log -1 --stat`
- [ ] Should show deleted LoaderStatus files
- [ ] Verify OAuth-Sheriff remote: `cd /Users/oliver/git/OAuth-Sheriff && git fetch origin && git log origin/feature/integrate_cui_http_changes -1`
- [ ] Verify cui-http remote: `cd /Users/oliver/git/cui-http && git fetch origin && git log origin/[branch-name] -1`
- [ ] Run final OAuth-Sheriff test suite: `cd /Users/oliver/git/OAuth-Sheriff && ./mvnw clean verify`
- [ ] Confirm all Phase 8 OAuth-Sheriff success criteria met (issue lines 897-907)
- [ ] Confirm all Phase 8 cui-http success criteria met (issue lines 909-920)
- [ ] Confirm all Phase 8 integration criteria met (issue lines 922-928)
- [ ] Confirm all Phase 8 git operations criteria met (issue lines 930-939)

**Acceptance Criteria:**
- OAuth-Sheriff shows 3 new files in util package
- cui-http shows 3 deleted Loader files
- Both remotes verified with successful pushes
- Full test suites pass in both repositories
- All Phase 8 success criteria verified and met
- Migration is 100% complete

---

## Completion Criteria

All tasks above must be marked `[x]` before the issue is considered complete.

**Final verification:**
1. All acceptance criteria met for every task
2. All tests passing in both repositories
3. No references to old package remain in OAuth-Sheriff
4. No LoaderStatus types remain in cui-http
5. Documentation updated in both repositories
6. All changes committed and pushed to remotes
7. Both repositories build successfully with `./mvnw clean install`

---

**Plan created by:** issue-manager agent
**Date:** 2025-10-28
**Total tasks:** 16
