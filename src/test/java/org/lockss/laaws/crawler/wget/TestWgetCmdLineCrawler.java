/*
 * Copyright (c) 2000-$.year, Board of Trustees of Leland Stanford Jr. University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.lockss.laaws.crawler.wget;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.util.ListUtil;

public class TestWgetCmdLineCrawler {

  private static final String CLASSIC_CRAWLER_ID = "classic";
  private static final String ATTR_SUCCESS_CODE = "successCode";
  private static final String ATTR_OUTPUT_LEVEL = "outputLevel";
  private WgetCmdLineCrawler wgetCmdLineCrawler;

  @BeforeEach
  void setUp() {
    wgetCmdLineCrawler = new WgetCmdLineCrawler();
  }

  @Test
  @DisplayName("Should set success codes with given values when input string is not null or empty")
  void setSuccessCodesWithGivenValuesWhenInputStringIsNotNull() {
    String input = "200;201;202";
    wgetCmdLineCrawler.setSuccessCodes(input);
    List<Integer> expected = ListUtil.list(200, 201, 202);
    List<Integer> actual = wgetCmdLineCrawler.getSuccessCodes();
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("Should set success codes with default value when input string is null or empty")
  void setSuccessCodesWithDefaultValueWhenInputStringIsNull() {
    String inputStr = null;
    wgetCmdLineCrawler.setSuccessCodes(inputStr);
    List<Integer> expectedSuccessCodes = new ArrayList<>();
    expectedSuccessCodes.add(0);
    List<Integer> actualSuccessCodes = wgetCmdLineCrawler.getSuccessCodes();
    assertEquals(expectedSuccessCodes, actualSuccessCodes);

    inputStr = "";
    wgetCmdLineCrawler.setSuccessCodes(inputStr);
    actualSuccessCodes = wgetCmdLineCrawler.getSuccessCodes();
    assertEquals(expectedSuccessCodes, actualSuccessCodes);
  }

  @Test
  @DisplayName("Should set the output level based on the given attribute")
  void
      updateCrawlerConfigSetsOutputLevel() { // Create a CrawlerConfig object with outputLevel
                                             // attribute set to "debug"
    CrawlerConfig crawlerConfig =
        new CrawlerConfig()
            .crawlerId(CLASSIC_CRAWLER_ID)
            .putAttributesItem(ATTR_OUTPUT_LEVEL, "debug");

    // Call the updateCrawlerConfig method of WgetCmdLineCrawler with the above CrawlerConfig object
    wgetCmdLineCrawler.updateCrawlerConfig(crawlerConfig);

    // Assert that the outputLevel of the WgetCmdLineCrawler object is set to "--debug"
    assertEquals("--debug", wgetCmdLineCrawler.getOutputLevel());
  }

  @Test
  @DisplayName("Should set the success codes based on the given attribute")
  void updateCrawlerConfigSetsSuccessCodes() { // Create a mock CrawlerConfig object
    CrawlerConfig mockCrawlerConfig = mock(CrawlerConfig.class);

    // Create a mock Map object for attributes
    Map<String, String> mockAttributes = new HashMap<>();
    mockAttributes.put(ATTR_SUCCESS_CODE, "0;200;404");
    mockAttributes.put(ATTR_OUTPUT_LEVEL, "quiet");
    mockAttributes.put("opt.recursive", "true");
    mockAttributes.put("opt.level", "inf");

    // Set the mock attributes to the mock CrawlerConfig object
    when(mockCrawlerConfig.getAttributes()).thenReturn(mockAttributes);

    // Call the updateCrawlerConfig method with the mock CrawlerConfig object
    wgetCmdLineCrawler.updateCrawlerConfig(mockCrawlerConfig);

    // Verify that the success codes are set correctly
    List<Integer> expectedSuccessCodes = ListUtil.list(0, 200, 404);
    assertEquals(expectedSuccessCodes, wgetCmdLineCrawler.getSuccessCodes());
  }

  @Test
  @DisplayName("Should add config options based on the attributes starting with 'opt.'")
  void updateCrawlerConfigAddsConfigOptions() { // Create a mock CrawlerConfig object
    CrawlerConfig mockConfig = mock(CrawlerConfig.class);

    // Create a mock Map object for attributes
    Map<String, String> mockAttributes = new HashMap<>();
    mockAttributes.put(ATTR_OUTPUT_LEVEL, "debug");
    mockAttributes.put(ATTR_SUCCESS_CODE, "0;1;2");
    mockAttributes.put("opt.recursive", "true");
    mockAttributes.put("opt.level", "inf");

    // Set the mock attributes to the mock CrawlerConfig object
    when(mockConfig.getAttributes()).thenReturn(mockAttributes);

    // Call the updateCrawlerConfig method with the mock CrawlerConfig object
    wgetCmdLineCrawler.updateCrawlerConfig(mockConfig);

    // Verify that the config options were added correctly
    List<String> expectedConfigOptions =
        ListUtil.list("--debug", "--recursive=true", "--level=inf");
    assertEquals(expectedConfigOptions, wgetCmdLineCrawler.getConfigOptions());
  }

  @Test
  @DisplayName("Should update the crawler config with the given attributes")
  void updateCrawlerConfigWithGivenAttributes() { // Create a mock CrawlerConfig object
    CrawlerConfig mockCrawlerConfig = mock(CrawlerConfig.class);

    // Create a mock Map object for attributes
    Map<String, String> mockAttributes = new HashMap<>();
    mockAttributes.put(ATTR_SUCCESS_CODE, "0");
    mockAttributes.put(ATTR_OUTPUT_LEVEL, "quiet");
    mockAttributes.put("opt.recursive", "true");
    mockAttributes.put("opt.level", "inf");

    // Set the mock attributes to the mock CrawlerConfig object
    when(mockCrawlerConfig.getAttributes()).thenReturn(mockAttributes);

    // Call the updateCrawlerConfig method with the mock CrawlerConfig object
    wgetCmdLineCrawler.updateCrawlerConfig(mockCrawlerConfig);

    // Verify that the updateCrawlerConfig method has set the attributes correctly
    assertEquals("--quiet", wgetCmdLineCrawler.getOutputLevel());
    assertEquals(ListUtil.list(0), wgetCmdLineCrawler.getSuccessCodes());
    assertEquals(
        ListUtil.list("--quiet", "--recursive=true", "--level=inf"),
        wgetCmdLineCrawler.getConfigOptions());
  }

  @Test
  @DisplayName("Should update the crawler config with provided output level")
  void updateCrawlerConfigWithOutputLevel() {
    CrawlerConfig crawlerConfig = new CrawlerConfig();
    crawlerConfig.setCrawlerId(CLASSIC_CRAWLER_ID);
    Map<String, String> attributes = new HashMap<>();
    attributes.put(WgetCmdLineCrawler.ATTR_OUTPUT_LEVEL, "debug");
    crawlerConfig.setAttributes(attributes);

    wgetCmdLineCrawler.updateCrawlerConfig(crawlerConfig);

    assertEquals("--debug", wgetCmdLineCrawler.getOutputLevel());
  }

  @Test
  @DisplayName(
      "Should update the crawler config with default values when no attributes are provided")
  void updateCrawlerConfigWithDefaultValues() {
    CrawlerConfig crawlerConfig = new CrawlerConfig().crawlerId(CLASSIC_CRAWLER_ID);
    wgetCmdLineCrawler.updateCrawlerConfig(crawlerConfig);
    assertEquals(CLASSIC_CRAWLER_ID, wgetCmdLineCrawler.getCrawlerId());
    assertNull(wgetCmdLineCrawler.getOutputLevel());
    assertEquals(ListUtil.list(0), wgetCmdLineCrawler.getSuccessCodes());
    assertEquals(Collections.EMPTY_LIST, wgetCmdLineCrawler.getConfigOptions());
  }

  @Test
  @DisplayName("Should update the crawler config with provided custom options")
  void updateCrawlerConfigWithCustomOptions() {
    CrawlerConfig crawlerConfig = new CrawlerConfig();
    crawlerConfig.setCrawlerId(CLASSIC_CRAWLER_ID);
    Map<String, String> attributes = new HashMap<>();
    attributes.put("outputLevel", "debug");
    attributes.put("successCode", "0;1;2");
    attributes.put("opt.recursive", "true");
    attributes.put("opt.level", "inf");
    crawlerConfig.setAttributes(attributes);

    wgetCmdLineCrawler.updateCrawlerConfig(crawlerConfig);

    List<String> configOptions = wgetCmdLineCrawler.getConfigOptions();
    assertEquals("--debug", wgetCmdLineCrawler.getOutputLevel());
    assertEquals(Arrays.asList(0, 1, 2), wgetCmdLineCrawler.getSuccessCodes());
    assertTrue(configOptions.contains("--debug"));
    assertTrue(configOptions.contains("--recursive=true"));
    assertTrue(configOptions.contains("--level=inf"));
  }

  @Test
  @DisplayName("Should update the crawler config with provided success codes")
  void updateCrawlerConfigWithSuccessCodes() {
    CrawlerConfig crawlerConfig = new CrawlerConfig();
    crawlerConfig.setCrawlerId(CLASSIC_CRAWLER_ID);
    Map<String, String> attributes = new HashMap<>();
    attributes.put(WgetCmdLineCrawler.ATTR_SUCCESS_CODE, "0;200;201");
    attributes.put(WgetCmdLineCrawler.ATTR_OUTPUT_LEVEL, "debug");
    attributes.put("opt.header", "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
    crawlerConfig.setAttributes(attributes);

    wgetCmdLineCrawler.updateCrawlerConfig(crawlerConfig);

    List<Integer> expectedSuccessCodes = ListUtil.list(0, 200, 201);
    assertEquals(expectedSuccessCodes, wgetCmdLineCrawler.getSuccessCodes());
    assertEquals("--debug", wgetCmdLineCrawler.getOutputLevel());
    List<String> expectedConfigOptions =
        ListUtil.list("--debug", "--header=Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
    assertEquals(expectedConfigOptions, wgetCmdLineCrawler.getConfigOptions());
  }
}
