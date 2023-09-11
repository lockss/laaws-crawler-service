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

import org.lockss.app.LockssDaemon;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.laaws.crawler.model.CrawlPager;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.UrlInfo;
import org.lockss.laaws.crawler.model.UrlPager;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

import static org.lockss.laaws.crawler.impl.ApiUtils.*;
import static org.lockss.util.rest.crawler.CrawlDesc.CLASSIC_CRAWLER_ID;

/**
 * Service for accessing crawls.
 */
@Service
public class CrawlsApiServiceImpl extends BaseSpringApiServiceImpl implements CrawlsApiDelegate {
  // Error Codes
  static final String NOT_INITIALIZED_MESSAGE = "The service has not been fully initialized";
  // The logger for this class.
  private static final L4JLogger log = L4JLogger.getLogger();

  private final HttpServletRequest request;

  @Autowired
  public CrawlsApiServiceImpl(HttpServletRequest request) {
    this.request = request;
  }


  /**
   * Provides the status of a requested crawl.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return a {@code ResponseEntity<CrawlStatus>} with the status of the crawl.
   * @see CrawlsApi#getCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> getCrawlById(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlStatus crawlStatus;
    JobStatus jobStatus;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("jobId = {}", jobId);
        jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
        crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
        return new ResponseEntity<>(crawlStatus, HttpStatus.SERVICE_UNAVAILABLE);
      }

      crawlStatus = getCrawlStatus(jobId);
      log.debug2("crawlStatus = {}", crawlStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.NOT_FOUND);
    }
    catch (Exception e) {
      String message = "Cannot getCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the items in a crawl by MIME type..
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param type              A String with the MIME type.
   * @param limit             An Integer with the maximum number of URLs per s page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the items.
   * @see CrawlsApi#getCrawlByMimeType
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlByMimeType(
    String jobId, String type, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("type = {}", type);
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = '{}'", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsOfMimeType(type);
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlByMimeType() for jobId '"
          + jobId
          + "', type = '"
          + type
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the error URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the error URLs.
   * @see CrawlsApi#getCrawlErrors
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlErrors(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      log.trace("status = {}", status);
      List<String> urls = new ArrayList<>(status.getUrlsErrorMap().keySet());
      log.trace("urls = {}", urls);
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlErrors() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the excluded URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the excluded URLs.
   * @see CrawlsApi#getCrawlExcluded
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlExcluded(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsExcluded();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlExcluded() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the fetched URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the fetched URLs.
   * @see CrawlsApi#getCrawlFetched
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlFetched(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsFetched();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlFetched() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the not-modified URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the not-modified URLs.
   * @see CrawlsApi#getCrawlNotModified
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlNotModified(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsNotModified();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlNotModified() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the parsed URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the parsed URLs.
   * @see CrawlsApi#getCrawlParsed
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlParsed(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsParsed();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlParsed() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the pending URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl when added.
   * @param limit             An Integer with the maximum number of URLs per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the pending URLs.
   * @see CrawlsApi#getCrawlPending
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlPending(
    String jobId, Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsPending();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String message =
        "Cannot getCrawlPending() for jobId '"
          + jobId
          + "', limit = "
          + limit
          + ", continuationToken = "
          + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Provides all (or a pageful of) the crawls in the service.
   *
   * @param limit             An Integer with the maximum number of crawls per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<CrawlPager>} with the information about the crawls.
   * @see CrawlsApi#getCrawls
   */
  @Override
  public ResponseEntity<CrawlPager> getCrawls(Integer limit, String continuationToken) {
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        log.error(NOT_INITIALIZED_MESSAGE);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlPager pager = getCrawlsPager(limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (IllegalArgumentException iae) {
      String message =
        "Cannot get crawls with limit = " + limit + ", continuationToken = " + continuationToken;
      log.error(message, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception ex) {
      String message =
        "Cannot get crawls with limit = " + limit + ", continuationToken = " + continuationToken;
      log.error(message, ex);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  /**
   * Provides a pageful of URLs.
   *
   * @param crawlerStatus     A CrawlerStatus with the crawler status.
   * @param allUrls           A List<String> with the complete collection of URLs to paginate.
   * @param requestLimit      An Integer with the request maximum number of URLs per page.
   * @param continuationToken A String with the continuation token provided in the request.
   * @return a UrlPager with the pageful of URLs.
   */
  UrlPager getUrlPager(
    CrawlerStatus crawlerStatus,
    List<String> allUrls,
    Integer requestLimit,
    String continuationToken) {
    log.debug2("crawlerStatus = {}", crawlerStatus);
    log.debug2("allUrls = {}", allUrls);
    log.debug2("requestLimit = {}", requestLimit);
    log.debug2("continuationToken = {}", continuationToken);

    // The continuation token timestamp.
    long timeStamp = crawlerStatus.getStartTime();
    log.trace("timeStamp = {}", timeStamp);

    // Validate the requested limit.
    Integer validLimit = validateLimit(requestLimit);
    log.trace("validLimit = {}", validLimit);

    // The last URL, of the list of all the URLs, to skip.
    long lastUrlToSkip = -1;

    // Check whether a continuation token has been received.
    if (continuationToken != null) {
      // Yes.
      ContinuationToken requestToken = new ContinuationToken(continuationToken);
      log.trace("requestToken = {}", requestToken);

      // Validate the continuation token.
      validateContinuationToken(timeStamp, requestToken);

      // Get the last previously served URL index.
      Long previouslastUrlIndex = requestToken.getLastElement();
      log.trace("previouslastUrlIndex = {}", previouslastUrlIndex);

      if (previouslastUrlIndex != null) {
        lastUrlToSkip = previouslastUrlIndex;
      }
    }

    // Get the size of the collection of all URLs.
    int listSize = allUrls.size();
    log.trace("listSize = {}", listSize);

    UrlPager pager = new UrlPager();
    Long lastItem = null;

    // Check whether there is anything to provide,
    if (listSize > 0) {
      // Yes: Validate the count of URLs to skip.
      if (lastUrlToSkip + 1 >= listSize) {
        String errMsg =
          "Invalid pagination request: startAt = "
            + (lastUrlToSkip + 1)
            + ", Total = "
            + listSize;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }

      List<UrlInfo> outputUrls = new ArrayList<>();

      // Get the number of URLs to return.
      int outputSize = (int) (listSize - (lastUrlToSkip + 1));

      if (validLimit != null && validLimit > 0 && validLimit < outputSize) {
        outputSize = validLimit;
      }

      log.trace("outputSize = {}", outputSize);

      int idx = 0;

      // Loop through all the URLs until the output size has been reached.
      while (outputUrls.size() < outputSize) {
        log.trace("idx = {}", idx);

        // Check whether this URL does not need to be skipped.
        if (idx > lastUrlToSkip) {
          // Yes: Get it.
          String url = allUrls.get(idx);
          log.trace("url = {}", url);

          // Add it to the output collection.
          outputUrls.add(makeUrlInfo(url, crawlerStatus));

          // Record that it is the last one so far.
          lastItem = (long) idx;
        }

        // Point to the next URL.
        idx++;
      }

      // Add the output URLs to the pagination.
      pager.setUrls(outputUrls);
    }

    // Set the pagination information.
    pager.setPageInfo(getPageInfo(validLimit, lastItem, listSize, timeStamp));

    log.debug2("pager = {}", pager);
    return pager;
  }


  /**
   * Provides a pageful of jobs.
   *
   * @param requestLimit      An Integer with the request maximum number of jobs per page.
   * @param continuationToken A String with the continuation token provided in the request.
   * @return a UrlPager with the pageful of jobs.
   */
  CrawlPager getCrawlsPager(Integer requestLimit, String continuationToken) {
    log.debug2("requestLimit = {}", requestLimit);
    log.debug2("continuationToken = {}", continuationToken);

    // The continuation token timestamp.
    long timeStamp = LockssDaemon.getLockssDaemon().getStartDate().getTime();
    log.trace("timeStamp = {}", timeStamp);

    // Validate the requested limit.
    Integer validLimit = validateLimit(requestLimit);
    log.trace("validLimit = {}", validLimit);

    // The last job, of the list of all the job, to skip.
    long lastJobToSkip = -1;

    // Check whether a continuation token has been received.
    if (continuationToken != null) {
      // Yes.
      ContinuationToken requestToken = new ContinuationToken(continuationToken);
      log.trace("requestToken = {}", requestToken);

      // Validate the continuation token.
      validateContinuationToken(timeStamp, requestToken);

      // Get the last previously served job index.
      Long previouslastJobIndex = requestToken.getLastElement();
      log.trace("previouslastJobIndex = {}", previouslastJobIndex);

      if (previouslastJobIndex != null) {
        lastJobToSkip = previouslastJobIndex;
      }
    }

    // Get the collection of all jobs.
    // This needs to be replaced with a CrawlJob map for crawl manager jobs
    List<CrawlerStatus> allCrawls = getLockssCrawlManager().getStatus().getCrawlerStatusList();
    log.trace("allCrawls = {}", allCrawls);

    // Get the size of the collection of all jobs.
    int listSize = allCrawls.size();
    log.trace("listSize = {}", listSize);

    CrawlPager pager = new CrawlPager();
    Long lastItem = null;

    // Check whether there is anything to provide,
    if (listSize > 0) {
      // Yes: Validate the count of jobs to skip.
      if (lastJobToSkip + 1 >= listSize) {
        String errMsg =
          "Invalid pagination request: startAt = "
            + (lastJobToSkip + 1)
            + ", Total = "
            + listSize;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }

      List<CrawlStatus> outputCrawls = new ArrayList<>();

      // Get the number of jobs to return.
      int outputSize = (int) (listSize - (lastJobToSkip + 1));

      if (validLimit != null && validLimit > 0 && validLimit < outputSize) {
        outputSize = validLimit;
      }

      log.trace("outputSize = {}", outputSize);

      int idx = 0;

      // Loop through all the jobs until the output size has been reached.
      while (outputCrawls.size() < outputSize) {
        log.trace("idx = {}", idx);

        // Check whether this job does not need to be skipped.
        if (idx > lastJobToSkip) {
          // Yes: Get it.
          CrawlerStatus crawlerStatus = allCrawls.get(idx);
          log.trace("crawlerStatus = {}", crawlerStatus);
          // Add it to the output collection.
          outputCrawls.add(makeCrawlStatus(crawlerStatus));

          // Record that it is the last one so far.
          lastItem = (long) idx;
        }

        // Point to the next job.
        idx++;
      }

      // Add the output URLs to the pagination.
      pager.setCrawls(outputCrawls);
    }

    // Set the pagination information.
    pager.setPageInfo(getPageInfo(validLimit, lastItem, listSize, timeStamp));

    log.debug2("pager = {}", pager);
    return pager;
  }


}
