/*
 * Copyright (c) 2018-2020 Board of Trustees of Leland Stanford Jr. University,
 * all rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Stanford University shall not
 * be used in advertising or otherwise to promote the sale, use or other dealings
 * in this Software without prior written authorization from Stanford University.
 */

package org.lockss.laaws.crawler.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.*;

import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.laaws.crawler.api.CrawlersApi;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.model.CrawlerStatus;
import org.lockss.laaws.crawler.model.CrawlerStatuses;
import org.lockss.log.L4JLogger;
import org.lockss.test.SpringLockssTestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestCrawlersApiServiceImpl extends SpringLockssTestCase {
  // The application Context used to specify the command line arguments.
  @Autowired
  private ApplicationContext appCtx;

  @Autowired
  private CrawlersApi api;

  private static final L4JLogger log = L4JLogger.getLogger();

  private static final String BOGUS_CRAWLER = "bogos";
  private static final String LOCKSS_CRAWLER = "lockss";

  @Test
  public void contextLoads() {
    log.info("context-loaded");
    assertNotNull(api);
  }
  @Test
  public void getCrawlerConfigTest() throws Exception {
    // test bogus not found.
    ResponseEntity<CrawlerConfig> responseEntity = api.getCrawlerConfig(BOGUS_CRAWLER);
    assertEquals(NOT_FOUND, responseEntity.getStatusCode());

    // test known crawler "lockss" is found.
    responseEntity = api.getCrawlerConfig(LOCKSS_CRAWLER);
    assertEquals(OK, responseEntity.getStatusCode());
    CrawlerConfig config = responseEntity.getBody();
    assertNotNull(config);
  }

  @Test
  public void getCrawlersTest() throws Exception {
    ResponseEntity<CrawlerStatuses> responseEntity = api.getCrawlers();
    assertEquals(OK,responseEntity.getStatusCode());
    CrawlerStatuses body  =  responseEntity.getBody();
    // we have one and onlu one crawler and it's name is "lockss"
    Map<String, CrawlerStatus> crawlers = body.getCrawlerMap();
    assertEquals(1, crawlers.size());
    assertTrue(crawlers.containsKey(LOCKSS_CRAWLER));
  }
}
