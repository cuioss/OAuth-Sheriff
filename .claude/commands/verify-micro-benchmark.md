# Verify Micro Benchmark Command

Execute micro-benchmarks with comprehensive JMH results analysis and validation.

## WORKFLOW INSTRUCTIONS

### Step 1: Read Configuration

Load acceptable warnings from run configuration:

```
Skill: cui-utilities:claude-run-configuration
Workflow: Read Configuration
Field: commands.verify-micro-benchmark.acceptable_warnings
```

If field doesn't exist, use empty array `[]`.

### Step 2: Execute Maven Build via Builder Skill

Delegate build execution to the builder-maven-rules skill workflow:

```
Skill: builder:builder-maven-rules
Workflow: Execute Maven Build
Parameters:
  goals: clean verify -Pbenchmark
  module: benchmarking/benchmark-core
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

### Step 3: Analyze Build Output
Thoroughly analyze the Maven output for:
- **Compilation errors** - MUST be fixed
- **Test failures** - MUST be fixed
- **JMH benchmark errors** - MUST be fixed
- **Warnings in console output** - Check against acceptable warnings

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
     Field: commands.verify-micro-benchmark.acceptable_warnings
     Value: "<warning-pattern>"
     ```
   - If user says it needs fixing, proceed to Step 4
4. Only continue to Step 4 if there are issues that need fixing

### Step 4: Validate Benchmark Results
**CRITICAL BENCHMARK RESULTS VALIDATION**:
1. Check that `benchmarking/benchmark-core/target/benchmark-results` directory exists
2. List all files in the benchmark-results directory
3. Validate the following **required benchmark output files**:
   - **`micro-result.json`** - JMH benchmark results in JSON format - MUST exist and contain valid benchmark data
   - **`jwt-validation-metrics.json`** - Library-specific performance metrics - MUST exist and contain valid JSON
   - **`gh-pages-ready/data/benchmark-data.json`** - Processed benchmark data for documentation/visualization - MUST exist and contain valid JSON with metadata, overview, benchmarks array, and chartData
4. Analyze benchmark results for:
   - **All benchmarks completed successfully** - No ERROR or FAILURE markers
   - **Performance metrics present** - Score, error margin, operations/second, etc.
   - **No anomalies** - Unusually low/high scores that might indicate problems
   - **Warm-up iterations completed** - JMH warm-up phase succeeded
   - **Measurement iterations completed** - JMH measurement phase succeeded
5. Read and validate `micro-result.json`:
   - Use `grep -E '"benchmark"|"primaryMetric"|"score"|"scoreError"'` to extract key metrics
   - Verify all expected benchmarks are present
   - Check that all scores are positive non-zero values
   - Ensure error margins are reasonable (not infinite or NaN)
6. Validate `jwt-validation-metrics.json`:
   - Ensure it contains performance breakdown by validation step
   - Check for metrics like: complete_validation, claims_validation, signature_validation, etc.
   - Verify all timing values (p50_us, p95_us, p99_us) are present and reasonable
7. Validate `gh-pages-ready/data/benchmark-data.json`:
   - Verify it contains required top-level keys: metadata, overview, benchmarks, chartData, trends
   - Check metadata has timestamp, displayTimestamp, benchmarkType, reportVersion
   - Verify overview section has throughput, latency, performanceScore, performanceGrade
   - Confirm benchmarks array contains all benchmark results with scores and percentiles
   - Ensure chartData has properly formatted data for visualization

**BENCHMARK RESULTS REQUIREMENTS**:
- `micro-result.json` must exist and contain valid JSON with JMH benchmark results
- `jwt-validation-metrics.json` must exist and contain valid JSON with library metrics
- `gh-pages-ready/data/benchmark-data.json` must exist and contain valid JSON with complete structure
- All benchmark files must be non-empty
- Results must show successful benchmark execution with valid performance numbers

### Step 5: Fix Issues
1. Fix all errors, failures, and warnings that need fixing
2. If fix is in a different module (not the benchmark-core module):
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
   Field: commands.verify-micro-benchmark.last_execution
   Value: {"date": "<current-date>", "status": "SUCCESS", "duration_ms": <total-duration>}
   ```

2. Display a summary report to the user:
   - Build status (from skill workflow)
   - Number of iterations performed
   - Issues found and fixed
   - Warnings handled
   - Benchmark results summary (number of benchmarks, location of results)
   - Execution time (from skill workflow metrics)
   - Any items added to acceptable warnings
   - Whether /builder-build-and-fix was run

## CRITICAL RULES

- **USE builder:builder-maven-rules skill workflow** - never call `./mvnw` directly
- **PARSE skill output** - extract status, issues, and metrics from structured JSON
- **ALWAYS validate benchmark results** in `benchmarking/benchmark-core/target/benchmark-results`
- **ALWAYS check that benchmark files exist and are non-empty**
- **ALWAYS ask user** before adding new acceptable warnings
- **ALWAYS wait for user response** before continuing after asking
- **ALWAYS repeat** the process after making code changes
- **RUN /builder-build-and-fix** if any code changes were made
- **USE claude-run-configuration skill** for all configuration access

## Usage

Simply invoke: `/verify-micro-benchmark`

No arguments needed. The command will automatically detect project root and execute the workflow.
