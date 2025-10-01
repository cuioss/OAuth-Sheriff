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

import de.cuioss.benchmarking.common.output.OutputDirectoryStructure;
import de.cuioss.benchmarking.common.util.JsonSerializationHelper;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_DATA_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_SUMMARY_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Html.ERROR_404;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.ROBOTS_TXT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.SITEMAP_XML;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Api.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Defaults.N_A;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.JsonFields.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.NOT_FOUND_FORMAT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.PATH_PREFIX;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates GitHub Pages ready deployment structure from benchmark artifacts.
 * Since all report files are now written directly to gh-pages-ready by other generators,
 * this class only needs to generate additional deployment assets (404.html, robots.txt, sitemap.xml).
 * <p>
 * NO COPYING is performed - everything else is already in gh-pages-ready.
 */
public class GitHubPagesGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Generates deployment-specific assets for GitHub Pages.
     * This assumes all report files are already written to gh-pages-ready directory.
     *
     * @param structure the output directory structure
     * @throws IOException if file operations fail
     */
    public void generateDeploymentAssets(OutputDirectoryStructure structure) throws IOException {
        LOGGER.info(INFO.PREPARING_GITHUB_PAGES::format);

        // Ensure deployment directory exists
        structure.ensureDirectories();

        Path deployDir = structure.getDeploymentDir();
        LOGGER.info(INFO.DEPLOY_DIRECTORY.format(deployDir));

        // Generate deployment-specific pages
        generate404Page(deployDir);
        generateRobotsTxt(deployDir);
        generateSitemap(deployDir);

        // Generate API endpoints
        generateApiEndpoints(structure);

        LOGGER.info(INFO.GITHUB_PAGES_READY::format);
    }

    /**
     * Generates a 404 error page.
     */
    private void generate404Page(Path deployDir) throws IOException {
        LOGGER.debug("Generating additional pages");
        String html404 = loadTemplate(ERROR_404);
        Files.writeString(deployDir.resolve(ERROR_404), html404);
    }

    /**
     * Generates robots.txt for search engines.
     */
    private void generateRobotsTxt(Path deployDir) throws IOException {
        String robotsTxt = loadTemplate(ROBOTS_TXT);
        Files.writeString(deployDir.resolve(ROBOTS_TXT), robotsTxt);
    }

    /**
     * Generates a basic sitemap.xml.
     */
    private void generateSitemap(Path deployDir) throws IOException {
        String sitemap = loadTemplate(SITEMAP_XML);
        Files.writeString(deployDir.resolve(SITEMAP_XML), sitemap);
    }

    /**
     * Generates API endpoints for programmatic access.
     */
    private void generateApiEndpoints(OutputDirectoryStructure structure) throws IOException {
        LOGGER.debug("Creating API endpoints");

        Path deployDir = structure.getDeploymentDir();
        Path apiDir = structure.getApiDir();

        // Create API structure
        createLatestEndpoint(deployDir, apiDir);
        createBenchmarksEndpoint(deployDir, apiDir);
        createStatusEndpoint(deployDir, apiDir);
    }

    private void createLatestEndpoint(Path deployDir, Path apiDir) throws IOException {
        Path latestFile = apiDir.resolve(LATEST_JSON);
        Path summaryFile = deployDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> latestData = new LinkedHashMap<>();
        latestData.put(TIMESTAMP, Instant.now().toString());
        latestData.put(STATUS, STATUS_SUCCESS);

        Map<String, Object> summary = new LinkedHashMap<>();
        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = JsonSerializationHelper.jsonToMap(summaryContent);
            summary.put(TOTAL_BENCHMARKS, summaryData.getOrDefault(TOTAL_BENCHMARKS, 0));
            summary.put(PERFORMANCE_GRADE_KEY, summaryData.getOrDefault(PERFORMANCE_GRADE_KEY, N_A));
            summary.put(AVERAGE_THROUGHPUT, summaryData.getOrDefault(AVERAGE_THROUGHPUT, 0.0));
        } else {
            summary.put(TOTAL_BENCHMARKS, 0);
            summary.put(PERFORMANCE_GRADE_KEY, N_A);
        }
        latestData.put(SUMMARY, summary);

        Map<String, String> links = new LinkedHashMap<>();
        links.put(BENCHMARKS, API_BENCHMARKS_PATH);
        links.put("badges", BADGES_PATH);
        latestData.put(LINKS, links);

        Files.writeString(latestFile, JsonSerializationHelper.toJson(latestData));
        LOGGER.debug("Created API endpoint: %s", latestFile);
    }

    private void createBenchmarksEndpoint(Path deployDir, Path apiDir) throws IOException {
        Path benchmarksFile = apiDir.resolve(BENCHMARKS_JSON);
        Path benchmarkDataFile = deployDir.resolve("data").resolve(BENCHMARK_DATA_JSON);

        if (Files.exists(benchmarkDataFile)) {
            // Extract benchmark data from benchmark-data.json
            String content = Files.readString(benchmarkDataFile);
            @SuppressWarnings("unchecked") Map<String, Object> data = JsonSerializationHelper.jsonToMap(content);
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put(BENCHMARKS, data.getOrDefault(BENCHMARKS, new LinkedHashMap<>()));
            benchmarksData.put(GENERATED, Instant.now().toString());

            Files.writeString(benchmarksFile, JsonSerializationHelper.toJson(benchmarksData));
        } else {
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put(BENCHMARKS, new LinkedHashMap<>());
            benchmarksData.put(GENERATED, Instant.now().toString());

            Files.writeString(benchmarksFile, JsonSerializationHelper.toJson(benchmarksData));
        }

        LOGGER.debug("Created API endpoint: %s", benchmarksFile);
    }

    private void createStatusEndpoint(Path deployDir, Path apiDir) throws IOException {
        Path statusFile = apiDir.resolve(STATUS_JSON);
        Path summaryFile = deployDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> statusData = new LinkedHashMap<>();
        statusData.put(STATUS, STATUS_HEALTHY);
        statusData.put(TIMESTAMP, Instant.now().toString());

        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = JsonSerializationHelper.jsonToMap(summaryContent);
            statusData.put(LAST_RUN, summaryData.getOrDefault(TIMESTAMP, Instant.now().toString()));
        } else {
            statusData.put(LAST_RUN, Instant.now().toString());
        }

        Files.writeString(statusFile, JsonSerializationHelper.toJson(statusData));
        LOGGER.debug("Created API endpoint: %s", statusFile);
    }

    /**
     * Loads a template from the classpath resources.
     *
     * @param templateName the name of the template file
     * @return the template content as a string
     * @throws IOException if the template cannot be loaded
     */
    private String loadTemplate(String templateName) throws IOException {
        String resourcePath = PATH_PREFIX + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException(NOT_FOUND_FORMAT.formatted(resourcePath));
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}