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
import static org.lockss.util.rest.crawler.CrawlDesc.WGET_CRAWLER_ID;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.lockss.app.LockssApp;
import org.lockss.app.LockssDaemon;
import org.lockss.app.ServiceBinding;
import org.lockss.app.ServiceDescr;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus.UrlErrorInfo;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.model.Counter;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.MimeCounter;
import org.lockss.laaws.crawler.model.PageInfo;
import org.lockss.laaws.crawler.model.UrlError;
import org.lockss.laaws.crawler.model.UrlError.SeverityEnum;
import org.lockss.laaws.crawler.model.UrlInfo;
import org.lockss.laaws.crawler.model.UrlPager;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.laaws.crawler.wget.WgetCrawlProcessor;
import org.lockss.laaws.crawler.wget.WgetCrawlReq;
import org.lockss.laaws.crawler.wget.WgetCrawlerStatus;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.ListUtil;
import org.lockss.util.RateLimiter;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.CrawlKind;
import org.lockss.util.rest.crawler.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Service for accessing crawls.
 */
@Service
public class CrawlsApiServiceImpl extends BaseSpringApiServiceImpl
    implements CrawlsApiDelegate {

  enum COUNTER_KIND {
    errors, excluded, fetched, notmodified, parsed, pending
  }

  static final String NO_REPAIR_URLS = "No urls for repair.";
  static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit";
  static final String USE_FORCE_MESSAGE =
      "Use the 'force' parameter to override.";

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
   * @param crawlDesc A CrawlDesc with the information about the requested
   *                  crawl.
   * @return a {@code ResponseEntity<CrawlJob>} with the information about the
   *         job created to perform the crawl.
   * @see CrawlsApi#doCrawl
   */
  @Override
  public ResponseEntity<CrawlJob> doCrawl(CrawlDesc crawlDesc) {
    log.debug2("crawlDesc = {}", crawlDesc);

    CrawlJob crawlJob = new CrawlJob().crawlDesc(crawlDesc);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("crawlDesc = {}", crawlDesc);
        HttpStatus httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
        crawlJob.status(new Status().code(httpStatus.value()).msg(message));
        log.debug2("crawlJob = {}", crawlJob);
        return new ResponseEntity<>(crawlJob, httpStatus);
      }

      String crawler = crawlDesc.getCrawler();

      // Validate the specified crawler.
      if (!CrawlersApiServiceImpl.getCrawlerIds().contains(crawler)) {
        String message = "Invalid crawler '" + crawler + "'";
        log.error(message);
        log.error("crawlDesc = {}", crawlDesc);
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        crawlJob.status(new Status().code(httpStatus.value()).msg(message));
        log.debug2("crawlJob = {}", crawlJob);
        return new ResponseEntity<>(crawlJob, httpStatus);
      }

      // Determine which crawler to use.
      switch (crawler) {
        case LOCKSS_CRAWLER_ID:
          // Get the Archival Unit to be crawled.
          ArchivalUnit au = getPluginManager().getAuFromId(crawlDesc.getAuId());

          // Handle a missing Archival Unit.
          if (au == null) {
            String message = NO_SUCH_AU_ERROR_MESSAGE;
            log.error(message);
            log.error("crawlDesc = {}", crawlDesc);
            HttpStatus httpStatus = HttpStatus.NOT_FOUND;
            crawlJob.status(new Status().code(httpStatus.value()).msg(message));
            log.debug2("crawlJob = {}", crawlJob);
            return new ResponseEntity<>(crawlJob, httpStatus);
          }

          CrawlKind crawlKind = crawlDesc.getCrawlKind();

          // Determine which kind of crawl is being requested.
          switch (crawlKind) {
            case NEWCONTENT:
              crawlJob = startLockssCrawl(au, crawlDesc.getRefetchDepth(),
        	  crawlDesc.getPriority(), crawlDesc.isForceCrawl())
              .crawlDesc(crawlDesc);
              break;
            case REPAIR:
              crawlJob = startLockssRepair(crawlDesc.getAuId(),
        	  crawlDesc.getRepairList()).crawlDesc(crawlDesc);
              break;
            default:
              String message = "Invalid crawl kind '" + crawlKind + "'";
              log.error(message);
              log.error("crawlDesc = {}", crawlDesc);
              HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
              crawlJob.status(
        	  new Status().code(httpStatus.value()).msg(message));
              log.debug2("crawlJob = {}", crawlJob);
              return new ResponseEntity<>(crawlJob, httpStatus);
          }

          break;
        case WGET_CRAWLER_ID:
          crawlKind = crawlDesc.getCrawlKind();

          // Determine which kind of crawl is being requested.
          switch (crawlKind) {
            case NEWCONTENT:
              crawlJob = startWgetCrawl(crawlDesc).crawlDesc(crawlDesc);
              break;
            case REPAIR:
              crawlJob = startWgetRepair(crawlDesc.getAuId(),
        	  crawlDesc.getRepairList()).crawlDesc(crawlDesc);
              break;
            default:
              String message = "Invalid crawl kind '" + crawlKind + "'";
              log.error(message);
              log.error("crawlDesc = {}", crawlDesc);
              HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
              crawlJob.status(
        	  new Status().code(httpStatus.value()).msg(message));
              log.debug2("crawlJob = {}", crawlJob);
              return new ResponseEntity<>(crawlJob, httpStatus);
          }

          break;
        default:
          String message = "Unimplemented crawler '" + crawler + "'";
          log.error(message);
          log.error("crawlDesc = {}", crawlDesc);
          HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
          crawlJob.status(new Status().code(httpStatus.value()).msg(message));
          log.debug2("crawlJob = {}", crawlJob);
          return new ResponseEntity<>(crawlJob, httpStatus);
      }

      log.debug2("crawlJob = {}", crawlJob);
      return new ResponseEntity<>(crawlJob,
	  HttpStatus.valueOf(crawlJob.getStatus().getCode()));
    } catch (Exception e) {
      String message = "Cannot doCrawl() for crawlDesc = '" + crawlDesc + "'";
      log.error(message, e);
      HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
      crawlJob.status(new Status().code(httpStatus.value()).msg(message));
      log.debug2("crawlJob = {}", crawlJob);
      return new ResponseEntity<>(crawlJob, httpStatus);
    }
  }

  /**
   * Provides all (or a pageful of) the crawls in the service.
   * 
   * @param limit             An Integer with the maximum number of crawls per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<JobPager>} with the information about the
   *         crawls.
   * @see CrawlsApi#getCrawls
   */
  @Override
  public ResponseEntity<JobPager> getCrawls(Integer limit,
      String continuationToken) {
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      JobPager pager = getJobsPager(limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message = "Cannot getCrawls() for limit = " + limit
	  + ", continuationToken = " + continuationToken;
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
   * Deletes a crawl previously added to the crawl queue, stopping the crawl if
   * already running.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return a {@code ResponseEntity<CrawlStatus>} with the status of the
   *         deleted crawl.
   * @see CrawlsApi#deleteCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> deleteCrawlById(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlStatus crawlStatus = null;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
	String message = "The service has not been fully initialized";
	log.error(message);
	log.error("jobId = {}", jobId);
	HttpStatus httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
	Status status = new Status().code(httpStatus.value()).msg(message);
	crawlStatus = new CrawlStatus().key(jobId).status(status);
	return new ResponseEntity<>(crawlStatus, httpStatus);
      }

      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      log.debug2("crawlerStatus = {}", crawlerStatus);

      if (crawlerStatus.isCrawlWaiting() || crawlerStatus.isCrawlActive()) {
	//TODO: unhighlight when we push the corresponding change in lockss-core
	getCrawlManager().deleteCrawl(crawlerStatus.getAu());
      }

      return new ResponseEntity<>(getCrawlStatus(crawlerStatus),
	  HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      HttpStatus httpStatus = HttpStatus.NOT_FOUND;
      Status status = new Status().code(httpStatus.value()).msg(message);
      crawlStatus = new CrawlStatus().key(jobId).status(status);
      return new ResponseEntity<>(crawlStatus, httpStatus);
    } catch (Exception e) {
      String message = "Cannot deleteCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
      Status status = new Status().code(httpStatus.value()).msg(message);
      crawlStatus = new CrawlStatus().key(jobId).status(status);
      return new ResponseEntity<>(crawlStatus, httpStatus);
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

    CrawlStatus crawlStatus = null;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("jobId = {}", jobId);
        HttpStatus httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
        Status status = new Status().code(httpStatus.value()).msg(message);
        crawlStatus = new CrawlStatus().key(jobId).status(status);
        return new ResponseEntity<>(crawlStatus, httpStatus);
      }

      crawlStatus = getCrawlStatus(jobId);
      log.debug2("crawlStatus = {}", crawlStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      HttpStatus httpStatus = HttpStatus.NOT_FOUND;
      Status status = new Status().code(httpStatus.value()).msg(message);
      crawlStatus = new CrawlStatus().key(jobId).status(status);
      return new ResponseEntity<>(crawlStatus, httpStatus);
    } catch (Exception e) {
      String message = "Cannot getCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
      Status status = new Status().code(httpStatus.value()).msg(message);
      crawlStatus = new CrawlStatus().key(jobId).status(status);
      return new ResponseEntity<>(crawlStatus, httpStatus);
    }
  }

  /**
   * Returns all (or a pageful of) the error URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         error URLs.
   * @see CrawlsApi#getCrawlErrors
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlErrors(String jobId, Integer limit,
      String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
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
      String message = "Cannot getCrawlErrors() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the excluded URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         excluded URLs.
   * @see CrawlsApi#getCrawlExcluded
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlExcluded(String jobId, Integer limit,
      String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
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
      String message = "Cannot getCrawlExcluded() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the fetched URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         fetched URLs.
   * @see CrawlsApi#getCrawlFetched
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlFetched(String jobId, Integer limit,
      String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsFetched();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message = "Cannot getCrawlFetched() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the not-modified URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         not-modified URLs.
   * @see CrawlsApi#getCrawlNotModified
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlNotModified(String jobId,
      Integer limit, String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsNotModified();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message = "Cannot getCrawlNotModified() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the parsed URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         parsed URLs.
   * @see CrawlsApi#getCrawlParsed
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlParsed(String jobId, Integer limit,
      String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsParsed();
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message = "Cannot getCrawlParsed() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the pending URLS in a crawl.
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param limit             An Integer with the maximum number of URLs per
   *                          page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         pending URLs.
   * @see CrawlsApi#getCrawlPending
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlPending(String jobId, Integer limit,
      String continuationToken) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
	// Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
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
      String message = "Cannot getCrawlPending() for jobId '" + jobId
	  + "', limit = " + limit + ", continuationToken = "
	  + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns all (or a pageful of) the items in a crawl by MIME type..
   *
   * @param jobId             A String with the identifier assigned to the crawl
   *                          when added.
   * @param type              A String with the MIME type.
   * @param limit             An Integer with the maximum number of URLs per
   *                         s page.
   * @param continuationToken A String with the continuation token used to fetch
   *                          the next page
   * @return a {@code ResponseEntity<UrlPager>} with the information about the
   *         items.
   * @see CrawlsApi#getCrawlByMimeType
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlByMimeType(String jobId, String type,
      Integer limit, String continuationToken) {
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
        log.error("limit = {}, continuationToken = {}", limit,
            continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsOfMimeType(type);
      UrlPager pager = getUrlPager(status, urls, limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (IllegalArgumentException iae) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      String message = "Cannot getCrawlByMimeType() for jobId '" + jobId
	  + "', type = '" + type + "', limit = " + limit
	  + ", continuationToken = " + continuationToken;
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return a CrawlStatus for the jobId.
   * @param jobId A String with the identifier assigned to the crawl when added.
   */
  private CrawlStatus getCrawlStatus(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlerStatus cs = getCrawlerStatus(jobId);

    CrawlStatus crawlStatus = new CrawlStatus()
	.key(cs.getKey())
	.auId(cs.getAuId())
	.auName(cs.getAuName())
	.type(cs.getType())
	.startUrls(ListUtil.fromIterable(cs.getStartUrls()))
	.startTime(cs.getStartTime())
	.endTime(cs.getEndTime())
	.status(new Status().code(cs.getCrawlStatus())
	    .msg(cs.getCrawlStatusMsg()))
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
      crawlStatus.mimeTypes(new ArrayList<MimeCounter>());

      for (String mimeType : cs.getMimeTypes()) {
        urlCount = cs.getMimeTypeCtr(mimeType);

        if (urlCount.getCount() > 0) {
          crawlStatus.addMimeTypesItem(getMimeCounter(jobId, mimeType,
              urlCount));
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
      crawlStatus.excludedItems(getCounter(COUNTER_KIND.excluded, jobId,
	  urlCount));
    }

    urlCount = cs.getFetchedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.fetchedItems(getCounter(COUNTER_KIND.fetched, jobId,
	  urlCount));
    }

    urlCount = cs.getNotModifiedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.notModifiedItems(getCounter(COUNTER_KIND.notmodified, jobId,
	  urlCount));
    }

    urlCount = cs.getParsedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.parsedItems(getCounter(COUNTER_KIND.parsed, jobId, urlCount));
    }

    urlCount = cs.getPendingCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.pendingItems(getCounter(COUNTER_KIND.pending, jobId,
	  urlCount));
    }

    log.debug2("crawlStatus = {}", crawlStatus);
    return crawlStatus;
  }

  /**
   * 
   * @param kind
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param urlCount
   * @return
   */
  static Counter getCounter(COUNTER_KIND kind, String jobId,
      CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", kind.name());
    String path = UriComponentsBuilder.fromPath(COUNTER_URI)
	.buildAndExpand(uriVariables).toUriString();
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
   * 
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param mimeType
   * @param urlCount
   * @return
   */
  static MimeCounter getMimeCounter(String jobId, String mimeType,
      CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();

    uriVariables.put("jobId", jobId);
    uriVariables.put("mimeType", mimeType);
    String path = UriComponentsBuilder.fromPath(MIME_URI)
	.buildAndExpand(uriVariables).toUriString();
    MimeCounter ctr = new MimeCounter();
    ctr.mimeType(mimeType);
    ctr.count(urlCount.getCount());
    ctr.counterLink(path);
    return ctr;
  }

  private CrawlJob startLockssCrawl(ArchivalUnit au, Integer depth,
      Integer requestedPriority, boolean force) throws InterruptedException {
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
      String errorMessage = "AU has crawled recently (" + neerl.getMessage()
	+ "). " + USE_FORCE_MESSAGE;
      Status status =
	  new Status().code(HttpStatus.BAD_REQUEST.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      String errorMessage = "Can't enqueue crawl: " + nee.getMessage();
      Status status =
	  new Status().code(HttpStatus.BAD_REQUEST.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    }

    String delayReason = null;

    try {
      cmi.checkEligibleForNewContentCrawl(au);
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      delayReason = "Start delayed due to: " + nee.getMessage();
      crawlJob.delayReason(delayReason);
    }

    // Get the crawl priority, specified or configured.
    int priority = 0;

    if (requestedPriority != null) {
      priority = requestedPriority.intValue();
    } else {
      Configuration config = ConfigManager.getCurrentConfig();
      priority = config.getInt(PARAM_CRAWL_PRIORITY, DEFAULT_CRAWL_PRIORITY);
    }

    log.trace("priority = " + priority);

    // Create the crawl request.
    CrawlReq req;

    try {
      CrawlerStatus crawlerStatus =
	  new CrawlerStatus(au, au.getStartUrls(), null);
      req = new CrawlReq(au, crawlerStatus);
      req.setPriority(priority);

      if (depth != null) {
	req.setRefetchDepth(depth.intValue());
      }
    } catch (RuntimeException e) {
      String errorMessage = "Can't enqueue crawl: ";
      log.error(errorMessage + au, e);
      Status status = new Status()
	  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    }

    crawlJob.creationDate(LocalDateTime.now());

    // Perform the crawl request.
    CrawlerStatus crawlerStatus = cmi.startNewContentCrawl(req);
    log.trace("crawlerStatus = {}", crawlerStatus);

    if (crawlerStatus.isCrawlError()) {
      String errorMessage = "Can't perform crawl for " + au + ": "
	  + crawlerStatus.getCrawlErrorMsg();
      log.error(errorMessage);
      Status status = new Status()
	  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    }

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

    ServiceBinding crawlerServiceBinding = LockssDaemon.getLockssDaemon()
	.getServiceBinding(ServiceDescr.SVC_CRAWLER);
    log.trace("crawlerServiceBinding = {}", crawlerServiceBinding);

    if (crawlerServiceBinding != null) {
      String crawlerServiceUrl = crawlerServiceBinding.getRestStem();
      crawlJob.result(crawlerServiceUrl);
    }

    Status status =
	new Status().code(HttpStatus.ACCEPTED.value()).msg("Success");
    crawlJob.status(status);

    log.debug2("result = {}", crawlJob);
    return crawlJob;
  }

  /**
   * Provides the local timestamp that corresponds to a number of milliseconds
   * since the epoch.
   * 
   * @param epochMs A long with the number of milliseconds since the epoch to be
   *                converted.
   * @return a LocalDateTime with the result of the conversion.
   */
  private LocalDateTime localDateTimeFromEpochMs(long epochMs) {
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("UTC"))
	.toLocalDateTime();
  }

  CrawlJob startLockssRepair(String auId, List<String> urls) {
    CrawlJob result;
    CrawlManagerImpl cmi = getCrawlManager();
    ArchivalUnit au = getPluginManager().getAuFromId(auId);

    // Handle a missing Archival Unit.
    if (au == null) {
      result = getRequestCrawlResult(auId, null, false, null,
	  NO_SUCH_AU_ERROR_MESSAGE);
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

  private CrawlJob getRequestCrawlResult(String auId, Integer depth,
      boolean success, String delayReason, String errorMessage) {
    CrawlDesc crawlDesc = new CrawlDesc().auId(auId);
    Status status = new Status().msg(errorMessage);
    CrawlJob result = new CrawlJob().crawlDesc(crawlDesc).status(status)
	.delayReason(delayReason);
    if (log.isDebugEnabled()) {
      log.debug("result = " + result);
    }
    return result;
  }

  /**
   * Provides a pageful of URLs.
   * 
   * @param crawlerStatus     A CrawlerStatus with the crawler status.
   * @param allUrls           A List<String> with the complete collection of
   *                          URLs to paginate.
   * @param requestLimit      An Integer with the request maximum number of URLs
   *                          per page.
   * @param continuationToken A String with the continuation token provided in
   *                          the request.
   * @return a UrlPager with the pageful of URLs.
   */
  UrlPager getUrlPager(CrawlerStatus crawlerStatus, List<String> allUrls,
      Integer requestLimit, String continuationToken) {
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
        String errorMessage = "Invalid pagination request: startAt = "
  	  + (lastUrlToSkip + 1) + ", Total = " + listSize;
        log.warn(errorMessage);
        throw new IllegalArgumentException(errorMessage);
      }

      List<UrlInfo> outputUrls = new ArrayList<>();

      // Get the number of URLs to return.
      int outputSize = (int)(listSize - (lastUrlToSkip + 1));

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
          lastItem = (long)idx;
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
   * @param timeStamp         A long with the timestamp used to validate the
   *                          continuation token.
   * @param continuationToken A ContinuationToken with the continuation token to
   *                          be validated.
   * @throws IllegalArgumentException if the passed continuation token is not
   *                                  valid.
   */
  private void validateContinuationToken(long timeStamp,
      ContinuationToken continuationToken) throws IllegalArgumentException {
    log.debug2("timeStamp = {}", timeStamp);
    log.debug2("continuationToken = {}", continuationToken);

    // Validate the continuation token.
    if (continuationToken.getTimestamp().longValue() != timeStamp) {
      String errorMessage = "Invalid continuation token: " + continuationToken;
      log.warn(errorMessage);
      throw new IllegalArgumentException(errorMessage);
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
      String errMsg = "Invalid limit: limit must be a non-negative integer; "
	  + "it was '" + limit + "'";
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
   * @param requestLimit      An Integer with the request maximum number of jobs
   *                          per page.
   * @param continuationToken A String with the continuation token provided in
   *                          the request.
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
    List<CrawlerStatus> allJobs =
	getCrawlManager().getStatus().getCrawlerStatusList();
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
        String errorMessage = "Invalid pagination request: startAt = "
  	  + (lastJobToSkip + 1) + ", Total = " + listSize;
        log.warn(errorMessage);
        throw new IllegalArgumentException(errorMessage);
      }

      List<CrawlStatus> outputJobs = new ArrayList<>();

      // Get the number of jobs to return.
      int outputSize = (int)(listSize - (lastJobToSkip + 1));

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
          lastItem = (long)idx;
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
   * @param lastElement    A Long with the index of the last element in the
   *                       page.
   * @param totalCount     An int with the total number of elements.
   * @param timeStamp      A Long with the timestamp to use in the continuation
   *                       token, if necessary.
   * @return a PageInfo with the pagination information.
   */
  private PageInfo getPageInfo(Integer resultsPerPage, Long lastElement,
      int totalCount, Long timeStamp) {
    log.debug2("resultsPerPage = {}", resultsPerPage);
    log.debug2("lastElement = {}", lastElement);
    log.debug2("totalCount = {}", totalCount);
    log.debug2("timeStamp = {}", timeStamp);

    PageInfo pi = new PageInfo();

    pi.setTotalCount(totalCount);
    pi.setResultsPerPage(resultsPerPage);

    ServletUriComponentsBuilder builder =
	ServletUriComponentsBuilder.fromCurrentRequest();

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
   * 
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return
   * @throws NotFoundException
   */
  private CrawlerStatus getCrawlerStatus(String jobId)
      throws NotFoundException {
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
    CrawlStatus crawlStatus = new CrawlStatus()
        .key(cs.getKey())
        .auId(cs.getAuId())
        .auName(cs.getAuName())
        .type(cs.getType())
        .startTime(cs.getStartTime())
        .endTime(cs.getEndTime())
        .status(new Status().code(cs.getCrawlStatus())
            .msg(cs.getCrawlStatusMsg()))
        .isWaiting(cs.isCrawlWaiting())
        .isActive(cs.isCrawlActive())
        .isError(cs.isCrawlError())
        .priority(cs.getPriority())
        .bytesFetched(cs.getContentBytesFetched())
        .depth(cs.getDepth())
        .refetchDepth(cs.getRefetchDepth())
        .proxy(cs.getProxy())
        .fetchedItems(getCounter(COUNTER_KIND.fetched, key, cs.getFetchedCtr()))
        .excludedItems(getCounter(COUNTER_KIND.excluded, key,
            cs.getFetchedCtr()))
        .notModifiedItems(getCounter(COUNTER_KIND.notmodified, key,
            cs.getNotModifiedCtr()))
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

  private CrawlJob startWgetCrawl(CrawlDesc crawlDesc)
      throws InterruptedException {
    log.debug2("crawlDesc = {}", crawlDesc);

    CrawlJob crawlJob = new CrawlJob();

    CrawlManagerImpl cmi = getCrawlManager();

    // Get the crawl priority, specified or configured.
    int priority = 0;

    if (crawlDesc.getPriority() != null) {
      priority = crawlDesc.getPriority().intValue();
    } else {
      Configuration config = ConfigManager.getCurrentConfig();
      priority = config.getInt(PARAM_CRAWL_PRIORITY, DEFAULT_CRAWL_PRIORITY);
    }

    log.trace("priority = " + priority);

    // Create the crawl request.
    WgetCrawlReq wgetCrawlReq;

    try {
      CrawlerStatus crawlerStatus = new WgetCrawlerStatus(crawlDesc.getAuId(),
	  crawlDesc.getCrawlList(), crawlDesc.getCrawlKind().name());
      wgetCrawlReq = new WgetCrawlReq(crawlDesc, crawlerStatus);
      wgetCrawlReq.setPriority(priority);

      if (crawlDesc.getCrawlDepth() != null) {
	wgetCrawlReq.setRefetchDepth(crawlDesc.getCrawlDepth().intValue());
      }
    } catch (IllegalArgumentException iae) {
      String errorMessage = "Invalid wget crawl specification for AU "
	  + crawlDesc.getAuId() + ": " + iae.getMessage();
      log.error(errorMessage);
      Status status =
	  new Status().code(HttpStatus.BAD_REQUEST.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    } catch (IOException ioe) {
      String errorMessage = "Can't parse crawl description for AU ";
      log.error(errorMessage + crawlDesc.getAuId() + ":", ioe);
      Status status =
	  new Status().code(HttpStatus.BAD_REQUEST.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    } catch (RuntimeException e) {
      String errorMessage = "Can't enqueue crawl for AU ";
      log.error(errorMessage + crawlDesc.getAuId() + ":", e);
      Status status = new Status()
	  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    }

    crawlJob.creationDate(LocalDateTime.now());

    // Perform the crawl request.
    CrawlerStatus crawlerStatus =
	new WgetCrawlProcessor(cmi).startNewContentCrawl(wgetCrawlReq);
    log.trace("crawlerStatus = {}", crawlerStatus);

    if (crawlerStatus.isCrawlError()) {
      String errorMessage = "Can't perform crawl for " + crawlDesc.getAuId()
      	+ ": " + crawlerStatus.getCrawlErrorMsg();
      log.error(errorMessage);
      Status status = new Status()
	  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()).msg(errorMessage);
      crawlJob.status(status);
      log.debug2("crawlJob = {}", crawlJob);
      return crawlJob;
    }

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

    ServiceBinding crawlerServiceBinding = LockssDaemon.getLockssDaemon()
	.getServiceBinding(ServiceDescr.SVC_CRAWLER);
    log.trace("crawlerServiceBinding = {}", crawlerServiceBinding);

    if (crawlerServiceBinding != null) {
      String crawlerServiceUrl = crawlerServiceBinding.getRestStem();
      crawlJob.result(crawlerServiceUrl);
    }

    Status status =
	new Status().code(HttpStatus.ACCEPTED.value()).msg("Success");
    crawlJob.status(status);

    log.debug2("result = {}", crawlJob);
    return crawlJob;
  }

  CrawlJob startWgetRepair(String auId, List<String> urls) {
    CrawlJob result;
    CrawlManagerImpl cmi = getCrawlManager();
    ArchivalUnit au = getPluginManager().getAuFromId(auId);

    // Handle a missing Archival Unit.
    if (au == null) {
      result = getRequestCrawlResult(auId, null, false, null,
	  NO_SUCH_AU_ERROR_MESSAGE);
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
    return (PluginManager)
	LockssApp.getManagerByKeyStatic(LockssApp.PLUGIN_MANAGER);
  }
}
