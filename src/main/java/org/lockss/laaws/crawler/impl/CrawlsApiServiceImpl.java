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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.lockss.app.LockssApp;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.crawler.CrawlerStatus.UrlErrorInfo;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.model.Counter;
import org.lockss.laaws.crawler.model.CrawlRequest;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.MimeCounter;
import org.lockss.laaws.crawler.model.PageInfo;
import org.lockss.laaws.crawler.model.RequestCrawlResult;
import org.lockss.laaws.crawler.model.Status;
import org.lockss.laaws.crawler.model.UrlError;
import org.lockss.laaws.crawler.model.UrlError.SeverityEnum;
import org.lockss.laaws.crawler.model.UrlInfo;
import org.lockss.laaws.crawler.model.UrlPager;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.ListUtil;
import org.lockss.util.RateLimiter;
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

  static final String NO_REPAIR_URLS =
      "No urls for repair.";
  static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit";
  static final String USE_FORCE_MESSAGE =
      "Use the 'force' parameter to override.";
  /**
   * A template URI for returning a counter for a specific URL list (eg. found
   * or parsed URLs).
   */
  private static final String COUNTER_URI = "crawls/{jobId}/{counterName}";
  /**
   * A template URI for returning a counter for a list of URLs of a specific
   * mimeType.
   */
  private static final String MIME_URI = "crawls/{jobId}/mimeType/{mimeType}";
  /**
   * The logger for this class
   */
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * @param body A CrawlRequest for the requested crawl
   * @see CrawlsApi#doCrawl
   */
  @Override
  public ResponseEntity<RequestCrawlResult> doCrawl(CrawlRequest body) {
    RequestCrawlResult result = null;
    try {
      if (!CrawlersApiServiceImpl.getCrawlerIds().contains(body.getCrawler())) {
        return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
      }
      switch (body.getCrawlKind()) {
        case NEWCONTENT:
          result = doCrawl(body.getAuId(), body.getRefetchDepth(),
              body.getPriority(), body.isForceCrawl());
          break;
        case REPAIR:
          result = doRepair(body.getAuId(), body.getRepairList());
          break;
      }
      if (result.isAccepted()) {
        return new ResponseEntity<>(result, HttpStatus.ACCEPTED);
      }
      else {
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
      }
    }
    catch (Exception ex) {
      log.error("do crawl failed", ex);
      return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  /**
   * @param limit the number of items per page
   * @param continuationToken the continuation token used to fetch the next page
   * @see CrawlsApi#getCrawls
   */
  @Override
  public ResponseEntity<JobPager> getCrawls(Integer limit,
      String continuationToken) {
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    try {
      JobPager pager = getJobsPager(limit, continuationToken);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (Exception e) {
      String message = "Cannot return crawls: " + e.getMessage();
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  /**
   * Delete a crawl previously added to the crawl queue, stop the crawl if
   * already running.
   *
   * @param jobId the id assigned to the crawl when added.
   * @see CrawlsApi#deleteCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> deleteCrawlById(String jobId) {
    log.debug2("jobId = {}", jobId);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    try {
      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      if (crawlerStatus != null) {
        if (crawlerStatus.isCrawlWaiting() || crawlerStatus.isCrawlActive()) {
          //todo: unhighlight when we push the corresponding change in lockss-core
          //cmi.deleteCrawl(crawlerStatus.getAu());
        }
        return new ResponseEntity<>(CrawlStatusfromCrawlerStatus(crawlerStatus),
            HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch(Exception ex) {
      log.error("deletion error on server.", ex);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the status of a requested crawl.
   *
   * @param jobId the id assigned to the crawl when added
   * @see CrawlsApi#getCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> getCrawlById(String jobId) {
    log.debug2("jobId = {}", jobId);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    try {
      CrawlStatus status = getCrawlStatus(jobId);
      return new ResponseEntity<>(status, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No info found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (Exception ex) {
      log.error("unable to return crawl status: server error", ex);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of error urls with error code and message.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlErrors
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlErrors(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = new ArrayList(status.getUrlsErrorMap().keySet());
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No info found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls excluded from the crawl.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlExcluded
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlExcluded(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsExcluded();
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No urls found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls fetched in the crawl.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlFetched
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlFetched(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsFetched();
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No urls found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls found to be notModified during the crawl.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlNotModified
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlNotModified(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsNotModified();
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No urls found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls parsed during the crawl.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlParsed
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlParsed(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsParsed();
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No urls found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls pending for the crawl.
   *
   * @param jobId the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlPending
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlPending(String jobId,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsPending();
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg = "No urls found for '" + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  /**
   * Return the list of items by mime-type.
   *
   * @param jobId the id of the crawl
   * @param type the mime-type
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit the number of items per page
   * @see CrawlsApi#getCrawlByMimeType
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlByMimeType(String jobId, String type,
      String continuationToken, Integer limit) {
    log.debug2("jobId = {}", jobId);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("limit = {}", limit);

//    // Check whether the service has not been fully initialized.
//    if (!waitReady()) {
//      // Yes: Notify the client.
//      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
//    }

    UrlPager pager;
    try {
      CrawlerStatus status = getCrawlerStatus(jobId);
      List<String> urls = status.getUrlsOfMimeType(type);
      pager = getUrlPager(status, urls, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String msg =
	  "Unable to find mime type '" + type + "' for job " + jobId + "'.";
      log.warn(msg);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    catch (IllegalArgumentException iae) {
      String msg = "Invalid continuation token'" + continuationToken + "'";
      log.warn(msg, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception e) {
      String msg = "Cannot return mime-type for auid '" + jobId + "'";
      log.error(msg, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return a CrawlStatus for the jobId.
   */
  CrawlStatus getCrawlStatus(String key) {
    CrawlerStatus cs = getCrawlerStatus(key);
    CrawlStatus crawlStatus = new CrawlStatus();
    crawlStatus.key(cs.getKey());
    crawlStatus.auId(cs.getAuId());
    crawlStatus.auName(cs.getAuName());
    crawlStatus.type(cs.getType());
    crawlStatus.startUrls(ListUtil.fromIterable(cs.getStartUrls()));
    crawlStatus.startTime(cs.getStartTime());
    crawlStatus.endTime(cs.getEndTime());
    crawlStatus.status(getStatus(cs));
    crawlStatus.priority(cs.getPriority());
    crawlStatus.sources(ListUtil.immutableListOfType(cs.getSources(),
	String.class));
    crawlStatus.bytesFetched(cs.getContentBytesFetched());
    crawlStatus.depth(cs.getDepth());
    crawlStatus.refetchDepth(cs.getRefetchDepth());
    crawlStatus.proxy(cs.getProxy());

    CrawlerStatus.UrlCount urlCount;
    if (cs.getNumOfMimeTypes() > 0) {
      crawlStatus.mimeTypes(new ArrayList<MimeCounter>());
      for (String mimeType : cs.getMimeTypes()) {
        urlCount = cs.getMimeTypeCtr(mimeType);
        if (urlCount.getCount() > 0) {
          crawlStatus.addMimeTypesItem(getMimeCounter(key, mimeType, urlCount));
        }
      }
    }
    // add the url counters
    urlCount = cs.getErrorCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.errors(getCounter(COUNTER_KIND.errors, key, urlCount));
    }
    urlCount = cs.getExcludedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.excludedItems(getCounter(COUNTER_KIND.excluded, key,
	  urlCount));
    }
    urlCount = cs.getFetchedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.fetchedItems(getCounter(COUNTER_KIND.fetched, key, urlCount));
    }
    urlCount = cs.getNotModifiedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.notModifiedItems(getCounter(COUNTER_KIND.notmodified, key,
	  urlCount));
    }
    urlCount = cs.getParsedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.parsedItems(getCounter(COUNTER_KIND.parsed, key, urlCount));
    }
    urlCount = cs.getPendingCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.pendingItems(getCounter(COUNTER_KIND.pending, key, urlCount));
    }
    crawlStatus.isActive(cs.isCrawlActive());
    crawlStatus.isError(cs.isCrawlError());
    crawlStatus.isWaiting(cs.isCrawlWaiting());

    return crawlStatus;
  }

  Status getStatus(CrawlerStatus cs) {
    Status status = new Status();
    status.code(cs.getCrawlStatus());
    status.msg(cs.getCrawlStatusMsg());
    return status;
  }

  Counter getCounter(COUNTER_KIND kind, String jobId,
      CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", kind.name());
    String path = UriComponentsBuilder.fromPath(COUNTER_URI)
	.buildAndExpand(uriVariables).toUriString();
    Counter ctr = new Counter();
    ctr.count(urlCount.getCount());
    ctr.itemsLink(path);
    return ctr;
  }

  MimeCounter getMimeCounter(String jobId, String mimeType,
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

  RequestCrawlResult doCrawl(String auId, Integer depth, Integer reqPriority,
      boolean force) {
    if (log.isDebugEnabled()) {
      log.debug("auId = " + auId);
      log.debug("depth = " + depth);
      log.debug("requestedPriority = " + reqPriority);
      log.debug("force = " + force);
    }
    RequestCrawlResult result = null;

    // Get the Archival Unit to be crawled.
    ArchivalUnit au = getPluginManager().getAuFromId(auId);

    // Handle a missing Archival Unit.
    if (au == null) {
      result = getRequestCrawlResult(auId, depth, false, null,
	  NO_SUCH_AU_ERROR_MESSAGE);
      return result;
    }

    CrawlManagerImpl cmi = getCrawlManager();

    // Reset the rate limiter if the request is forced.
    if (force) {
      RateLimiter limiter = cmi.getNewContentRateLimiter(au);
      if (!limiter.isEventOk()) {
        limiter.unevent();
      }
    }

    // Handle eligibility for queuing the crawl.
    try {
      cmi.checkEligibleToQueueNewContentCrawl(au);
    }
    catch (CrawlManagerImpl.NotEligibleException.RateLimiter neerl) {
      String errorMessage = "AU has crawled recently (" + neerl.getMessage()
          + "). " + USE_FORCE_MESSAGE;
      result = getRequestCrawlResult(auId, depth, false, null, errorMessage);
      return result;

    }
    catch (CrawlManagerImpl.NotEligibleException nee) {
      String errorMessage = "Can't enqueue crawl: " + nee.getMessage();
      result = getRequestCrawlResult(auId, depth, false, null, errorMessage);
      return result;
    }

    String delayReason = null;

    try {
      cmi.checkEligibleForNewContentCrawl(au);
    }
    catch (CrawlManagerImpl.NotEligibleException nee) {
      delayReason = "Start delayed due to: " + nee.getMessage();
    }

    // Get the crawl priority, specified or configured.
    int priority = 0;

    if (reqPriority != null) {
      priority = reqPriority;
    }

    // Create the crawl request.
    CrawlReq req;

    try {
      req = new CrawlReq(au);
      req.setPriority(priority);

      if (depth != null) {
        req.setRefetchDepth(depth);
      }
    }
    catch (RuntimeException e) {
      String errorMessage = "Can't enqueue crawl: ";
      log.error(errorMessage + au, e);
      result = getRequestCrawlResult(auId, depth, false, null,
	  errorMessage + e.toString());
      return result;
    }

    // Perform the crawl request.
    cmi.startNewContentCrawl(req);
    result = getRequestCrawlResult(auId, depth, true, delayReason, null);
    return result;
  }

  private RequestCrawlResult getRequestCrawlResult(String auId, Integer depth,
      boolean success, String delayReason, String errorMessage) {
    RequestCrawlResult result;
    result = new RequestCrawlResult().auId(auId).refetchDepth(depth)
	.accepted(success).delayReason(delayReason).errorMessage(errorMessage);
    if (log.isDebugEnabled()) {
      log.debug("result = " + result);
    }
    return result;
  }

  RequestCrawlResult doRepair(String auId, List<String> urls) {
    RequestCrawlResult result;
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

  UrlPager getUrlPager(CrawlerStatus status, List<String> urls,
      String continuationToken, Integer limit) {
    UrlPager pager = new UrlPager();
    Integer curLimit = getLimit(limit);
    ContinuationToken inToken = null;
    if (continuationToken != null) {
      inToken = new ContinuationToken(continuationToken);
    }
    int listSize = urls.size();   // Get the pageful of results.

    if (listSize > 0) {// there may be something to send
      List<UrlInfo> urlInfos = new ArrayList<>();
      // find the boundary
      int count = 0;
      Long lastElement = 0L;

      if (curLimit > 0 && curLimit < listSize) { // we need to break up the list.
        if (inToken != null) {
          lastElement = inToken.getLastElement();
        }
        int el = lastElement.intValue();
        while (el < listSize && count < curLimit) {
          // convert our urls to to url infos.
          String url = urls.get(el);
          urlInfos.add(makeUrlInfo(url, status));
          el++;
          count++;
        }
      }
      else { // send everything
        for (String url : urls) {
          urlInfos.add(makeUrlInfo(url, status));
        }
        count = urls.size();
      }
      pager.setUrls(urlInfos);
      pager.setPageInfo(createPageInfo(curLimit, count, lastElement, listSize));
    }
    return pager;
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

  JobPager getJobsPager(Integer limit, String continuationToken) {
    JobPager pager = new JobPager();
    Integer curLimit = getLimit(limit);
    ContinuationToken inToken = null;
    if (continuationToken != null) {
      inToken = new ContinuationToken(continuationToken);
    }
    // Get the pageful of results.
    CrawlManagerImpl cmi = getCrawlManager();
    List<CrawlerStatus> inList = cmi.getStatus().getCrawlerStatusList();
    List<CrawlStatus> items = new ArrayList<>();
    pager.setJobs(items);
    int count = 0;
    Long lastElement = 0L;
    int listSize = inList.size();
    if (curLimit > 0 && curLimit < listSize) { // we need to break up the list.
      if (inToken != null) {
        lastElement = inToken.getLastElement();
      }
      int el = lastElement.intValue();
      while (el < listSize && count < curLimit) {
        items.add(CrawlStatusfromCrawlerStatus(inList.get(el)));
        el++;
        count++;
      }
    }
    else { // just send everything
      for (CrawlerStatus inCs : inList) {
        items.add(CrawlStatusfromCrawlerStatus(inCs));
      }
      count = listSize;
    }
    if (log.isTraceEnabled()) {
      log.trace("jobs = {}", () -> items);
    }
    // create the page
    // create page info block
    pager.setPageInfo(createPageInfo(curLimit, count, lastElement, listSize));
    log.debug2("result = {}", () -> pager);
    return pager;
  }

  private PageInfo createPageInfo(Integer curLimit, int count, Long lastElement,
      int listSize) {
    PageInfo pi = new PageInfo();

    ServletUriComponentsBuilder builder =
	ServletUriComponentsBuilder.fromCurrentRequest();
    // set current link
    pi.setCurLink(builder.cloneBuilder().toUriString());
    pi.setTotalCount(count);
    pi.setResultsPerPage(curLimit);
    if (lastElement < listSize) {
      String nextToken = new ContinuationToken(System.currentTimeMillis(),
	  lastElement).toToken();
      pi.setContinuationToken(nextToken);
      builder.replaceQueryParam("continuationToken", nextToken);
    }
    else {
      builder.replaceQueryParam("continuationToken");
    }
    pi.setNextLink(builder.toUriString());
    return pi;
  }

  private CrawlManagerImpl getCrawlManager() throws IllegalStateException{
    CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
    return (CrawlManagerImpl) cmgr;

  }

  private CrawlerStatus getCrawlerStatus(String key) throws NotFoundException {
    CrawlManagerImpl cmi = getCrawlManager();
    CrawlerStatus cs = cmi.getStatus().getCrawlerStatus(key);
    if (cs == null) {
      String msg = "No Job found for '" + key + "'";
      log.warn(msg);
      throw new NotFoundException();
    }
    return cs;
  }

  private CrawlStatus CrawlStatusfromCrawlerStatus(CrawlerStatus cs) {
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
    crawlStatus.getStartUrls().addAll(cs.getAu().getStartUrls());

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

  /**
   * Provides the plugin manager.
   *
   * @return the current Lockss PluginManager.
   */
  private PluginManager getPluginManager() {
    return (PluginManager)
	LockssApp.getManagerByKeyStatic(LockssApp.PLUGIN_MANAGER);
  }

  /**
   * Check that a limit field is valid.
   */
  private Integer getLimit(Integer limit) throws IllegalArgumentException {
    // check limit if assigned is greater than 0
    if (limit != null && limit.intValue() < 0) {
      String errMsg = "Invalid limit: limit must be a non-negative integer; "
	  + "it was '" + limit + "'";
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }

    return limit;
  }
}
