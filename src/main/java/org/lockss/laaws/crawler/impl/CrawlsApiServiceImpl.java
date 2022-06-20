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

import static org.lockss.servlet.DebugPanel.DEFAULT_CRAWL_PRIORITY;
import static org.lockss.servlet.DebugPanel.PARAM_CRAWL_PRIORITY;
import static org.lockss.util.rest.crawler.CrawlDesc.LOCKSS_CRAWLER_ID;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.ws.rs.NotFoundException;
import org.jetbrains.annotations.NotNull;
import org.lockss.app.LockssApp;
import org.lockss.app.LockssDaemon;
import org.lockss.app.ServiceBinding;
import org.lockss.app.ServiceDescr;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.crawler.CrawlerStatus.UrlErrorInfo;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.impl.external.ExternalCrawlProcessor;
import org.lockss.laaws.crawler.impl.external.ExternalCrawlReq;
import org.lockss.laaws.crawler.impl.external.ExternalCrawler;
import org.lockss.laaws.crawler.impl.external.ExternalCrawlerStatus;
import org.lockss.laaws.crawler.model.*;
import org.lockss.laaws.crawler.model.UrlError.SeverityEnum;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.ClassUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.RateLimiter;
import org.lockss.util.StringUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.CrawlKind;
import org.lockss.util.rest.crawler.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/** Service for accessing crawls. */
@Service
public class CrawlsApiServiceImpl extends BaseSpringApiServiceImpl
    implements CrawlsApiDelegate {

  enum COUNTER_KIND {
    errors,
    excluded,
    fetched,
    notmodified,
    parsed,
    pending
  }

  // Error Codes
  static final String NO_REPAIR_URLS = "No urls for repair.";
  static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit";
  static final String USE_FORCE_MESSAGE = "Use the 'force' parameter to override.";

  // A template URI for returning a counter for a specific URL list (eg. found
  // or parsed URLs).
  private static final String COUNTER_URI = "crawls/{jobId}/{counterName}";

  // A template URI for returning a counter for a list of URLs of a specific
  // mimeType.
  private static final String MIME_URI = "crawls/{jobId}/mimeType/{mimeType}";

  // The logger for this class.
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Requests a crawl.
   *
   * @param crawlDesc A CrawlDesc with the information about the requested crawl.
   * @return a {@code ResponseEntity<CrawlJob>} with the information about the job created to
   *     perform the crawl.
   * @see CrawlsApi#doCrawl
   */
  @Override
  public ResponseEntity<CrawlJob> doCrawl(CrawlDesc crawlDesc) {
    log.debug2("crawlDesc = {}", crawlDesc);

    CrawlJob crawlJob;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        String err = "The service has not been fully initialized";
        crawlJob = reportCrawlError(err, crawlDesc, HttpStatus.SERVICE_UNAVAILABLE);
        return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
      }
      // Get the crawler Id and Crawl kind
      String crawlerId = crawlDesc.getCrawler();
      CrawlKind crawlKind = crawlDesc.getCrawlKind();
      switch (crawlKind) {
        case NEWCONTENT:
          // Determine which crawler to use.
          if (crawlerId.equals(LOCKSS_CRAWLER_ID)) {
            ArchivalUnit au = getPluginManager().getAuFromId(crawlDesc.getAuId());
            if (au != null) {
              crawlJob =
                  startLockssCrawl(
                          au,
                          crawlDesc.getRefetchDepth(),
                          crawlDesc.getPriority(),
                          crawlDesc.isForceCrawl())
                      .crawlDesc(crawlDesc);

            } else {
              crawlJob = reportCrawlError(NO_SUCH_AU_ERROR_MESSAGE, crawlDesc, HttpStatus.NOT_FOUND);
            }
          } else if (getCrawlerIds().contains(crawlerId)) {
            crawlJob = startExternalCrawl(crawlDesc).crawlDesc(crawlDesc);
          } else {
            String errMsg = "Unknown crawler ID:'" + crawlerId + "'";
            crawlJob = reportCrawlError(errMsg, crawlDesc, HttpStatus.BAD_REQUEST);
          }
          break;
        case REPAIR:
          // For now, we always use the LOCKSS crawler for repairs.
          crawlJob =
              startLockssRepair(crawlDesc.getAuId(), crawlDesc.getRepairList())
                  .crawlDesc(crawlDesc);
          break;
        default:
          String errMsg = "Invalid crawl kind '" + crawlKind + "'";
          crawlJob = reportCrawlError(errMsg, crawlDesc, HttpStatus.BAD_REQUEST);
      }
    } catch (Exception ex) {
      String errMsg = "Cannot doCrawl() for crawlDesc = '" + crawlDesc + "'";
      crawlJob = reportCrawlError(errMsg, crawlDesc, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    log.debug2("crawlJob = {}", crawlJob);
    return new ResponseEntity<>(crawlJob, HttpStatus.valueOf(crawlJob.getStatus().getCode()));
  }

  @NotNull
  private CrawlJob reportCrawlError(String msg, CrawlDesc crawlDesc, HttpStatus status) {
    log.error("crawlDesc = {}", crawlDesc);
    CrawlJob crawlJob = new CrawlJob().crawlDesc(crawlDesc);
    return reportCrawlError(null, msg, crawlJob, status);
  }
  private CrawlJob reportCrawlError(Exception ex, String msg, CrawlJob crawlJob, HttpStatus status) {
    // log the error and any throwaable.
    if (ex != null)
      log.error(msg, ex);
    else
      log.error(msg);

    crawlJob.status(new Status().code(status.value()).msg(msg));
    log.debug2("crawlJob = {}", crawlJob);
    return crawlJob;
  }

  private ResponseEntity<CrawlStatus> handleCrawlErrorWithId(
      String msg, String jobId, HttpStatus httpStatus) {
    log.error(msg);
    log.error("jobId = {}", jobId);
    Status status = new Status().code(httpStatus.value()).msg(msg);
    CrawlStatus crawlStatus = new CrawlStatus().key(jobId).status(status);
    return new ResponseEntity<>(crawlStatus, httpStatus);
  }

  /**
   * Provides all (or a pageful of) the crawls in the service.
   *
   * @param limit An Integer with the maximum number of crawls per page.
   * @param continuationToken A String with the continuation token used to fetch the next page
   * @return a {@code ResponseEntity<JobPager>} with the information about the crawls.
   * @see CrawlsApi#getCrawls
   */
  @Override
  public ResponseEntity<JobPager> getCrawls(Integer limit, String continuationToken) {
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      JobPager pager = getJobsPager(limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message =
          "Cannot getCrawls() for limit = " + limit + ", continuationToken = " + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Deletes all of the currently queued and active crawl requests.
   *
   * @return a {@code ResponseEntity<Void>}.
   * @see CrawlsApi#deleteCrawls
   */
  @Override
  public ResponseEntity<Void> deleteCrawls() {
    log.debug2("Invoked");

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      getCrawlManager().deleteAllCrawls();

      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      String message = "Cannot deleteCrawls()";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Deletes a crawl previously added to the crawl queue, stopping the crawl if already running.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return a {@code ResponseEntity<CrawlStatus>} with the status of the deleted crawl.
   * @see CrawlsApi#deleteCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> deleteCrawlById(String jobId) {
    log.debug2("jobId = {}", jobId);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        return handleCrawlErrorWithId(message, jobId, HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      log.debug2("crawlerStatus = {}", crawlerStatus);

      if (crawlerStatus.isCrawlWaiting() || crawlerStatus.isCrawlActive()) {
        getCrawlManager().deleteCrawl(crawlerStatus.getAu());
      }

      return new ResponseEntity<>(getCrawlStatus(crawlerStatus), HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      return handleCrawlErrorWithId(message, jobId, HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot deleteCrawlById() for jobId = '" + jobId + "'";
      return handleCrawlErrorWithId(message, jobId, HttpStatus.INTERNAL_SERVER_ERROR);
    }
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

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        return handleCrawlErrorWithId(message, jobId, HttpStatus.SERVICE_UNAVAILABLE);
      }

      crawlStatus = getCrawlStatus(jobId);
      log.debug2("crawlStatus = {}", crawlStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      return handleCrawlErrorWithId(message, jobId, HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot getCrawlById() for jobId = '" + jobId + "'";
      return handleCrawlErrorWithId(message, jobId, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the error URLS in a crawl.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param limit An Integer with the maximum number of URLs per page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * Returns all (or a pageful of) the items in a crawl by MIME type..
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param type A String with the MIME type.
   * @param limit An Integer with the maximum number of URLs per s page.
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
      if (!waitConfig()) {
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
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
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
   * Return a CrawlStatus for the jobId.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   */
  private CrawlStatus getCrawlStatus(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlerStatus cs = getCrawlerStatus(jobId);

    CrawlStatus crawlStatus =
        new CrawlStatus()
            .key(cs.getKey())
            .auId(cs.getAuId())
            .auName(cs.getAuName())
            .type(cs.getType())
            .startUrls(ListUtil.fromIterable(cs.getStartUrls()))
            .startTime(cs.getStartTime())
            .endTime(cs.getEndTime())
            .status(new Status().code(cs.getCrawlStatus()).msg(cs.getCrawlStatusMsg()))
            .priority(cs.getPriority())
            .sources(ListUtil.immutableListOfType(cs.getSources(), String.class))
            .bytesFetched(cs.getContentBytesFetched())
            .depth(cs.getDepth())
            .refetchDepth(cs.getRefetchDepth())
            .proxy(cs.getProxy())
            .isActive(cs.isCrawlActive())
            .isError(cs.isCrawlError())
            .isWaiting(cs.isCrawlWaiting());

    CrawlerStatus.UrlCount urlCount;

    if (cs.getNumOfMimeTypes() > 0) {
      crawlStatus.mimeTypes(new ArrayList<>());

      for (String mimeType : cs.getMimeTypes()) {
        urlCount = cs.getMimeTypeCtr(mimeType);

        if (urlCount.getCount() > 0) {
          crawlStatus.addMimeTypesItem(getMimeCounter(jobId, mimeType, urlCount));
        }
      }
    }

    // add the url counters
    urlCount = cs.getErrorCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.errors(getCounter(COUNTER_KIND.errors, jobId, urlCount));
    }

    urlCount = cs.getExcludedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.excludedItems(getCounter(COUNTER_KIND.excluded, jobId, urlCount));
    }

    urlCount = cs.getFetchedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.fetchedItems(getCounter(COUNTER_KIND.fetched, jobId, urlCount));
    }

    urlCount = cs.getNotModifiedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.notModifiedItems(getCounter(COUNTER_KIND.notmodified, jobId, urlCount));
    }

    urlCount = cs.getParsedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.parsedItems(getCounter(COUNTER_KIND.parsed, jobId, urlCount));
    }

    urlCount = cs.getPendingCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.pendingItems(getCounter(COUNTER_KIND.pending, jobId, urlCount));
    }

    log.debug2("crawlStatus = {}", crawlStatus);
    return crawlStatus;
  }

  /**
   * @param kind
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param urlCount
   * @return
   */
  static Counter getCounter(COUNTER_KIND kind, String jobId, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", kind.name());
    String path =
        UriComponentsBuilder.fromPath(COUNTER_URI).buildAndExpand(uriVariables).toUriString();
    Counter ctr = new Counter();
    if (urlCount != null) {
      ctr.count(urlCount.getCount());
    } else {
      ctr.count(0);
    }
    ctr.itemsLink(path);
    return ctr;
  }

  /**
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param mimeType
   * @param urlCount
   * @return
   */
  static MimeCounter getMimeCounter(
      String jobId, String mimeType, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();

    uriVariables.put("jobId", jobId);
    uriVariables.put("mimeType", mimeType);
    String path =
        UriComponentsBuilder.fromPath(MIME_URI).buildAndExpand(uriVariables).toUriString();
    MimeCounter ctr = new MimeCounter();
    ctr.mimeType(mimeType);
    ctr.count(urlCount.getCount());
    ctr.counterLink(path);
    return ctr;
  }

  private CrawlJob startLockssCrawl(
      ArchivalUnit au, Integer depth, Integer requestedPriority, boolean force)
      throws InterruptedException {
    log.debug2("au = {}", au);
    log.debug2("depth = {}", depth);
    log.debug2("requestedPriority = {}", requestedPriority);
    log.debug2("force = {}", force);

    CrawlJob crawlJob = new CrawlJob();
    CrawlManagerImpl cmi = getCrawlManager();

    // Reset the rate limiter if the request is forced.
    if (force) {
      RateLimiter limiter = cmi.getNewContentRateLimiter(au);
      log.trace("limiter = {}", limiter);

      if (!limiter.isEventOk()) {
        limiter.unevent();
      }
    }

    // Handle eligibility for queuing the crawl.
    try {
      cmi.checkEligibleToQueueNewContentCrawl(au);
    } catch (CrawlManagerImpl.NotEligibleException.RateLimiter neerl) {
      String errorMsg =
          "AU has crawled recently (" + neerl.getMessage() + "). " + USE_FORCE_MESSAGE;
      return reportCrawlError(neerl, errorMsg, crawlJob, HttpStatus.BAD_REQUEST);
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      String errorMsg = "Can't enqueue crawl: " + nee.getMessage();
      return reportCrawlError(nee,errorMsg, crawlJob, HttpStatus.BAD_REQUEST);
    }

    String delayReason = null;

    try {
      cmi.checkEligibleForNewContentCrawl(au);
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      delayReason = "Start delayed due to: " + nee.getMessage();
      crawlJob.delayReason(delayReason);
    }

    // Get the crawl priority, specified or configured.
    int priority = getCrawlPriority(requestedPriority);

    // Create the crawl request.
    CrawlReq req;

    try {
      CrawlerStatus crawlerStatus = new CrawlerStatus(au, au.getStartUrls(), null);
      req = new CrawlReq(au, crawlerStatus);
      req.setPriority(priority);

      if (depth != null) {
        req.setRefetchDepth(depth.intValue());
      }
    } catch (RuntimeException e) {
      String errorMsg = "Can't enqueue crawl: ";
      return reportCrawlError(e, errorMsg, crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    crawlJob.creationDate(LocalDateTime.now());

    // Perform the crawl request.
    CrawlerStatus crawlerStatus = cmi.startNewContentCrawl(req);
    log.trace("crawlerStatus = {}", crawlerStatus);

    if (crawlerStatus.isCrawlError()) {
      String errorMsg =
          "Can't perform crawl for " + au + ": " + crawlerStatus.getCrawlErrorMsg();
      return reportCrawlError(null, errorMsg, crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return updateCrawlJob(crawlJob, crawlerStatus);
  }

  @NotNull
  private CrawlJob updateCrawlJob(CrawlJob crawlJob, CrawlerStatus crawlerStatus) {
    String jobId = crawlerStatus.getKey();
    log.trace("jobId = {}", jobId);
    crawlJob.jobId(jobId);

    long startTimeInMs = crawlerStatus.getStartTime();
    log.trace("startTimeInMs = {}", startTimeInMs);

    if (startTimeInMs >= 0) {
      crawlJob.startDate(localDateTimeFromEpochMs(startTimeInMs));
    }

    long endTimeInMs = crawlerStatus.getEndTime();
    log.trace("endTimeInMs = {}", endTimeInMs);

    if (endTimeInMs >= 0) {
      crawlJob.endDate(localDateTimeFromEpochMs(endTimeInMs));
    }

    ServiceBinding crawlerServiceBinding =
        LockssDaemon.getLockssDaemon().getServiceBinding(ServiceDescr.SVC_CRAWLER);
    log.trace("crawlerServiceBinding = {}", crawlerServiceBinding);

    if (crawlerServiceBinding != null) {
      String crawlerServiceUrl = crawlerServiceBinding.getRestStem();
      crawlJob.result(crawlerServiceUrl);
    }

    Status status = new Status().code(HttpStatus.ACCEPTED.value()).msg("Success");
    crawlJob.status(status);

    log.debug2("result = {}", crawlJob);
    return crawlJob;
  }

  /**
   * Provides the local timestamp that corresponds to a number of milliseconds since the epoch.
   *
   * @param epochMs A long with the number of milliseconds since the epoch to be converted.
   * @return a LocalDateTime with the result of the conversion.
   */
  private LocalDateTime localDateTimeFromEpochMs(long epochMs) {
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("UTC")).toLocalDateTime();
  }

  CrawlJob startLockssRepair(String auId, List<String> urls) {
    CrawlJob result;
    CrawlManagerImpl cmi = getCrawlManager();
    ArchivalUnit au = getPluginManager().getAuFromId(auId);

    // Handle a missing Archival Unit.
    if (au == null) {
      result = getRequestCrawlResult(auId, null, false, null, NO_SUCH_AU_ERROR_MESSAGE);
      return result;
    }
    // Handle missing Repair Urls.
    if (urls == null) {
      result = getRequestCrawlResult(auId, null, false, null, NO_REPAIR_URLS);
      return result;
    }

    cmi.startRepair(au, urls, null, null);
    return result = getRequestCrawlResult(auId, null, true, null, null);
  }

  private CrawlJob getRequestCrawlResult(
      String auId, Integer depth, boolean success, String delayReason, String errorMessage) {
    CrawlDesc crawlDesc = new CrawlDesc().auId(auId);
    Status status = new Status().msg(errorMessage);
    CrawlJob result = new CrawlJob().crawlDesc(crawlDesc).status(status).delayReason(delayReason);
    if (log.isDebugEnabled()) {
      log.debug("result = " + result);
    }
    return result;
  }

  /**
   * Provides a pageful of URLs.
   *
   * @param crawlerStatus A CrawlerStatus with the crawler status.
   * @param allUrls A List<String> with the complete collection of URLs to paginate.
   * @param requestLimit An Integer with the request maximum number of URLs per page.
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
   * Checks that a continuation token is valid.
   *
   * @param timeStamp A long with the timestamp used to validate the continuation token.
   * @param continuationToken A ContinuationToken with the continuation token to be validated.
   * @throws IllegalArgumentException if the passed continuation token is not valid.
   */
  private void validateContinuationToken(long timeStamp, ContinuationToken continuationToken)
      throws IllegalArgumentException {
    log.debug2("timeStamp = {}", timeStamp);
    log.debug2("continuationToken = {}", continuationToken);

    // Validate the continuation token.
    if (continuationToken.getTimestamp() != timeStamp) {
      String errMsg = "Invalid continuation token: " + continuationToken;
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }
  }

  /**
   * Checks that a limit field is valid.
   *
   * @param limit An Integer with the limit value to validate.
   * @return an Integer with the validated limit value.
   * @throws IllegalArgumentException if the passed limit value is not valid.
   */
  private Integer validateLimit(Integer limit) throws IllegalArgumentException {
    // check limit if assigned is greater than 0
    if (limit != null && limit.intValue() < 0) {
      String errMsg =
          "Invalid limit: limit must be a non-negative integer; " + "it was '" + limit + "'";
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }

    return limit;
  }

  UrlInfo makeUrlInfo(String url, CrawlerStatus status) {
    UrlInfo uInfo = new UrlInfo();
    uInfo.url(url);
    UrlErrorInfo errInfo = status.getErrorInfoForUrl(url);
    if (errInfo != null) {
      UrlError error = new UrlError();
      error.setMessage(errInfo.getMessage());
      error.setSeverity(SeverityEnum.fromValue(errInfo.getSeverity().name()));
      uInfo.setError(error);
    }
    uInfo.setReferrers(status.getReferrers(url));
    return uInfo;
  }

  /**
   * Provides a pageful of jobs.
   *
   * @param requestLimit An Integer with the request maximum number of jobs per page.
   * @param continuationToken A String with the continuation token provided in the request.
   * @return a UrlPager with the pageful of jobs.
   */
  JobPager getJobsPager(Integer requestLimit, String continuationToken) {
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
    List<CrawlerStatus> allJobs = getCrawlManager().getStatus().getCrawlerStatusList();
    log.trace("allJobs = {}", allJobs);

    // Get the size of the collection of all jobs.
    int listSize = allJobs.size();
    log.trace("listSize = {}", listSize);

    JobPager pager = new JobPager();
    Long lastItem = null;

    // Check whether there is anything to provide,
    if (listSize > 0) {
      // Yes: Validate the count of jobs to skip.
      if (lastJobToSkip + 1 >= listSize) {
        String  errMsg =
            "Invalid pagination request: startAt = "
                + (lastJobToSkip + 1)
                + ", Total = "
                + listSize;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }

      List<CrawlStatus> outputJobs = new ArrayList<>();

      // Get the number of jobs to return.
      int outputSize = (int) (listSize - (lastJobToSkip + 1));

      if (validLimit != null && validLimit > 0 && validLimit < outputSize) {
        outputSize = validLimit;
      }

      log.trace("outputSize = {}", outputSize);

      int idx = 0;

      // Loop through all the jobs until the output size has been reached.
      while (outputJobs.size() < outputSize) {
        log.trace("idx = {}", idx);

        // Check whether this job does not need to be skipped.
        if (idx > lastJobToSkip) {
          // Yes: Get it.
          CrawlerStatus crawlerStatus = allJobs.get(idx);
          log.trace("crawlerStatus = {}", crawlerStatus);

          // Add it to the output collection.
          outputJobs.add(getCrawlStatus(crawlerStatus));

          // Record that it is the last one so far.
          lastItem = (long) idx;
        }

        // Point to the next job.
        idx++;
      }

      // Add the output URLs to the pagination.
      pager.setJobs(outputJobs);
    }

    // Set the pagination information.
    pager.setPageInfo(getPageInfo(validLimit, lastItem, listSize, timeStamp));

    log.debug2("pager = {}", pager);
    return pager;
  }

  /**
   * Provides the pagination information.
   *
   * @param resultsPerPage An Integer with the number of results per page.
   * @param lastElement A Long with the index of the last element in the page.
   * @param totalCount An int with the total number of elements.
   * @param timeStamp A Long with the timestamp to use in the continuation token, if necessary.
   * @return a PageInfo with the pagination information.
   */
  private PageInfo getPageInfo(
      Integer resultsPerPage, Long lastElement, int totalCount, Long timeStamp) {
    log.debug2("resultsPerPage = {}", resultsPerPage);
    log.debug2("lastElement = {}", lastElement);
    log.debug2("totalCount = {}", totalCount);
    log.debug2("timeStamp = {}", timeStamp);

    PageInfo pi = new PageInfo();

    pi.setTotalCount(totalCount);
    pi.setResultsPerPage(resultsPerPage);

    ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentRequest();

    pi.setCurLink(builder.cloneBuilder().toUriString());

    String nextToken = null;

    // Determine whether a continuation token needs to be provided.
    if (lastElement != null && lastElement < totalCount - 1) {
      // Yes: Create it.
      nextToken = new ContinuationToken(timeStamp, lastElement).toToken();
      log.trace("nextToken = {}", nextToken);

      pi.setContinuationToken(nextToken);
      builder.replaceQueryParam("continuationToken", nextToken);
      pi.setNextLink(builder.toUriString());
    }

    log.debug2("pi = {}", pi);
    return pi;
  }

  /**
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return
   * @throws NotFoundException
   */
  private CrawlerStatus getCrawlerStatus(String jobId) throws NotFoundException {
    CrawlerStatus cs = getCrawlManager().getStatus().getCrawlerStatus(jobId);

    if (cs == null) {
      String message = "No Job found for '" + jobId + "'";
      log.warn(message);
      throw new NotFoundException();
    }

    return cs;
  }

  private static CrawlStatus getCrawlStatus(CrawlerStatus cs) {
    String key = cs.getKey();
    CrawlStatus crawlStatus =
        new CrawlStatus()
            .key(cs.getKey())
            .auId(cs.getAuId())
            .auName(cs.getAuName())
            .type(cs.getType())
            .startTime(cs.getStartTime())
            .endTime(cs.getEndTime())
            .status(new Status().code(cs.getCrawlStatus()).msg(cs.getCrawlStatusMsg()))
            .isWaiting(cs.isCrawlWaiting())
            .isActive(cs.isCrawlActive())
            .isError(cs.isCrawlError())
            .priority(cs.getPriority())
            .bytesFetched(cs.getContentBytesFetched())
            .depth(cs.getDepth())
            .refetchDepth(cs.getRefetchDepth())
            .proxy(cs.getProxy())
            .fetchedItems(getCounter(COUNTER_KIND.fetched, key, cs.getFetchedCtr()))
            .excludedItems(getCounter(COUNTER_KIND.excluded, key, cs.getFetchedCtr()))
            .notModifiedItems(getCounter(COUNTER_KIND.notmodified, key, cs.getNotModifiedCtr()))
            .parsedItems(getCounter(COUNTER_KIND.parsed, key, cs.getParsedCtr()))
            .sources(cs.getSources())
            .pendingItems(getCounter(COUNTER_KIND.pending, key, cs.getPendingCtr()))
            .errors(getCounter(COUNTER_KIND.errors, key, cs.getErrorCtr()));

    // Handle the crawl status seperately
    crawlStatus.getStartUrls().addAll(cs.getStartUrls());

    // Add the MIME types array if needed.
    Collection<String> mimeTypes = cs.getMimeTypes();

    if (mimeTypes != null && !mimeTypes.isEmpty()) {
      List<MimeCounter> typeList = new ArrayList<>();

      for (String mtype : mimeTypes) {
        typeList.add(getMimeCounter(key, mtype, cs.getMimeTypeCtr(mtype)));
      }

      crawlStatus.setMimeTypes(typeList);
    }

    return crawlStatus;
  }

  private CrawlJob startExternalCrawl(CrawlDesc crawlDesc) throws InterruptedException {
    log.debug2("crawlDesc = {}", crawlDesc);
    CrawlJob crawlJob = new CrawlJob();
    CrawlManagerImpl cmi = getCrawlManager();
    int priority = getCrawlPriority(crawlDesc.getPriority());
    // Create the crawl request.
    ExternalCrawlReq extCrawlReq;
    // Get the crawler if one is defined.
    // call crawler.getStatus to get the crawlerstatus object for this crawler.
    try {
      CrawlerStatus crawlerStatus =
          new ExternalCrawlerStatus(
              crawlDesc.getCrawler(),
              crawlDesc.getAuId(),
              crawlDesc.getCrawlList(),
              crawlDesc.getCrawlKind().name());
      extCrawlReq = new ExternalCrawlReq(crawlDesc, crawlerStatus);
      extCrawlReq.setPriority(priority);

      if (crawlDesc.getCrawlDepth() != null) {
        extCrawlReq.setRefetchDepth(crawlDesc.getCrawlDepth().intValue());
      }
    } catch (IllegalArgumentException iae) {
      String errMsg =
          "Invalid external crawl specification for AU "
              + crawlDesc.getAuId()
              + ": "
              + iae.getMessage();
      return reportCrawlError(iae, errMsg,crawlJob, HttpStatus.BAD_REQUEST);
    } catch (IOException ioe) {
      String errMsg = "Can't parse crawl description for AU ";
      return reportCrawlError(ioe, errMsg,crawlJob, HttpStatus.BAD_REQUEST);
    } catch (RuntimeException e) {
      String errMsg = "Can't enqueue crawl for AU ";
      return reportCrawlError(e, errMsg, crawlJob, HttpStatus.INTERNAL_SERVER_ERROR );
    }

    crawlJob.creationDate(LocalDateTime.now());

    // Perform the crawl request.
    CrawlerStatus crawlerStatus = new ExternalCrawlProcessor(cmi).startNewContentCrawl(extCrawlReq);
    log.trace("crawlerStatus = {}", crawlerStatus);

    if (crawlerStatus.isCrawlError()) {
      String errMsg = "Can't perform crawl for " + crawlDesc.getAuId()
              + ": " + crawlerStatus.getCrawlErrorMsg();
      return reportCrawlError(null, errMsg,crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return updateCrawlJob(crawlJob, crawlerStatus);
  }

  //  CrawlJob startExternalRepair(String auId, List<String> urls) {
  //    CrawlJob result;
  //    CrawlManagerImpl cmi = getCrawlManager();
  //    ArchivalUnit au = getPluginManager().getAuFromId(auId);
  //
  //    // Handle a missing Archival Unit.
  //    if (au == null) {
  //      result = getRequestCrawlResult(auId, null, false, null, NO_SUCH_AU_ERROR_MESSAGE);
  //      return result;
  //    }
  //    // Handle missing Repair Urls.
  //    if (urls == null) {
  //      result = getRequestCrawlResult(auId, null, false, null, NO_REPAIR_URLS);
  //      return result;
  //    }
  //    cmi.startRepair(au, urls, null, null);
  //   result = getRequestCrawlResult(auId, null, true, null, null);
  //   return result;
  //  }

  List<String> getCrawlerIds() {
    Configuration config = ConfigManager.getCurrentConfig();
    return config.getList(
        CrawlersApiServiceImpl.CRAWLER_IDS, CrawlersApiServiceImpl.defaultCrawlerIds);
  }

  ExternalCrawler getExternalCrawler(String crawlerId) {
    Configuration config = ConfigManager.getCurrentConfig();
    String className = CrawlersApiServiceImpl.PREFIX + crawlerId + ".crawler";
    ExternalCrawler extCrawler = null;
    // First look for a loadable definition.
    if (StringUtil.isNullString(className)) {
      try {
        extCrawler = ClassUtil.instantiate(className, ExternalCrawler.class);
      } catch (IllegalStateException ise) {
        log.error("Unable to instantiate class " + className + " for " + crawlerId + "");
      }
    }
    return extCrawler;
  }

  /**
   * Return the crawl priority, specified or configured.
   *
   * @param requestedPriority the priority from the crawl request
   * @return the priority to use for the crawl.
   */
  int getCrawlPriority(Integer requestedPriority) {
    int priority;

    if (requestedPriority != null) {
      priority = requestedPriority;
    } else {
      Configuration config = ConfigManager.getCurrentConfig();
      priority = config.getInt(PARAM_CRAWL_PRIORITY, DEFAULT_CRAWL_PRIORITY);
    }
    log.trace("priority = " + priority);
    return priority;
  }

  /**
   * Provides the crawl manager.
   *
   * @return a CrawlManagerImpl with the crawl manager implementation.
   */
  private CrawlManagerImpl getCrawlManager() {
    CrawlManagerImpl crawlManager = null;
    CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);

    if (cmgr instanceof CrawlManagerImpl) {
      crawlManager = (CrawlManagerImpl) cmgr;
    }

    return crawlManager;
  }

  /**
   * Provides the plugin manager.
   *
   * @return the current Lockss PluginManager.
   */
  private PluginManager getPluginManager() {
    return (PluginManager) LockssApp.getManagerByKeyStatic(LockssApp.PLUGIN_MANAGER);
  }
}
