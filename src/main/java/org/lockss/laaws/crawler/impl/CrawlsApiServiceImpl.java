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
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawl;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.laaws.crawler.model.*;
import org.lockss.laaws.crawler.model.UrlError.SeverityEnum;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.ListUtil;
import org.lockss.util.RateLimiter;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import org.lockss.util.time.TimeBase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.ws.rs.NotFoundException;
import java.util.*;

import static org.lockss.daemon.Crawler.STATUS_QUEUED;
import static org.lockss.laaws.crawler.CrawlerApplication.PLUGGABLE_CRAWL_MANAGER;
import static org.lockss.servlet.DebugPanel.DEFAULT_CRAWL_PRIORITY;
import static org.lockss.servlet.DebugPanel.PARAM_CRAWL_PRIORITY;
import static org.lockss.util.rest.crawler.CrawlDesc.LOCKSS_CRAWLER_ID;

/**
 * Service for accessing crawls.
 */
@Service
public class CrawlsApiServiceImpl extends BaseSpringApiServiceImpl implements CrawlsApiDelegate {
  // Error Codes
  static final String NO_REPAIR_URLS = "No urls for repair.";
  static final String NO_URLS = "No urls to crawl.";
  static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit:";
  static final String USE_FORCE_MESSAGE = "Use the 'force' parameter to override.";
  static final String NOT_INITIALIZED_MESSAGE = "The service has not been fully initialized";
  static final String UNKNOWN_CRAWLER_MESSAGE = "No registered crawler with id:";
  static final String UNKNOWN_CRAWL_TYPE = "Unknown crawl kind:";
  // A template URI for returning a counter for a specific URL list (eg. found
  // or parsed URLs).
  private static final String COUNTER_URI = "crawls/{jobId}/{counterName}";
  // A template URI for returning a counter for a list of URLs of a specific
  // mimeType.
  private static final String MIME_URI = "crawls/{jobId}/mimeType/{mimeType}";
  // The logger for this class.
  private static final L4JLogger log = L4JLogger.getLogger();

  private PluggableCrawlManager pluggableCrawlManager;

  /**
   * @param kind the type of counter we will be returning
   * @param jobId    A String with the identifier assigned to the crawl when added.
   * @param urlCount the number of urls
   * @return an newly constructed Counter
   */
  static Counter getCounter(COUNTER_KIND kind, String jobId, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", kind.name());
    String path =
      UriComponentsBuilder.fromPath(COUNTER_URI).buildAndExpand(uriVariables).toUriString();
    Counter ctr = new Counter();
    if (urlCount != null) {
      ctr.count(urlCount.getCount());
    }
    else {
      ctr.count(0);
    }
    ctr.itemsLink(path);
    return ctr;
  }

