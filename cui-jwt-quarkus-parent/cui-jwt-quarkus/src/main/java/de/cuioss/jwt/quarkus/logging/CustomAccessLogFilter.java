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
package de.cuioss.jwt.quarkus.logging;

import de.cuioss.jwt.quarkus.config.AccessLogFilterConfig;
import de.cuioss.jwt.quarkus.config.AccessLogFilterConfigResolver;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;

/**
 * Custom HTTP access log filter with configurable status code and path filtering.
 * This filter provides more granular control than Quarkus built-in access logging,
 * allowing filtering by HTTP status codes and URL patterns.
 *
 * Control via enabled flag: cui.http.access-log.filter.enabled=true
 *
 * Features:
 * - Uses INFO level logging
 * - Filter by HTTP status code ranges
 * - Include/exclude specific status codes
 * - Include/exclude URL path patterns
 * - Configurable log format
 * - Performance optimized with cached disabled state
 */
@Provider
@ApplicationScoped
@RegisterForReflection
public class CustomAccessLogFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String REQUEST_START_TIME = "cui.access-log.start-time";
    private static final CuiLogger LOGGER = new CuiLogger(CustomAccessLogFilter.class);


    private final AccessLogFilterConfig config;
    private final List<PathMatcher> includePathMatchers;
    private final List<PathMatcher> excludePathMatchers;
    private final boolean disabled;

    @Inject
    public CustomAccessLogFilter(AccessLogFilterConfigResolver configResolver) {
        this.config = configResolver.resolveConfig();

        // Cache disabled state for performance optimization
        this.disabled = !config.isEnabled();

        // Get pre-compiled path matchers from config
        this.includePathMatchers = config.getIncludePathMatchers();
        this.excludePathMatchers = config.getExcludePathMatchers();

        LOGGER.info(INFO.CUSTOM_ACCESS_LOG_FILTER_INITIALIZED.format(config));
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Early exit if disabled - performance optimized with cached state
        if (disabled) {
            return;
        }

        // Store request start time for duration calculation
        requestContext.setProperty(REQUEST_START_TIME, Instant.now());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // Early exit if disabled - performance optimized with cached state
        if (disabled) {
            return;
        }

        int statusCode = responseContext.getStatus();
        String path = requestContext.getUriInfo().getPath();

        // Check if this request should be logged
        if (!shouldLog(statusCode, path)) {
            return;
        }

        // Calculate request duration
        Instant startTime = (Instant) requestContext.getProperty(REQUEST_START_TIME);
        long duration = startTime != null ?
                Duration.between(startTime, Instant.now()).toMillis() : -1;

        // Format and log the access entry
        String logEntry = formatLogEntry(requestContext, responseContext, duration);
        LOGGER.info(INFO.ACCESS_LOG_ENTRY.format(logEntry));
    }

    /**
     * Determines if a request should be logged based on status code and path.
     */
    private boolean shouldLog(int statusCode, String path) {
        // Check path inclusion/exclusion first (cheaper than status code checks)
        if (!isPathIncluded(path)) {
            return false;
        }

        // Check if status code is in the configured range
        if (statusCode >= config.getMinStatusCode() && statusCode <= config.getMaxStatusCode()) {
            return true;
        }

        // Check if status code is in the explicit include list
        return config.getIncludeStatusCodes().contains(statusCode);
    }

    /**
     * Checks if a path should be included in logging based on include/exclude patterns.
     */
    private boolean isPathIncluded(String path) {
        // Check exclude patterns first (take precedence)
        Path checkPath = Path.of(path);
        for (PathMatcher matcher : excludePathMatchers) {
            if (matcher.matches(checkPath)) {
                return false;
            }
        }

        // If no include patterns specified, all paths are included
        if (includePathMatchers.isEmpty()) {
            return true;
        }

        // Check include patterns
        for (PathMatcher matcher : includePathMatchers) {
            if (matcher.matches(checkPath)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Formats the log entry according to the configured pattern.
     */
    private String formatLogEntry(ContainerRequestContext requestContext,
            ContainerResponseContext responseContext,
            long duration) {
        String pattern = config.getPattern();
        String remoteAddr = ClientIpExtractor.extractClientIp(requestContext.getHeaders());
        String userAgent = requestContext.getHeaderString("User-Agent");

        return pattern
                .replace("{method}", requestContext.getMethod())
                .replace("{path}", requestContext.getUriInfo().getPath())
                .replace("{status}", String.valueOf(responseContext.getStatus()))
                .replace("{duration}", String.valueOf(duration))
                .replace("{remoteAddr}", remoteAddr)
                .replace("{userAgent}", userAgent != null ? userAgent : "-");
    }

}