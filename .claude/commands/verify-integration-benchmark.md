# Verify Integration Benchmark Command

Execute WRK-based integration benchmarks with comprehensive result analysis and Quarkus/Keycloak log validation.

## WORKFLOW INSTRUCTIONS

### Step 1: Read Configuration

Load acceptable warnings from run configuration:

```
Skill: cui-utilities:claude-run-configuration
Workflow: Read Configuration
Field: commands.verify-integration-benchmark.acceptable_warnings
```

If field doesn't exist, use empty array `[]`.

### Step 2: Execute Maven Build via Builder Skill

Delegate build execution to the builder-maven-rules skill workflow:

```
Skill: builder:builder-maven-rules
Workflow: Execute Maven Build
Parameters:
  goals: clean verify -Pbenchmark
  module: benchmarking/benchmark-integration-wrk
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
- **Container startup errors** - MUST be fixed
- **WRK execution errors** - MUST be fixed
- **Quarkus startup errors** - MUST be fixed
- **Keycloak errors** - MUST be investigated
- **Warnings in console output** - Check against acceptable warnings
- **Warnings in Quarkus logs** - Check against acceptable warnings
- **Warnings in Keycloak logs** - Check against acceptable warnings
- **Oddities in logs** - Unexpected behavior, strange timing, unusual messages

**CRITICAL LOG ANALYSIS**:
- Read the complete Maven output from the build log file (e.g., `target/maven-build.log`)
- Logs are in the `benchmarking/benchmark-integration-wrk/target/` directory
- Read `quarkus-logs.txt` carefully
- Read Keycloak container logs if available in target directory
- Look for:
  - Stack traces
  - ERROR level messages
  - WARN level messages
  - Connection issues
  - Timeout issues
  - Authentication/Authorization failures
  - Configuration problems
  - WRK connection errors or timeouts

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
     Field: commands.verify-integration-benchmark.acceptable_warnings
     Value: "<warning-pattern>"
     ```
   - If user says it needs fixing, proceed to Step 5
4. Only continue to Step 5 if there are issues that need fixing

### Step 4: Validate Benchmark Results
**CRITICAL BENCHMARK RESULTS VALIDATION**:
1. Check that `benchmarking/benchmark-integration-wrk/target/benchmark-results` directory exists
2. List all files in the benchmark-results directory
3. Validate the following **required benchmark output files**:
   - **`wrk-health-output.txt`** - Raw WRK output for health endpoint - MUST exist and be non-empty
   - **`wrk-health-results.json`** - Processed JSON report for health benchmark - MUST exist and contain valid JSON
   - **`wrk-jwt-output.txt`** - Raw WRK output for JWT endpoint - MUST exist and be non-empty
   - **`wrk-jwt-results.json`** - Processed JSON report for JWT benchmark - MUST exist and contain valid JSON
   - **`quarkus-logs.txt`** - Application logs from benchmark run - MUST exist (may contain warnings to analyze)
4. Analyze benchmark results for:
   - **All benchmarks completed successfully** - No connection errors, no timeout errors
   - **Performance metrics present** - requests_per_second, latency, total_requests, etc.
   - **Reasonable performance numbers** - Not suspiciously low (e.g., <1000 req/s suggests issues)
   - **Error counts** - Should be 0 or very low (document if non-zero)
5. Read and validate `wrk-health-results.json`:
   - Use `jq '.performance'` or `grep` to extract key metrics
   - Verify requests_per_second is reasonable (typically 20,000+ for health checks)
   - Check latency_avg_ms is low (typically <2ms for health checks)
   - Ensure errors field is 0 or explain if not
6. Read and validate `wrk-jwt-results.json`:
   - Verify requests_per_second is reasonable (typically 15,000+ for JWT validation)
   - Check latency_avg_ms is reasonable (typically 1-5ms for JWT validation)
   - Ensure errors field is 0 or explain if not
7. Read and analyze `quarkus-logs.txt`:
   - Scan for ERROR messages
   - Scan for WARN messages
   - Check against acceptable warnings list
   - Look for startup issues, authentication problems, or unexpected behavior

**BENCHMARK RESULTS REQUIREMENTS**:
- `wrk-health-output.txt` must exist and contain WRK benchmark output
- `wrk-health-results.json` must exist and contain valid JSON with performance metrics
- `wrk-jwt-output.txt` must exist and contain WRK benchmark output
- `wrk-jwt-results.json` must exist and contain valid JSON with performance metrics
- `quarkus-logs.txt` must exist (warnings analyzed but file must be present)
- All benchmark files must show successful execution with reasonable performance numbers
- Error counts should be 0 or documented/explained

### Step 5: Fix Issues
1. Fix all errors, failures, and warnings that need fixing
2. If fix is in a different module (not the benchmark-integration-wrk module):
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

### Step 6: Post-Verification (Only if Code Changes Were Made)
If ANY code changes were made during the fixing process:
1. Run `/builder-build-and-fix system=maven` to ensure the entire project still builds correctly
2. This ensures changes didn't break other modules

### Step 7: Record Execution and Display Summary Report
Once the benchmarks complete successfully with no changes needed:

1. Record execution result:
   ```
   Skill: cui-utilities:claude-run-configuration
   Workflow: Update Configuration
   Field: commands.verify-integration-benchmark.last_execution
   Value: {"date": "<current-date>", "status": "SUCCESS", "duration_ms": <total-duration>}
   ```

2. Display a summary report to the user:
   - Build status (from skill workflow)
   - Number of iterations performed
   - Issues found and fixed
   - Warnings handled
   - Benchmark results summary:
     - Health endpoint performance (req/s, latency)
     - JWT endpoint performance (req/s, latency)
     - Error counts
     - Location of result files
   - Execution time (from skill workflow metrics)
   - Any items added to acceptable warnings
   - Whether /builder-build-and-fix was run

## CRITICAL RULES

- **USE builder:builder-maven-rules skill workflow** - never call `./mvnw` directly
- **PARSE skill output** - extract status, issues, and metrics from structured JSON
- **ALWAYS validate benchmark results** in `benchmarking/benchmark-integration-wrk/target/benchmark-results`
- **ALWAYS check that all required result files exist**
- **ALWAYS read and analyze quarkus-logs.txt** for errors and warnings
- **ALWAYS ask user** before adding new acceptable warnings
- **ALWAYS wait for user response** before continuing after asking
- **ALWAYS repeat** the process after making code changes
- **RUN /builder-build-and-fix** if any code changes were made
- **USE claude-run-configuration skill** for all configuration access

## Usage

Simply invoke: `/verify-integration-benchmark`

No arguments needed. The command will automatically detect project root and execute the workflow.
