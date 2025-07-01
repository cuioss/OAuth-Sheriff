/**
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
package de.cuioss.jwt.validation.util;

import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.HttpStatusFamily;
import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * ETag-aware HTTP handler with stateful caching capabilities.
 * <p>
 * This component provides HTTP-based caching using ETags and "If-None-Match" headers.
 * It tracks whether content was loaded from cache (304 Not Modified) or freshly fetched (200 OK).
 * <p>
 * Thread-safe implementation using volatile fields and synchronized methods.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class ETagAwareHttpHandler {

    private static final CuiLogger LOGGER = new CuiLogger(ETagAwareHttpHandler.class);

    /**
     * Enum representing the state of a load operation.
     */
    public enum LoadState {
        /**
         * Content was freshly loaded from the server (200 OK).
         * Data has changed - keys should be reloaded/reevaluated.
         */
        LOADED_FROM_SERVER(true),

        /**
         * Server responded with 304 Not Modified based on ETag.
         * Data has not changed - no need to reload keys.
         */
        CACHE_ETAG(false),

        /**
         * Content was returned from local cache without server request.
         * Data has not changed - no need to reload keys.
         */
        CACHE_CONTENT(false),

        /**
         * An error occurred during loading, but cached data is still available.
         * Data has not changed - no need to reload keys.
         */
        ERROR_WITH_CACHE(false),

        /**
         * An error occurred during loading and no cached data is available.
         * Data state is unknown - keys need reevaluation.
         */
        ERROR_NO_CACHE(true);

        /**
         * true if data changed and keys need reevaluation, false if unchanged
Ï        */
        @Getter
        private final boolean dataChanged;

        LoadState(boolean dataChanged) {
            this.dataChanged = dataChanged;
        }

    }

    /**
     * Result of a load operation containing the payload and detailed load state.
     *
     * @param content the HTTP content as string
     * @param loadState the detailed state of the load operation
     */
    public record LoadResult(String content, LoadState loadState) {
    }

    private final HttpHandler httpHandler;

    private volatile String cachedContent;
    private volatile String cachedETag;

    /**
     * Creates a new ETag-aware HTTP handler for cache validation.
     *
     * @param httpHandler the HTTP handler for making requests
     */
    public ETagAwareHttpHandler(@NonNull HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    /**
     * Loads HTTP content, using ETag-based HTTP caching when supported.
     *
     * @return LoadResult containing content and cache status, never null
     */
    public synchronized LoadResult load() {
        HttpFetchResult result = fetchJwksContentWithCache();

        if (result.error) {
            return handleErrorResult();
        }

        if (hasCachedContentWithETag() && result.notModified) {
            return handleNotModifiedResult();
        }

        return handleSuccessResult(result);
    }

    /**
     * Forces a reload of HTTP content, optionally clearing cache completely.
     *
     * @param clearCache if true, clears all cached content; if false, only bypasses ETag validation
     * @return LoadResult with fresh content or error state, never null
     */
    public synchronized LoadResult reload(boolean clearCache) {
        if (clearCache) {
            LOGGER.debug("Clearing HTTP cache and reloading from %s", httpHandler.getUrl());
            this.cachedContent = null;
        } else {
            LOGGER.debug("Bypassing ETag validation and reloading from %s", httpHandler.getUrl());
        }
        this.cachedETag = null;

        return load();
    }

    /**
     * Checks if we have both cached content and ETag available.
     */
    private boolean hasCachedContentWithETag() {
        return cachedContent != null && cachedETag != null;
    }

    /**
     * Handles error results by returning cached content if available.
     */
    private LoadResult handleErrorResult() {
        if (cachedContent != null) {
            return new LoadResult(cachedContent, LoadState.ERROR_WITH_CACHE);
        } else {
            return new LoadResult(null, LoadState.ERROR_NO_CACHE);
        }
    }

    /**
     * Handles 304 Not Modified response by returning cached content.
     */
    private LoadResult handleNotModifiedResult() {
        LOGGER.debug("HTTP content not modified (304), using cached version");
        return new LoadResult(cachedContent, LoadState.CACHE_ETAG);
    }

    /**
     * Handles successful response by checking for content changes and updating cache.
     */
    private LoadResult handleSuccessResult(HttpFetchResult result) {
        // Check if content actually changed despite new response
        if (cachedContent != null && cachedContent.equals(result.content)) {
            LOGGER.debug("HTTP content unchanged despite 200 OK response");
            return new LoadResult(cachedContent, LoadState.CACHE_CONTENT);
        }

        // Update cache with fresh content
        this.cachedContent = result.content;
        this.cachedETag = result.etag; // May be null if server doesn't support ETags

        LOGGER.info("Loaded fresh HTTP content from %s", httpHandler.getUrl());
        return new LoadResult(result.content, LoadState.LOADED_FROM_SERVER);
    }

    /**
     * Internal result for HTTP fetch operations.
     */
    private record HttpFetchResult(String content, String etag, boolean notModified, boolean error) {
    }

    /**
     * Fetches HTTP content from the endpoint with ETag support.
     *
     * @return HttpFetchResult with error flag set if request fails
     */
    @SuppressWarnings("java:S2095") // owolff False positive for HttpResponse since it is closed automatically
    private HttpFetchResult fetchJwksContentWithCache() {
        // Build request with conditional headers
        HttpRequest.Builder requestBuilder = httpHandler.requestBuilder();

        // Add If-None-Match header if we have a cached ETag
        if (cachedETag != null) {
            requestBuilder.header("If-None-Match", cachedETag);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpClient client = httpHandler.createHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            HttpStatusFamily statusFamily = HttpStatusFamily.fromStatusCode(response.statusCode());

            if (response.statusCode() == 304) {
                // Not Modified - content hasn't changed
                LOGGER.debug("Received 304 Not Modified from %s", httpHandler.getUrl());
                return new HttpFetchResult(null, null, true, false);
            } else if (statusFamily == HttpStatusFamily.SUCCESS) {
                // 2xx Success - fresh content
                String content = response.body();
                String etag = response.headers().firstValue("ETag").orElse(null);

                LOGGER.debug("Received %s %s from %s with ETag: %s", response.statusCode(), statusFamily, httpHandler.getUrl(), etag);
                return new HttpFetchResult(content, etag, false, false);
            } else {
                LOGGER.warn("HTTP %s (%s) from %s", response.statusCode(), statusFamily, httpHandler.getUrl());
                return new HttpFetchResult(null, null, false, true);
            }

        } catch (IOException e) {
            LOGGER.warn(e, "Failed to fetch HTTP content from %s", httpHandler.getUrl());
            return new HttpFetchResult(null, null, false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while fetching HTTP content from %s", httpHandler.getUrl());
            return new HttpFetchResult(null, null, false, true);
        }
    }
}