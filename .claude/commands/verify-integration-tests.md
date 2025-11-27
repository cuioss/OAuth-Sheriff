# Verify Integration Tests Command

Execute integration tests with comprehensive Quarkus/Keycloak log analysis and issue tracking.

## WORKFLOW INSTRUCTIONS

### Step 1: Read Configuration

Load acceptable warnings from run configuration:

```
Skill: cui-utilities:claude-run-configuration
Workflow: Read Configuration
Field: commands.verify-integration-tests.acceptable_warnings
```

If field doesn't exist, use empty array `[]`.

### Step 2: Execute Maven Build via Builder Skill

Delegate build execution to the builder-maven-rules skill workflow:

```
Skill: builder:builder-maven-rules
Workflow: Execute Maven Build
Parameters:
  goals: clean verify -Pintegration-tests
  module: oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests
  output_mode: structured
```

The skill workflow will:
- Execute the Maven build with proper timeout handling
- Capture output to timestamped file in target/
- Parse and categorize issues (compilation errors, test failures, etc.)
- Return structured JSON with status, issues, and metrics

**Expected Output Format:**
```json
{
  "status": "success|error",
  "data": {
    "build_status": "SUCCESS|FAILURE",
    "issues": [...],
    "summary": { "compilation_errors": 0, "test_failures": 0, ... }
  },
  "metrics": { "duration_ms": ..., "tests_run": ..., "tests_failed": ... }
}
```

### Step 3: Analyze Build Output and Logs
Thoroughly analyze the Maven output AND all Quarkus/Keycloak logs for:
- **Compilation errors** - MUST be fixed
- **Test failures** - MUST be fixed
- **Quarkus startup errors** - MUST be fixed
- **Keycloak errors** - MUST be investigated
- **Warnings in console output** - Check against acceptable warnings
- **Warnings in Quarkus logs** - Check against acceptable warnings
- **Warnings in Keycloak logs** - Check against acceptable warnings
- **Oddities in logs** - Unexpected behavior, strange timing, unusual messages

**CRITICAL LOG ANALYSIS**:
- Read the complete Maven output from the build log file (e.g., `target/maven-build.log`)
- Logs are copied to the `target` directory
- Read Quarkus application logs carefully
- Read Keycloak container logs carefully
- Look for:
  - Stack traces
  - ERROR level messages
  - WARN level messages
  - Connection issues
  - Timeout issues
  - Authentication/Authorization failures
  - Configuration problems

**WARNING HANDLING**:
1. Parse warnings from skill workflow output
2. For each warning or oddity found, check if it's listed in acceptable warnings from Step 1
3. If NOT in acceptable warnings list:
   - **STOP and ASK USER** whether this warning is acceptable
   - **WAIT for user response** before continuing
   - If user says it's acceptable, add it using run-configuration skill:
     ```
     Skill: cui-utilities:claude-run-configuration
     Workflow: Update Configuration
     Action: add-entry
     Field: commands.verify-integration-tests.acceptable_warnings
     Value: "<warning-pattern>"
     ```
   - If user says it needs fixing, proceed to Step 4
4. Only continue to Step 4 if there are issues that need fixing

### Step 4: Fix Issues
1. Fix all errors, failures, and warnings that need fixing
2. If fix is in a different module (not the integration-tests module):
   - Rebuild that module using builder skill:
     ```
     Skill: builder:builder-maven-rules
     Workflow: Execute Maven Build
     Parameters:
       goals: clean install
       module: <module-name>
       output_mode: errors
     ```
   - Then rebuild any dependent modules if needed
3. For each code change made, **REPEAT THE ENTIRE PROCESS** (go back to Step 2)
4. Continue until no more changes are needed

### Step 5: Post-Verification (Only if Code Changes Were Made)
If ANY code changes were made during the fixing process:
1. Run `/builder-build-and-fix system=maven` to ensure the entire project still builds correctly
2. This ensures changes didn't break other modules

### Step 6: Record Execution and Display Summary Report
Once the integration tests complete successfully with no changes needed:

1. Record execution result:
   ```
   Skill: cui-utilities:claude-run-configuration
   Workflow: Update Configuration
   Field: commands.verify-integration-tests.last_execution
   Value: {"date": "<current-date>", "status": "SUCCESS", "duration_ms": <total-duration>}
   ```

2. Display a summary report to the user:
   - Build status (from skill workflow)
   - Number of iterations performed
   - Issues found and fixed
   - Warnings handled
   - Execution time (from skill workflow metrics)
   - Any items added to acceptable warnings
   - Whether /builder-build-and-fix was run

## CRITICAL RULES

- **USE builder:builder-maven-rules skill workflow** - never call `./mvnw` directly
- **PARSE skill output** - extract status, issues, and metrics from structured JSON
- **ALWAYS read and analyze Quarkus/Keycloak logs** from target directory
- **ALWAYS ask user** before adding new acceptable warnings
- **ALWAYS wait for user response** before continuing after asking
- **ALWAYS repeat** the process after making code changes
- **RUN /builder-build-and-fix** if any code changes were made
- **USE claude-run-configuration skill** for all configuration access

## Usage

Simply invoke: `/verify-integration-tests`

No arguments needed. The command will automatically detect project root and execute the workflow.
