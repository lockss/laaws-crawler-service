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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.UrlPager;
import org.lockss.log.L4JLogger;
import org.lockss.test.SpringLockssTestCase;
import org.lockss.util.rest.crawler.Crawl;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestCrawlsApiServiceImpl extends SpringLockssTestCase {

  private static final L4JLogger log = L4JLogger.getLogger();
  // The port that Tomcat is using during this test.
  @LocalServerPort
  private int port;

  // The application Context used to specify the command line arguments to be
  // used for the tests.
  @Autowired
  ApplicationContext appCtx;

  @Autowired
  private CrawlsApi api;
  private static final String BOGUS_ID = "bogus";


  @Test
  public void deleteCrawlByIdTest() throws Exception {
    String jobId = BOGUS_ID;
    ResponseEntity<CrawlStatus> responseEntity = api.deleteCrawlById(jobId);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void deleteCrawlsTest() throws Exception {
    ResponseEntity<Void> responseEntity = api.deleteCrawls();
    assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
  }

  @Test
  public void doCrawlTest() throws Exception {
    CrawlDesc body = new CrawlDesc();
    body.crawlKind("FollowLinkCrawler");
    body.auId("auId");
    ResponseEntity<Crawl> responseEntity = api.doCrawl(body);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlByIdTest() throws Exception {
    String jobId = BOGUS_ID;
    ResponseEntity<CrawlStatus> responseEntity = api.getCrawlById(jobId);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlByMimeTypeTest() throws Exception {
    String jobId = BOGUS_ID;
    String type = "type_example";
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api
        .getCrawlByMimeType(jobId, type, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlErrorsTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api.getCrawlErrors(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlExcludedTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api.getCrawlExcluded(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlFetchedTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api.getCrawlFetched(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlNotModifiedTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api
        .getCrawlNotModified(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlParsedTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api.getCrawlParsed(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlPendingTest() throws Exception {
    String jobId = BOGUS_ID;
    String continuationToken = "continuationToken_example";
    Integer limit = 56;
    ResponseEntity<UrlPager> responseEntity = api.getCrawlPending(jobId, continuationToken, limit);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }

  @Test
  public void getCrawlsTest() throws Exception {
    Integer limit = 56;
    String continuationToken = "continuationToken_example";
    ResponseEntity<JobPager> responseEntity = api.getCrawls(limit, continuationToken);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
  }
}