  /**
   * @param jobId    A String with the identifier assigned to the crawl when added.
   * @param mimeType The mine type we are counting
   * @param urlCount The number of urls
   * @return A newly constructed MimeCounter of mimeType
   */
  static MimeCounter getMimeCounter(
    String jobId, String mimeType, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<>();

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

  public static CrawlStatus makeCrawlStatus(CrawlerStatus cs) {
    String key = cs.getKey();
    CrawlStatus crawlStatus =
      new CrawlStatus()
        .jobId(cs.getKey())
        .auId(cs.getAuId())
        .auName(cs.getAuName())
        .type(cs.getType())
        .startTime(cs.getStartTime())
        .endTime(cs.getEndTime())
        .jobStatus(makeJobStatus(cs))
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

  public static CrawlJob makeCrawlJob(CrawlerStatus cs) {
    CrawlJob crawlJob = new CrawlJob();
    updateCrawlJob(crawlJob, cs);
    return crawlJob;
  }

  static void updateCrawlJob(CrawlJob crawlJob, CrawlerStatus cs) {
    if(crawlJob.getCrawlDesc() == null) {
      crawlJob.setCrawlDesc(makeCrawlDesc(cs));
    }
    crawlJob.jobId(cs.getKey());
    crawlJob.jobStatus(makeJobStatus(cs));
    crawlJob.startDate(cs.getStartTime());
    crawlJob.endDate(cs.getEndTime());
  }

  public static CrawlDesc makeCrawlDesc(CrawlerStatus cs) {
    CrawlDesc desc = new CrawlDesc()
      .auId(cs.getAuId())
      .crawlDepth(cs.getDepth())
      .crawlList(ListUtil.fromIterable(cs.getStartUrls()))
      .crawlerId(LOCKSS_CRAWLER_ID)
      .refetchDepth(cs.getRefetchDepth())
      .priority(cs.getPriority());
      String crawlType = cs.getType().toLowerCase();
      log.debug2("Found crawl type string: {}", crawlType);
      if(crawlType.startsWith("new"))
        desc.setCrawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
      else
        desc.setCrawlKind(CrawlDesc.CrawlKindEnum.REPAIR);

    return desc;
  }

  public static JobStatus makeJobStatus(CrawlerStatus crawlerStatus) {
    JobStatus js = new JobStatus();
    StatusCodeEnum statusCode;
    switch (crawlerStatus.getCrawlStatus()) {
      case STATUS_QUEUED:
        statusCode = StatusCodeEnum.QUEUED;
        break;
      case org.lockss.daemon.Crawler.STATUS_ACTIVE:
        statusCode = StatusCodeEnum.ACTIVE;
        break;
      case org.lockss.daemon.Crawler.STATUS_SUCCESSFUL:
        statusCode = StatusCodeEnum.SUCCESSFUL;
        break;
      case org.lockss.daemon.Crawler.STATUS_ERROR:
        statusCode = StatusCodeEnum.ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_ABORTED:
        statusCode = StatusCodeEnum.ABORTED;
        break;
      case org.lockss.daemon.Crawler.STATUS_WINDOW_CLOSED:
        statusCode = StatusCodeEnum.WINDOW_CLOSED;
        break;
      case org.lockss.daemon.Crawler.STATUS_FETCH_ERROR:
        statusCode = StatusCodeEnum.FETCH_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_NO_PUB_PERMISSION:
        statusCode = StatusCodeEnum.NO_PUB_PERMISSION;
        break;
      case org.lockss.daemon.Crawler.STATUS_PLUGIN_ERROR:
        statusCode = StatusCodeEnum.PLUGIN_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_REPO_ERR:
        statusCode = StatusCodeEnum.REPO_ERR;
        break;
      case org.lockss.daemon.Crawler.STATUS_RUNNING_AT_CRASH:
        statusCode = StatusCodeEnum.RUNNING_AT_CRASH;
        break;
      case org.lockss.daemon.Crawler.STATUS_EXTRACTOR_ERROR:
        statusCode = StatusCodeEnum.EXTRACTOR_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_CRAWL_TEST_SUCCESSFUL:
        statusCode = StatusCodeEnum.CRAWL_TEST_SUCCESSFUL;
        break;
      case org.lockss.daemon.Crawler.STATUS_CRAWL_TEST_FAIL:
        statusCode = StatusCodeEnum.CRAWL_TEST_FAIL;
        break;
      case org.lockss.daemon.Crawler.STATUS_INELIGIBLE:
        statusCode = StatusCodeEnum.INELIGIBLE;
        break;
      case org.lockss.daemon.Crawler.STATUS_INACTIVE_REQUEST:
        statusCode = StatusCodeEnum.INACTIVE_REQUEST;
        break;
      case org.lockss.daemon.Crawler.STATUS_INTERRUPTED:
        statusCode = StatusCodeEnum.INTERRUPTED;
        break;
      default:
        statusCode = StatusCodeEnum.UNKNOWN;
    }
    js.setStatusCode(statusCode);
    js.setMsg(crawlerStatus.getCrawlStatusMsg());
    return js;
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

    CrawlStatus crawlStatus;
    JobStatus jobStatus;
    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        log.error(NOT_INITIALIZED_MESSAGE);
        log.error("jobId = {}", jobId);
        jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(NOT_INITIALIZED_MESSAGE);
        crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
        return new ResponseEntity<>(crawlStatus, HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      log.debug2("crawlerStatus = {}", crawlerStatus);

      if (crawlerStatus.isCrawlWaiting() || crawlerStatus.isCrawlActive()) {
        getCrawlManager().deleteCrawl(crawlerStatus.getAu());
      }
      return new ResponseEntity<>(makeCrawlStatus(crawlerStatus), HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      HttpStatus httpStatus = HttpStatus.NOT_FOUND;
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlStatus, httpStatus);
    }
    catch (Exception e) {
      String message = "Cannot deleteCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlStatus = new CrawlStatus().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlStatus, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Deletes all the currently queued and active crawl requests.
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
        log.error(NOT_INITIALIZED_MESSAGE);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      getCrawlManager().deleteAllCrawls();
      getPluggableCrawlManager().deleteAllCrawls();
      return new ResponseEntity<>(HttpStatus.OK);
    }
    catch (Exception e) {
      String message = "Cannot deleteCrawls()";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Requests a crawl.
   *
   * @param crawlDesc A CrawlDesc with the information about the requested crawl.
   * @return a {@code ResponseEntity<CrawlJob>} with the information about the job created to
   * perform the crawl.
   * @see CrawlsApi#doCrawl
   */
  @Override
  public ResponseEntity<CrawlJob> doCrawl(CrawlDesc crawlDesc) {
    log.debug2("crawlDesc = {}", crawlDesc);
    HttpStatus httpStatus;
    CrawlJob crawlJob = new CrawlJob().crawlDesc(crawlDesc);

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
        // Yes: Report the problem.
        logCrawlError(NOT_INITIALIZED_MESSAGE, crawlJob);
        return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
      }
      // Get the crawler Id and Crawl kind
      String crawlerId = crawlDesc.getCrawlerId();
      CrawlDesc.CrawlKindEnum crawlKind = crawlDesc.getCrawlKind();
      // Validate the specified crawlerId.
      if (!getCrawlerIds().contains(crawlerId)) {
        logCrawlError(UNKNOWN_CRAWLER_MESSAGE + crawlerId, crawlJob);
        return new ResponseEntity<>(crawlJob, HttpStatus.BAD_REQUEST);
      }
      // Determine which crawler to use.
      if (crawlerId.equals(LOCKSS_CRAWLER_ID)) {
        // Get the Archival Unit to be crawled.
        ArchivalUnit au = getPluginManager().getAuFromId(crawlDesc.getAuId());
        // Handle a missing Archival Unit.
        if (au == null) {
          logCrawlError(NO_SUCH_AU_ERROR_MESSAGE, crawlJob);
          return new ResponseEntity<>(crawlJob, HttpStatus.NOT_FOUND);
        }
        // Determine which kind of crawl is being requested.
        switch (crawlKind) {
          case NEWCONTENT:
            httpStatus = startLockssCrawl(au, crawlJob);
            break;
          case REPAIR:
            httpStatus = startLockssRepair(au, crawlJob);
            break;
          default:
            httpStatus = HttpStatus.BAD_REQUEST;
            logCrawlError(UNKNOWN_CRAWL_TYPE + crawlKind, crawlJob);
        }
      }
      else {
        // Determine which kind of crawl is being requested.
        switch (crawlKind) {
          case NEWCONTENT:
          case REPAIR:
            crawlJob = new CrawlJob().crawlDesc(crawlDesc);
            httpStatus = startExternalCrawl(crawlJob);
            break;
          default:
            httpStatus = HttpStatus.BAD_REQUEST;
            logCrawlError(UNKNOWN_CRAWL_TYPE + crawlKind, crawlJob);
        }
      }
      log.debug2("crawlJob = {}", crawlJob);
      return new ResponseEntity<>(crawlJob, httpStatus);
    }
    catch (Exception ex) {
      String message = "Attempted crawl of '" + crawlDesc + "' failed.";
      logCrawlError(message, crawlJob);
      if (log.isDebugEnabled()) {
        ex.printStackTrace();
      }
      return new ResponseEntity<>(crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
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
    JobStatus jobStatus;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitConfig()) {
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
        log.error(NOT_INITIALIZED_MESSAGE);
        log.error("limit = {}, continuationToken = {}", limit, continuationToken);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      JobPager pager = getJobsPager(limit, continuationToken);
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
   * Return a CrawlStatus for the jobId.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   */
  private CrawlStatus getCrawlStatus(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlerStatus cs = getCrawlerStatus(jobId);

    CrawlStatus crawlStatus =
      new CrawlStatus()
        .jobId(cs.getKey())
        .auId(cs.getAuId())
        .auName(cs.getAuName())
        .type(cs.getType())
        .startUrls(ListUtil.fromIterable(cs.getStartUrls()))
        .startTime(cs.getStartTime())
        .endTime(cs.getEndTime())
        .jobStatus(makeJobStatus(cs))
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

  private HttpStatus startLockssCrawl(ArchivalUnit au, CrawlJob crawlJob) {
    CrawlDesc crawlDesc = crawlJob.getCrawlDesc();
    Integer depth = crawlDesc.getCrawlDepth();
    Integer requestedPriority = crawlDesc.getPriority();
    boolean force = crawlDesc.isForceCrawl();

    log.debug2("au = {}", au);
    log.debug2("depth = {}", depth);
    log.debug2("requestedPriority = {}", requestedPriority);
    log.debug2("force = {}", force);

    CrawlManagerImpl cmi = getCrawlManager();
    // Reset the rate limiter if the request is forced.
    if (force) {
      RateLimiter limiter = cmi.getNewContentRateLimiter(au);
      log.trace("limiter = {}", limiter);

      if (!limiter.isEventOk()) {
        limiter.unevent();
      }
    }
    JobStatus jobStatus = new JobStatus();
    crawlJob.jobStatus(jobStatus);
    String msg;
    // Handle eligibility for queuing the crawl.
    try {
      cmi.checkEligibleToQueueNewContentCrawl(au);
      cmi.checkEligibleForNewContentCrawl(au);
    }
    catch (CrawlManagerImpl.NotEligibleException.RateLimiter neerl) {
      msg = "AU has crawled recently (" + neerl.getMessage() + "). " + USE_FORCE_MESSAGE;
      logCrawlError(msg, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    catch (CrawlManagerImpl.NotEligibleException nee) {
      msg = "Can't enqueue crawl: " + nee.getMessage();
      logCrawlError(msg, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }

    // Get the crawl priority, specified or configured.
    int priority;

    if (requestedPriority != null) {
      priority = requestedPriority;
    }
    else {
      Configuration config = ConfigManager.getCurrentConfig();
      priority = config.getInt(PARAM_CRAWL_PRIORITY, DEFAULT_CRAWL_PRIORITY);
    }

    log.trace("priority = " + priority);

    // Create the crawl request.
    CrawlReq req;

    try {
      CrawlerStatus crawlerStatus = new CrawlerStatus(au, au.getStartUrls(), null);
      req = new CrawlReq(au, crawlerStatus);
      req.setPriority(priority);

      if (depth != null) {
        req.setRefetchDepth(depth);
      }
    }
    catch (RuntimeException e) {
      msg = "Can't enqueue crawl: ";
      logCrawlError(msg, crawlJob);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    crawlJob.requestDate(TimeBase.nowMs());

    // Perform the crawl request.
    CrawlerStatus lockssCrawlStatus = cmi.startNewContentCrawl(req);
    log.trace("crawlerStatus = {}", lockssCrawlStatus);
    updateCrawlJob(crawlJob,lockssCrawlStatus);
    if (lockssCrawlStatus.isCrawlError()) {
      msg = "Can't perform crawl for " + au + ": " + lockssCrawlStatus.getCrawlErrorMsg();
      logCrawlError(msg, crawlJob);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    ServiceBinding crawlerServiceBinding =
      LockssDaemon.getLockssDaemon().getServiceBinding(ServiceDescr.SVC_CRAWLER);
    log.trace("crawlerServiceBinding = {}", crawlerServiceBinding);

    if (crawlerServiceBinding != null) {
      String crawlerServiceUrl = crawlerServiceBinding.getRestStem();
      crawlJob.result(crawlerServiceUrl);
    }
    crawlJob.jobStatus(makeJobStatus(lockssCrawlStatus));
    log.debug2("result = {}", crawlJob);
    getPluggableCrawlManager().addCrawlJob(crawlJob);
    return HttpStatus.ACCEPTED;
  }

  HttpStatus startLockssRepair(ArchivalUnit au, CrawlJob crawlJob) {
    CrawlManagerImpl cmi = getCrawlManager();
    List<String> urls = crawlJob.getCrawlDesc().getCrawlList();
    // Handle a missing Archival Unit.
    if (au == null) {
      logCrawlError(NO_SUCH_AU_ERROR_MESSAGE, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    // Handle missing Repair Urls.
    if (urls == null) {
      logCrawlError(NO_REPAIR_URLS, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }

    CrawlerStatus status = cmi.startRepair(au, urls, new CrawlManagerCallback(), null);
    updateCrawlJob(crawlJob,status);
    getPluggableCrawlManager().addCrawlJob(crawlJob);
    return HttpStatus.ACCEPTED;
  }

  HttpStatus startExternalCrawl(CrawlJob crawlJob) {
    CrawlDesc crawlDesc = crawlJob.getCrawlDesc();
    log.debug2("crawlDesc = {}", crawlDesc);
    String msg;
    PluggableCrawlManager pcMgr = getPluggableCrawlManager();
    Collection<String> urls = crawlDesc.getCrawlList();
    if (urls == null || urls.isEmpty()) {
      // try to get the urls from the au.
      ArchivalUnit au = getPluginManager().getAuFromId(crawlDesc.getAuId());
      if((au != null)) {
        urls = au.getStartUrls();
        crawlDesc.setCrawlList((List<String>) urls);
      }
      if(urls == null || urls.isEmpty() ){
        logCrawlError(NO_URLS, crawlJob);
        return HttpStatus.BAD_REQUEST;
      }
    }
    String crawlerId = crawlDesc.getCrawlerId();
    PluggableCrawler crawler = pcMgr.getCrawler(crawlerId);
    if (crawler == null) {
      logCrawlError(UNKNOWN_CRAWLER_MESSAGE + crawlerId, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    crawlJob.requestDate(TimeBase.nowMs());
    try {
      // add the requested crawlJob to the CrawlQueue for that crawler.
      PluggableCrawl crawl = crawler.requestCrawl(crawlJob, new CrawlManagerCallback());
      CrawlerStatus crawlerStatus = crawl.getCrawlerStatus();
      updateCrawlJob(crawlJob, crawlerStatus);
      JobStatus jobStatus = crawl.getJobStatus();
      if (jobStatus.getStatusCode().equals(StatusCodeEnum.ERROR)) {
        msg = "Can't perform crawl for " + crawlDesc.getAuId()
          + ": " + jobStatus.getMsg();
        logCrawlError(msg, crawlJob);
        return HttpStatus.INTERNAL_SERVER_ERROR;
      }
      ServiceBinding crawlerServiceBinding = LockssDaemon.getLockssDaemon()
        .getServiceBinding(ServiceDescr.SVC_CRAWLER);
      log.trace("crawlerServiceBinding = {}", crawlerServiceBinding);

      if (crawlerServiceBinding != null) {
        String crawlerServiceUrl = crawlerServiceBinding.getRestStem();
        crawlJob.result(crawlerServiceUrl);
      }
      log.debug2("result = {}", crawlJob);
      pcMgr.addCrawlJob(crawlJob);
      return HttpStatus.ACCEPTED;
    }
    catch (IllegalArgumentException iae) {
      msg = "Invalid crawl specification for AU " + crawlDesc.getAuId() + ": " + iae.getMessage();
      logCrawlError(msg, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    catch (RuntimeException e) {
      msg = "Can't enqueue crawl for AU ";
      logCrawlError(msg, crawlJob);
      return HttpStatus.INTERNAL_SERVER_ERROR;
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
   * Checks that a continuation token is valid.
   *
   * @param timeStamp         A long with the timestamp used to validate the continuation token.
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
    if (limit != null && limit < 0) {
      String errMsg =
        "Invalid limit: limit must be a non-negative integer; " + "it was '" + limit + "'";
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }

    return limit;
  }

  public UrlInfo makeUrlInfo(String url, CrawlerStatus status) {
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
   * @param requestLimit      An Integer with the request maximum number of jobs per page.
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
    // This needs to be replaced with a CrawlJob map for crawl manager jobs
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
        String errMsg =
          "Invalid pagination request: startAt = "
            + (lastJobToSkip + 1)
            + ", Total = "
            + listSize;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }

      List<CrawlJob> outputJobs = new ArrayList<>();

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
          outputJobs.add(makeCrawlJob(crawlerStatus));

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
   * @param lastElement    A Long with the index of the last element in the page.
   * @param totalCount     An int with the total number of elements.
   * @param timeStamp      A Long with the timestamp to use in the continuation token, if necessary.
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

    String nextToken;

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
   * @return The CrawlerStatus for thi if the s job
   * @throws NotFoundException if there is no crawl status for this job
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

  /**
   * Provides the crawl manager.
   *
   * @return a CrawlManagerImpl with the crawl manager implementation.
   */
  CrawlManagerImpl getCrawlManager() {
    CrawlManagerImpl crawlManager = null;
    CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);

    if (cmgr instanceof CrawlManagerImpl) {
      crawlManager = (CrawlManagerImpl) cmgr;
    }

    return crawlManager;
  }

  PluggableCrawlManager getPluggableCrawlManager() {
    if(pluggableCrawlManager == null) {
      pluggableCrawlManager = (PluggableCrawlManager) LockssApp.getManagerByKeyStatic(PLUGGABLE_CRAWL_MANAGER);
    }
    return pluggableCrawlManager;
  }

  List<String> getCrawlerIds() {
    return getPluggableCrawlManager().getCrawlerIds();
  }

  private void logCrawlError(String message, CrawlJob crawlJob) {
    // Yes: Report the problem.
    log.error(message);
    log.error("crawlDesc = {}", crawlJob.getCrawlDesc());
    crawlJob.jobStatus(new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message));
    log.debug2("crawlJob = {}", crawlJob);
  }

  public class CrawlManagerCallback implements CrawlManager.Callback {

    @Override
    public void signalCrawlAttemptCompleted(boolean success, Object cookie, CrawlerStatus status) {
      CrawlJob crawlJob = pluggableCrawlManager.getCrawlJob(status.getKey());
      if(crawlJob != null) {
        updateCrawlJob(crawlJob,status);
        pluggableCrawlManager.updateCrawlJob(crawlJob);
      }
      else {
        log.error("Received a callback without a crawl job for Crawler Status {}",status);
      }
    }
  }

  enum COUNTER_KIND {
    errors,
    excluded,
    fetched,
    notmodified,
    parsed,
    pending
  }

}
