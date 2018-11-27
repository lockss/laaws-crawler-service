/*
 * Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

import org.lockss.app.LockssApp;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.model.CrawlRequest;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.ErrorPager;
import org.lockss.laaws.crawler.model.UrlPager;
import org.lockss.laaws.status.model.ApiStatus;
import org.lockss.log.L4JLogger;
import org.lockss.spring.status.SpringLockssBaseApiController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CrawlsApiImpl extends SpringLockssBaseApiController
    implements CrawlsApiDelegate {
  private static L4JLogger log = L4JLogger.getLogger();
  private static final String API_VERSION = "1.0.0";

  /**
   * Return the status of the service and its version.
   * @return ApiStatus the common laaws status info for this service.
   */
  @Override
  public ApiStatus getApiStatus() {
    return new ApiStatus()
        .setVersion(API_VERSION)
        .setReady(LockssApp.getLockssApp().isAppRunning());
  }

  /**
   * Request a crawl be added to the crawl queue.
   * @param body the Crawl Request needed to run the crawl.
   * @see CrawlsApi#addCrawl
   */
  @Override
  public ResponseEntity<CrawlRequest> addCrawl(CrawlRequest body) {
    return null;
  }

  /**
   * Delete a crawl previously added to the crawl queue, stop the crawl if already running.
   * @param jobId the id assigned to the crawl when added.
   * @see CrawlsApi#deleteCrawlById
   */
  @Override
  public ResponseEntity<CrawlRequest> deleteCrawlById(Integer jobId) {
    return null;
  }


  /**
   * Return the status of a requested crawl.
   * @param jobId the id assigned to the crawl when added
   * @see CrawlsApi#getCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> getCrawlById(Integer jobId) {
    return null;
  }

  /**
   * Return the list of error urls with error code and message.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlErrored
   */
  @Override
  public ResponseEntity<ErrorPager> getCrawlErrored(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

  /**
   * Return the list of urls excluded from the crawl.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlExcluded
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlExcluded(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

  /**
   * Return the list of urls fetched in the crawl.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlFetched
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlFetched(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

  /**
   * Return the list of urls found to be notModified during the crawl.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlNotModified
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlNotModified(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

  /**
   * Return the list of urls parsed during the crawl.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlParsed
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlParsed(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

  /**
   * Return the list of urls pending for the crawl.
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlPending
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlPending(Integer jobId, String continuationToken, Integer limit) {
    return null;
  }

}
