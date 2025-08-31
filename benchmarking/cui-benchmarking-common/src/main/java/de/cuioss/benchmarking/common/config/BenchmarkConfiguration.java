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
package de.cuioss.benchmarking.common.config;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.TimeValue;

import static de.cuioss.benchmarking.common.repository.TokenRepositoryConfig.requireProperty;

/**
 * Modern configuration API for JMH benchmarks.
 * Provides a fluent builder pattern and immutable configuration objects.
 * Combines JMH runtime configuration with report generation configuration.
 * 
 * <p>Example usage:
 * <pre>{@code
 * var config = BenchmarkConfiguration.builder()
 *     .withReportConfig(ReportConfiguration.builder()
 *         .withBenchmarkType(BenchmarkType.MICRO)
 *         .withThroughputBenchmarkName("myThroughputTest")
 *         .withLatencyBenchmarkName("myLatencyTest")
 *         .build())
 *     .withForks(2)
 *     .withThreads(10)
 *     .build();
 * 
 * Options jmhOptions = config.toJmhOptions();
 * }</pre>
 * 
 * @since 1.0
 */
public record BenchmarkConfiguration(
ReportConfiguration reportConfig,
String includePattern,
int forks,
int warmupIterations,
int measurementIterations,
TimeValue measurementTime,
TimeValue warmupTime,
int threads,
IntegrationConfiguration integrationConfig
) {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkConfiguration.class);

    /**
     * System property keys for benchmark configuration.
     */
    public static final class Properties {
        public static final String INCLUDE = "jmh.include";
        public static final String FORKS = "jmh.forks";
        public static final String WARMUP_ITERATIONS = "jmh.warmupIterations";
        public static final String MEASUREMENT_ITERATIONS = "jmh.iterations";
        public static final String MEASUREMENT_TIME = "jmh.time";
        public static final String WARMUP_TIME = "jmh.warmupTime";
        public static final String THREADS = "jmh.threads";

        private Properties() {
        }
    }


    /**
     * Creates a configuration builder with system properties for JMH settings.
     * Note: Report configuration must be set explicitly via the builder.
     * 
     * @return a new builder initialized from system properties
     */
    public static Builder builder() {
        return new Builder()
                .withIncludePattern(requireJmhProperty(Properties.INCLUDE, "JMH include pattern"))
                .withForks(requireIntProperty(Properties.FORKS, "JMH fork count"))
                .withWarmupIterations(requireIntProperty(Properties.WARMUP_ITERATIONS, "JMH warmup iterations"))
                .withMeasurementIterations(requireIntProperty(Properties.MEASUREMENT_ITERATIONS, "JMH measurement iterations"))
                .withMeasurementTime(parseTimeValue(requireJmhProperty(Properties.MEASUREMENT_TIME, "JMH measurement time")))
                .withWarmupTime(parseTimeValue(requireJmhProperty(Properties.WARMUP_TIME, "JMH warmup time")))
                .withThreads(parseThreadCount(requireJmhProperty(Properties.THREADS, "JMH thread count")))
        ;
    }

    /**
     * Creates a default configuration.
     * 
     * @return a new builder with default values
     */
    public static Builder defaults() {
        return new Builder();
    }

    /**
     * Creates a builder from this configuration.
     * 
     * @return a new builder initialized with this configuration's values
     */
    public Builder toBuilder() {
        var builder = new Builder()
                .withReportConfig(reportConfig)
                .withIncludePattern(includePattern)
                .withForks(forks)
                .withWarmupIterations(warmupIterations)
                .withMeasurementIterations(measurementIterations)
                .withMeasurementTime(measurementTime)
                .withWarmupTime(warmupTime)
                .withThreads(threads);

        if (integrationConfig != null) {
            builder.withIntegrationConfig(integrationConfig);
        }
        return builder;
    }


    /**
     * Checks if this configuration includes integration configuration.
     * Integration configuration is required for integration benchmarks.
     * 
     * @return true if integration configuration is present, false otherwise
     */
    public boolean hasIntegrationConfig() {
        return integrationConfig != null;
    }

    /**
     * Convenience methods for accessing nested report configuration.
     */
    public BenchmarkType benchmarkType() {
        return reportConfig.benchmarkType();
    }

    public String throughputBenchmarkName() {
        return reportConfig.throughputBenchmarkName();
    }

    public String latencyBenchmarkName() {
        return reportConfig.latencyBenchmarkName();
    }

    public String resultsDirectory() {
        return reportConfig.resultsDirectory();
    }

    public String resultFile() {
        return reportConfig.resultFile();
    }


    private static TimeValue parseTimeValue(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return TimeValue.seconds(1);
        }

        // Check if the last character is a digit (no unit specified)
        char lastChar = timeStr.charAt(timeStr.length() - 1);
        if (Character.isDigit(lastChar)) {
            // No unit specified, assume seconds
            return TimeValue.seconds(Long.parseLong(timeStr));
        }

        // Parse value and unit
        long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));

        return switch (lastChar) {
            case 's' -> TimeValue.seconds(value);
            case 'm' -> TimeValue.minutes(value);
            case 'h' -> TimeValue.hours(value);
            default -> {
                LOGGER.warn("Unknown time unit '{}' in '{}', defaulting to seconds", lastChar, timeStr);
                yield TimeValue.seconds(value);
            }
        };
    }

    private static int parseThreadCount(String threads) {
        if ("MAX".equalsIgnoreCase(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        if ("HALF".equalsIgnoreCase(threads)) {
            return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        }
        try {
            return Integer.parseInt(threads);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JMH thread count must be a valid integer or 'MAX' or 'HALF', but got: %s. Set system property: %s".formatted(
                    threads, Properties.THREADS));
        }
    }

    private static String requireJmhProperty(String key, String description) {
        return requireProperty(System.getProperty(key), description, key);
    }

    private static int requireIntProperty(String key, String description) {
        String value = requireProperty(System.getProperty(key), description, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s must be a valid integer, but got: %s. Set system property: %s".formatted(
                    description, value, key));
        }
    }

    /**
     * Builder for BenchmarkConfiguration.
     */
    public static class Builder {
        private ReportConfiguration reportConfig;
        private ReportConfiguration.Builder reportConfigBuilder;
        private String includePattern;
        private int forks;
        private int warmupIterations;
        private int measurementIterations;
        private TimeValue measurementTime;
        private TimeValue warmupTime;
        private int threads;
        private IntegrationConfiguration integrationConfig;

        /**
         * Sets the complete report configuration.
         */
        public Builder withReportConfig(ReportConfiguration config) {
            this.reportConfig = config;
            this.reportConfigBuilder = null; // Clear builder if direct config is set
            return this;
        }

        /**
         * Convenience method to set benchmark type for report config.
         * Creates a report config builder if needed.
         */
        public Builder withBenchmarkType(BenchmarkType type) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withBenchmarkType(type);
            return this;
        }

        /**
         * Convenience method to set throughput benchmark name.
         * Creates a report config builder if needed.
         */
        public Builder withThroughputBenchmarkName(String name) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withThroughputBenchmarkName(name);
            return this;
        }

        /**
         * Convenience method to set latency benchmark name.
         * Creates a report config builder if needed.
         */
        public Builder withLatencyBenchmarkName(String name) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withLatencyBenchmarkName(name);
            return this;
        }

        /**
         * Convenience method to set results directory.
         * Creates a report config builder if needed.
         */
        public Builder withResultsDirectory(String dir) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultsDirectory(dir);
            return this;
        }

        /**
         * Convenience method to set result file.
         * Creates a report config builder if needed.
         */
        public Builder withResultFile(String file) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultFile(file);
            return this;
        }

        /**
         * Convenience method to set result format.
         * Creates a report config builder if needed.
         */
        public Builder withResultFormat(ResultFormatType format) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultFormat(format);
            return this;
        }

        private void ensureReportConfigBuilder() {
            if (reportConfigBuilder == null && reportConfig == null) {
                reportConfigBuilder = ReportConfiguration.builder();
            } else if (reportConfigBuilder == null) {
                // reportConfig must be non-null here
                reportConfigBuilder = reportConfig.toBuilder();
                reportConfig = null; // Will be rebuilt from builder
            }
        }

        public Builder withIncludePattern(String pattern) {
            this.includePattern = pattern;
            return this;
        }

        public Builder withForks(int forks) {
            this.forks = forks;
            return this;
        }

        public Builder withWarmupIterations(int iterations) {
            this.warmupIterations = iterations;
            return this;
        }

        public Builder withMeasurementIterations(int iterations) {
            this.measurementIterations = iterations;
            return this;
        }

        public Builder withMeasurementTime(TimeValue time) {
            this.measurementTime = time;
            return this;
        }

        public Builder withWarmupTime(TimeValue time) {
            this.warmupTime = time;
            return this;
        }

        public Builder withThreads(int threads) {
            this.threads = threads;
            return this;
        }

        /**
         * Sets the integration configuration for integration benchmarks.
         * This configuration contains URLs needed for integration testing.
         */
        public Builder withIntegrationConfig(IntegrationConfiguration config) {
            this.integrationConfig = config;
            return this;
        }

        /**
         * Builds the configuration.
         * 
         * @return the built configuration
         * @throws IllegalArgumentException if required fields are not set
         */
        public BenchmarkConfiguration build() {
            // Build report config if using builder
            ReportConfiguration finalReportConfig = reportConfig;
            if (finalReportConfig == null) {
                if (reportConfigBuilder == null) {
                    throw new IllegalArgumentException("Report configuration must be set");
                }
                finalReportConfig = reportConfigBuilder.build();
            }

            return new BenchmarkConfiguration(
                    finalReportConfig,
                    includePattern,
                    forks,
                    warmupIterations,
                    measurementIterations,
                    measurementTime,
                    warmupTime,
                    threads,
                    integrationConfig
            );
        }
    }
}