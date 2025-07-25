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
package de.cuioss.jwt.validation.metrics;

import de.cuioss.tools.concurrent.StripedRingBuffer;
import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;
import java.util.Set;

/**
 * Provides high-metrics, thread-safe monitoring of JWT validation pipeline metrics.
 * <p>
 * This class measures execution times for different stages of JWT validation with microsecond
 * precision. It maintains a configurable rolling window of recent measurements for each
 * pipeline step and provides average calculation capabilities.
 * <p>
 * <strong>Design Principles:</strong>
 * <ul>
 *   <li><strong>Thread-Safe:</strong> All operations are lock-free using atomic operations</li>
 *   <li><strong>Zero Runtime Impact:</strong> Optimized for minimal overhead during measurement</li>
 *   <li><strong>Microsecond Precision:</strong> All measurements recorded in microseconds</li>
 *   <li><strong>Rolling Window:</strong> Maintains configurable number of recent samples</li>
 *   <li><strong>Pipeline Aware:</strong> Measures each validation step separately</li>
 * </ul>
 * <p>
 * <strong>Implementation:</strong>
 * Uses a striped ring buffer design with multiple independent buffers to minimize contention
 * between threads. Each measurement type maintains its own set of striped buffers for optimal
 * metrics in high-concurrency environments.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * // Create monitor with default window size (100 samples)
 * TokenValidatorMonitor monitor = new TokenValidatorMonitor();
 *
 * // Record a measurement
 * long startNanos = System.nanoTime();
 * // ... perform signature validation ...
 * long durationNanos = System.nanoTime() - startNanos;
 * monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, durationNanos);
 *
 * // Get average for analysis
 * Duration avgSignatureTime = monitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION);
 * </pre>
 * <p>
 * This implementation is structured to simplify later integration with micrometer-based
 * metrics systems but does not create any dependency on external monitoring frameworks.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenValidatorMonitor {


    /**
     * Striped ring buffers for each measurement type.
     */
    private final StripedRingBuffer[] measurementBuffers;

    /**
     * Set of enabled measurement types. Only measurements for these types
     * will be recorded; others will be no-op operations.
     */
    @Getter
    private final Set<MeasurementType> enabledTypes;

    /**
     * Creates a new metrics monitor with specified window size and enabled measurement types.
     * <p>
     * Package-private constructor to be used from TokenValidatorMonitorConfig.
     *
     * @param windowSize number of samples to maintain per measurement type (must be positive)
     * @param enabledTypes set of measurement types to monitor (others will be no-op)
     * @throws IllegalArgumentException if windowSize is not positive
     */
    TokenValidatorMonitor(int windowSize, @NonNull Set<MeasurementType> enabledTypes) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive, got: " + windowSize);
        }

        this.enabledTypes = Set.copyOf(enabledTypes); // Defensive copy

        // Initialize one striped ring buffer per measurement type
        this.measurementBuffers = new StripedRingBuffer[MeasurementType.values().length];
        for (int i = 0; i < measurementBuffers.length; i++) {
            // Only create buffers for enabled types to save memory
            if (this.enabledTypes.contains(MeasurementType.values()[i])) {
                measurementBuffers[i] = new StripedRingBuffer(windowSize);
            }
        }
    }

    /**
     * Records a metrics measurement for the specified pipeline step.
     * <p>
     * This method is designed for maximum metrics with minimal overhead.
     * The measurement is recorded in microseconds and stored in a lock-free
     * ring buffer for later analysis.
     * <p>
     * If the measurement type is not enabled in this monitor's configuration,
     * this method performs a no-op operation for optimal performance.
     *
     * @param measurementType the type of measurement being recorded
     * @param durationNanos   the duration in nanoseconds (will be converted to microseconds)
     */
    public void recordMeasurement(@NonNull MeasurementType measurementType, long durationNanos) {
        // Early return for disabled measurement types (no-op)
        if (!enabledTypes.contains(measurementType)) {
            return;
        }

        StripedRingBuffer buffer = measurementBuffers[measurementType.ordinal()];
        if (buffer != null) { // Additional safety check
            // Convert nanoseconds to microseconds for consistent storage
            long durationMicros = Math.max(0, durationNanos / 1000);
            buffer.recordMeasurement(durationMicros);
        }
    }

    /**
     * Calculates the average duration for the specified measurement type.
     * <p>
     * The average is calculated from all current samples in the rolling window.
     * If no measurements have been recorded or the measurement type is disabled,
     * returns Duration.ZERO.
     *
     * @param measurementType the type of measurement to analyze
     * @return the average duration, or Duration.ZERO if no measurements exist or type is disabled
     */
    public Duration getAverageDuration(@NonNull MeasurementType measurementType) {
        StripedRingBuffer buffer = measurementBuffers[measurementType.ordinal()];
        if (buffer == null) {
            return Duration.ZERO; // Measurement type not enabled
        }

        long averageMicros = buffer.getAverage();
        // Convert microseconds back to Duration
        return Duration.ofNanos(averageMicros * 1000);
    }

    /**
     * Gets the current sample count for the specified measurement type.
     *
     * @param measurementType the type of measurement to check
     * @return the number of samples currently recorded (up to window size), or 0 if type is disabled
     */
    public int getSampleCount(@NonNull MeasurementType measurementType) {
        StripedRingBuffer buffer = measurementBuffers[measurementType.ordinal()];
        return buffer != null ? buffer.getSampleCount() : 0;
    }

    /**
     * Resets all measurements for the specified type.
     *
     * @param measurementType the type of measurement to reset
     */
    public void reset(@NonNull MeasurementType measurementType) {
        StripedRingBuffer buffer = measurementBuffers[measurementType.ordinal()];
        if (buffer != null) {
            buffer.reset();
        }
    }

    /**
     * Resets all measurements for all enabled types.
     */
    public void resetAll() {
        for (StripedRingBuffer buffer : measurementBuffers) {
            if (buffer != null) {
                buffer.reset();
            }
        }
    }

}