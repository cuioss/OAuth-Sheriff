/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.benchmarking.common.report;

import static de.cuioss.benchmarking.common.report.ReportConstants.ERRORS;

/**
 * Immutable record containing all computed benchmark metrics.
 * This is the central data structure shared across all report generators.
 */
public record BenchmarkMetrics(
String throughputBenchmarkName,
String latencyBenchmarkName,
double throughput,
double latency,
double performanceScore,
String performanceGrade
) {
    public BenchmarkMetrics {
        if (throughputBenchmarkName == null || throughputBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(ERRORS.THROUGHPUT_NAME_REQUIRED);
        }
        if (latencyBenchmarkName == null || latencyBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(ERRORS.LATENCY_NAME_REQUIRED);
        }
        if (throughput <= 0) {
            throw new IllegalArgumentException(ERRORS.THROUGHPUT_POSITIVE + throughput);
        }
        if (latency <= 0) {
            throw new IllegalArgumentException(ERRORS.LATENCY_POSITIVE + latency);
        }
        if (performanceScore < 0) {
            throw new IllegalArgumentException(ERRORS.SCORE_NON_NEGATIVE + performanceScore);
        }
    }
}