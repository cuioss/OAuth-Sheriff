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
package de.cuioss.benchmarking.common.metrics;

import de.cuioss.tools.logging.CuiLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Transforms Prometheus time-series data into the benchmark server metrics format
 * as defined in benchmark-metrics.adoc.
 *
 * @since 1.0
 */
public class BenchmarkMetricsTransformer {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkMetricsTransformer.class);

    /**
     * Transforms Prometheus time-series data into benchmark server metrics.
     *
     * @param benchmarkName Name of the benchmark
     * @param startTime Start time of the benchmark
     * @param endTime End time of the benchmark
     * @param timeSeriesData Raw time-series data from Prometheus
     * @return Structured benchmark metrics as defined in requirements
     */
    public Map<String, Object> transformToServerMetrics(
            String benchmarkName,
            Instant startTime,
            Instant endTime,
            Map<String, PrometheusClient.TimeSeries> timeSeriesData) {

        Map<String, Object> result = new LinkedHashMap<>();
        Duration duration = Duration.between(startTime, endTime);

        // Benchmark metadata
        Map<String, Object> benchmark = new LinkedHashMap<>();
        benchmark.put("name", benchmarkName);
        benchmark.put("start_time", startTime.toString());
        benchmark.put("end_time", endTime.toString());
        benchmark.put("duration_seconds", duration.getSeconds());
        result.put("benchmark", benchmark);

        // Resource metrics
        result.put("resources", createResourceMetrics(timeSeriesData));

        // Application metrics
        result.put("application", createApplicationMetrics(timeSeriesData));

        // Optional: Include raw time-series data
        if (includeTimeSeries()) {
            result.put("time_series", createTimeSeriesData(timeSeriesData, duration));
        }

        return result;
    }


    private Map<String, Object> createResourceMetrics(Map<String, PrometheusClient.TimeSeries> data) {
        Map<String, Object> resources = new LinkedHashMap<>();

        // CPU metrics
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("process", createCpuMetrics(data.get("process_cpu_usage"), "Process"));
        cpu.put("system", createCpuMetrics(data.get("system_cpu_usage"), "System"));

        // Get CPU cores
        PrometheusClient.TimeSeries cpuCount = data.get("system_cpu_count");
        if (cpuCount != null && !cpuCount.getValues().isEmpty()) {
            cpu.put("cores_available", (int) cpuCount.getValues().getFirst().getValue());
        } else {
            cpu.put("cores_available", 4); // Default
        }
        resources.put("cpu", cpu);

        // Memory metrics
        resources.put("memory", createMemoryMetrics(data));

        // Thread metrics
        resources.put("threads", createThreadMetrics(data));

        return resources;
    }

    private Map<String, Object> createCpuMetrics(PrometheusClient.TimeSeries series, String type) {
        Map<String, Object> cpuMetrics = new LinkedHashMap<>();

        if (series == null || series.getValues().isEmpty()) {
            cpuMetrics.put("average_percent", 0.0);
            cpuMetrics.put("peak_percent", 0.0);
            cpuMetrics.put("std_dev", 0.0);
            return cpuMetrics;
        }

        List<Double> values = series.getValues().stream()
                .map(dp -> dp.getValue() * 100) // Convert to percentage
                .collect(Collectors.toList());

        Statistics stats = calculateStatistics(values);

        cpuMetrics.put("average_percent", round(stats.mean, 1));
        cpuMetrics.put("peak_percent", round(stats.max, 1));
        cpuMetrics.put("std_dev", round(stats.stdDev, 2));

        if ("Process".equals(type)) {
            Map<String, Object> percentiles = new LinkedHashMap<>();
            percentiles.put("p50", round(stats.p50, 1));
            percentiles.put("p75", round(stats.p75, 1));
            percentiles.put("p90", round(stats.p90, 1));
            percentiles.put("p99", round(stats.p99, 1));
            cpuMetrics.put("percentiles", percentiles);
        }

        return cpuMetrics;
    }

    private Map<String, Object> createMemoryMetrics(Map<String, PrometheusClient.TimeSeries> data) {
        Map<String, Object> memory = new LinkedHashMap<>();

        // Heap memory
        Map<String, Object> heap = new LinkedHashMap<>();
        PrometheusClient.TimeSeries heapUsed = findHeapMemory(data.get("jvm_memory_used_bytes"));
        if (heapUsed != null && !heapUsed.getValues().isEmpty()) {
            List<Double> heapMbValues = heapUsed.getValues().stream()
                    .map(dp -> dp.getValue() / 1024 / 1024) // Convert to MB
                    .collect(Collectors.toList());

            Statistics stats = calculateStatistics(heapMbValues);
            heap.put("average_mb", round(stats.mean, 1));
            heap.put("peak_mb", round(stats.max, 1));
            heap.put("final_mb", round(heapMbValues.getLast(), 1));
        } else {
            heap.put("average_mb", 0.0);
            heap.put("peak_mb", 0.0);
            heap.put("final_mb", 0.0);
        }
        memory.put("heap", heap);

        // GC metrics
        Map<String, Object> gc = new LinkedHashMap<>();
        PrometheusClient.TimeSeries gcOverhead = data.get("jvm_gc_overhead");
        if (gcOverhead != null && !gcOverhead.getValues().isEmpty()) {
            double avgOverhead = gcOverhead.getValues().stream()
                    .mapToDouble(PrometheusClient.DataPoint::getValue)
                    .average().orElse(0.0);
            gc.put("overhead_percent", round(avgOverhead * 100, 2));
        } else {
            gc.put("overhead_percent", 0.0);
        }
        memory.put("gc", gc);

        return memory;
    }

    private Map<String, Object> createThreadMetrics(Map<String, PrometheusClient.TimeSeries> data) {
        Map<String, Object> threads = new LinkedHashMap<>();

        PrometheusClient.TimeSeries liveThreads = data.get("jvm_threads_live_threads");
        if (liveThreads != null && !liveThreads.getValues().isEmpty()) {
            Statistics stats = calculateStatistics(
                    liveThreads.getValues().stream()
                            .map(PrometheusClient.DataPoint::getValue)
                            .collect(Collectors.toList())
            );
            threads.put("average", (int) stats.mean);
            threads.put("peak", (int) stats.max);
            threads.put("final", (int) liveThreads.getValues().get(liveThreads.getValues().size() - 1).getValue());
        } else {
            threads.put("average", 0);
            threads.put("peak", 0);
            threads.put("final", 0);
        }

        PrometheusClient.TimeSeries daemonThreads = data.get("jvm_threads_daemon_threads");
        if (daemonThreads != null && !daemonThreads.getValues().isEmpty()) {
            threads.put("daemon", (int) daemonThreads.getValues().getFirst().getValue());
        } else {
            threads.put("daemon", 0);
        }

        return threads;
    }


    private Map<String, Object> createApplicationMetrics(Map<String, PrometheusClient.TimeSeries> data) {
        Map<String, Object> application = new LinkedHashMap<>();

        Map<String, Object> jwtValidations = new LinkedHashMap<>();

        // JWT validation metrics
        PrometheusClient.TimeSeries jwtSuccess = data.get("cui_jwt_validation_success_operations_total");
        double totalSuccess = 0;
        double cacheHits = 0;

        if (jwtSuccess != null && !jwtSuccess.getValues().isEmpty()) {
            totalSuccess = getDeltaValue(jwtSuccess);
            // Check if it's a cache hit based on metric labels
            if (jwtSuccess.getLabels().containsKey("event_type") &&
                    jwtSuccess.getLabels().get("event_type").contains("CACHE_HIT")) {
                cacheHits = totalSuccess;
            }
        }

        PrometheusClient.TimeSeries jwtErrors = data.get("cui_jwt_validation_errors_total");
        double totalErrors = jwtErrors != null ? getDeltaValue(jwtErrors) : 0;

        double total = totalSuccess + totalErrors;
        jwtValidations.put("total", (int) total);
        jwtValidations.put("success", (int) totalSuccess);
        jwtValidations.put("errors", (int) totalErrors);
        jwtValidations.put("cache_hits", (int) cacheHits);
        jwtValidations.put("cache_hit_rate_percent", total > 0 ? round(cacheHits / total * 100, 1) : 0.0);

        // JWT validation timing
        PrometheusClient.TimeSeries jwtDurationSum = data.get("cui_jwt_bearer_token_validation_seconds_sum");
        PrometheusClient.TimeSeries jwtDurationCount = data.get("cui_jwt_bearer_token_validation_seconds_count");
        if (jwtDurationSum != null && jwtDurationCount != null) {
            double sum = getDeltaValue(jwtDurationSum);
            double count = getDeltaValue(jwtDurationCount);
            if (count > 0) {
                jwtValidations.put("average_validation_time_ms", round(sum / count * 1000, 2));
            }
        }

        application.put("jwt_validations", jwtValidations);

        return application;
    }

    private Map<String, Object> createTimeSeriesData(Map<String, PrometheusClient.TimeSeries> data, Duration duration) {
        Map<String, Object> timeSeries = new LinkedHashMap<>();

        timeSeries.put("sampling_interval_seconds", 2);
        timeSeries.put("duration_seconds", duration.getSeconds());

        Map<String, List<Double>> metrics = new LinkedHashMap<>();

        // Include key time-series data
        if (data.containsKey("process_cpu_usage")) {
            metrics.put("cpu_usage", extractTimeSeriesValues(data.get("process_cpu_usage"), 100));
        }

        if (data.containsKey("jvm_memory_used_bytes")) {
            PrometheusClient.TimeSeries heapMemory = findHeapMemory(data.get("jvm_memory_used_bytes"));
            if (heapMemory != null) {
                metrics.put("memory_usage_mb", extractTimeSeriesValues(heapMemory, 1.0 / 1024 / 1024));
            }
        }

        timeSeries.put("metrics", metrics);

        return timeSeries;
    }

    private List<Double> extractTimeSeriesValues(PrometheusClient.TimeSeries series, double multiplier) {
        if (series == null || series.getValues().isEmpty()) {
            return List.of();
        }
        return series.getValues().stream()
                .map(dp -> round(dp.getValue() * multiplier, 2))
                .collect(Collectors.toList());
    }

    private PrometheusClient.TimeSeries findHeapMemory(PrometheusClient.TimeSeries memorySeries) {
        if (memorySeries == null) {
            return null;
        }
        // Check if this is heap memory based on labels
        if (memorySeries.getLabels().containsKey("area") &&
                "heap".equals(memorySeries.getLabels().get("area"))) {
            return memorySeries;
        }
        return null;
    }

    private double getDeltaValue(PrometheusClient.TimeSeries series) {
        if (series == null || series.getValues().isEmpty()) {
            return 0;
        }
        List<PrometheusClient.DataPoint> values = series.getValues();
        if (values.size() == 1) {
            return values.getFirst().getValue();
        }
        // For counters, get the difference between last and first
        return values.getLast().getValue() - values.getFirst().getValue();
    }

    private Statistics calculateStatistics(List<Double> values) {
        if (values.isEmpty()) {
            return new Statistics();
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        Statistics stats = new Statistics();
        stats.mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        stats.min = sorted.getFirst();
        stats.max = sorted.getLast();

        if (values.size() > 1) {
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - stats.mean, 2))
                    .average().orElse(0);
            stats.stdDev = Math.sqrt(variance);
        }

        stats.p50 = getPercentile(sorted, 50);
        stats.p75 = getPercentile(sorted, 75);
        stats.p90 = getPercentile(sorted, 90);
        stats.p99 = getPercentile(sorted, 99);

        return stats;
    }

    private double getPercentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (sorted.size() * percentile) / 100;
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index);
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private boolean includeTimeSeries() {
        // Configuration option to include raw time-series data
        return false; // Can be made configurable
    }

    private static class Statistics {
        double mean = 0;
        double min = 0;
        double max = 0;
        double stdDev = 0;
        double p50 = 0;
        double p75 = 0;
        double p90 = 0;
        double p99 = 0;
    }
}