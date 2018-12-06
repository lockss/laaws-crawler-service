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
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.api.CrawlsApi;
import org.lockss.laaws.crawler.api.CrawlsApiDelegate;
import org.lockss.laaws.crawler.model.*;
import org.lockss.laaws.status.model.ApiStatus;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.spring.status.SpringLockssBaseApiController;
import org.lockss.util.ListUtil;
import org.lockss.util.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static org.lockss.app.LockssApp.PLUGIN_MANAGER;
import static org.lockss.laaws.crawler.impl.CrawlersApiImpl.CRAWLERS;

@Service
public class CrawlsApiImpl extends SpringLockssBaseApiController
    implements CrawlsApiDelegate {
  public static final String PREFIX = Configuration.PREFIX + "crawlerService.";

  /**
   * Priority for crawls started from the this service.
   */
  public static final String PARAM_CRAWL_PRIORITY =
      PREFIX + "crawlPriority";

  public static final int DEFAULT_CRAWL_PRIORITY = 10;
  static final String NO_REPAIR_URLS =
      "No urls for repair.";
  static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit";
  static final String USE_FORCE_MESSAGE =
      "Use the 'force' parameter to override.";
  private static final String DEBUG_HEADER = "doRequest: ";
  /**
   * The current version of the API
   */
  private static final String API_VERSION = "1.0.0";
  /**
   * The logger for this class
   */
  private static L4JLogger log = L4JLogger.getLogger(CrawlsApiImpl.class);
  /**
   * A template URI for returning a counter for a specific url list eg found or parsed urls.
   */
  private static final String COUNTER_URI = "crawls/{jobId}/{counterName}";
  /**
   * A template URI for returnin a counter for a list of urls of a specific mimeType.
   */
  private static final String MIME_URI = "crawls/{jobId}/mimeType/{mimeType}";
  /**
   * The crawlManager
   */
  private CrawlManagerImpl crawlMgrImpl;

  /**
   * The pluginManager for used for turning AUName to Au.
   */
  private PluginManager pluginManager;


  /**
   * Return the status of the service and its version.
   *
   * @return ApiStatus the common laaws status info for this service.
   */
  @Override
  public ApiStatus getApiStatus() {
    return new ApiStatus()
        .setVersion(API_VERSION)
        .setReady(LockssApp.getLockssApp().isAppRunning());
  }

  /**
   * @param body A CrawlRequest for the requested crawl
   * @see CrawlsApi#doCrawl
   */
  @Override
  public ResponseEntity<RequestCrawlResult> doCrawl(CrawlRequest body) {
    RequestCrawlResult result = null;

    if (!CRAWLERS.contains(body.getCrawler())) {
      return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
    }
    switch (body.getCrawlKind()) {
      case NEWCONTENT:
        result = doCrawl(body.getAuId(), body.getRefetchDepth(), body.getPriority(), body.isForceCrawl());
        break;
      case REPAIR:
        result = doRepair(body.getAuId(), body.getRepairList());
        break;
    }
    if (result.isAccepted()) {
      return new ResponseEntity<>(result, HttpStatus.ACCEPTED);
    } else {
      return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * @param limit             the number of items per page
   * @param continuationToken the continuation token used to fetch the next page
   * @see CrawlsApi#getCrawls
   */
  @Override
  public ResponseEntity<JobPager> getCrawls(Integer limit, String continuationToken) {

    JobPager pager;
    try {
      pager = getJobsPager(limit, continuationToken);
      return new ResponseEntity<JobPager>(pager, HttpStatus.OK);
    } catch (Exception e) {
      String message = "Cannot return crawls: " + e.getMessage();
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  /**
   * Delete a crawl previously added to the crawl queue, stop the crawl if already running.
   *
   * @param jobId the id assigned to the crawl when added.
   * @see CrawlsApi#deleteCrawlById
   */
  @Override
  public ResponseEntity<CrawlRequest> deleteCrawlById(String jobId) {
    return null;
  }


  /**
   * Return the status of a requested crawl.
   *
   * @param jobId the id assigned to the crawl when added
   * @see CrawlsApi#getCrawlById
   */
  @Override
  public ResponseEntity<CrawlStatus> getCrawlById(String jobId) {
    CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
    if(crawlerStatus == null) {
      String message = "No Job found for '" + jobId + "'";
      log.warn(message);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    CrawlStatus status = getCrawlStatus(jobId);
    if(status != null) {
      return new ResponseEntity<>(status, HttpStatus.OK);
    }
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Return the list of error urls with error code and message.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlErrored
   */
  @Override
  public ResponseEntity<ErrorPager> getCrawlErrored(String jobId, String continuationToken, Integer limit) {
    ErrorPager pager;
    try {
      pager = getErrorPager(jobId, continuationToken, limit);
      return new ResponseEntity<ErrorPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for auid '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Job found for jobId'" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return error items for auid '" + jobId + "'";
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls excluded from the crawl.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlExcluded
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlExcluded(String jobId, String continuationToken, Integer limit) {
    UrlPager pager;
    try {
      pager = getUrlPager("exclueded", jobId, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for auid '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Job found for jobId'" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return excluded items for auid '" + jobId + "'";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls fetched in the crawl.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlFetched
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlFetched(String jobId, String continuationToken, Integer limit) {
    UrlPager pager;
    try {
      pager = getUrlPager("fetched", jobId, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for auid '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Job found for jobId'" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return fetched items for auid '" + jobId + "'";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls found to be notModified during the crawl.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlNotModified
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlNotModified(String jobId, String continuationToken, Integer limit) {
    UrlPager pager;
    try {
      pager = getUrlPager("notModified", jobId, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for jobId '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Archival Unit found for jobId '" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return notModified items for auid '" + jobId + "'";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Return the list of urls parsed during the crawl.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlParsed
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlParsed(String jobId, String continuationToken, Integer limit) {
    UrlPager pager;
    try {
      pager = getUrlPager("parsed", jobId, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for auid '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Job found for jobId'" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return parsed items for auid '" + jobId + "'";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }


  }

  /**
   * Return the list of urls pending for the crawl.
   *
   * @param jobId             the id of the crawl
   * @param continuationToken the continuation token used to fetch the next page
   * @param limit             the number of items per page
   * @see CrawlsApi#getCrawlPending
   */
  @Override
  public ResponseEntity<UrlPager> getCrawlPending(String jobId, String continuationToken, Integer limit) {
    UrlPager pager;
    try {
      pager = getUrlPager("pending", jobId, continuationToken, limit);
      return new ResponseEntity<UrlPager>(pager, HttpStatus.OK);
    } catch (ConcurrentModificationException cme) {
      String message =
          "Pagination conflict for auid '" + jobId + "': " + cme.getMessage();
      log.warn(message, cme);
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    } catch (IllegalArgumentException iae) {
      String message = "No Job found for jobId'" + jobId + "'";
      log.warn(message, iae);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      String message = "Cannot return pending items for auid '" + jobId + "'";
      log.error(message, e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


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
    crawlStatus.sources(ListUtil.immutableListOfType(cs.getSources(), String.class));
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
          crawlStatus.addMimeTypesItem(getMimeCounter( key, mimeType, urlCount));
        }
      }
    }
    // add the url counters
    urlCount = cs.getErrorCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.errors(getCounter("errors", key, urlCount));
    }
    urlCount = cs.getExcludedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.excludedItems(getCounter("excluded", key, urlCount));
    }
    urlCount = cs.getFetchedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.fetchedItems(getCounter("fetched", key, urlCount));
    }
    urlCount = cs.getNotModifiedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.notModifiedItems(getCounter("notmodified", key, urlCount));
    }
    urlCount = cs.getParsedCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.parsedItems(getCounter("parsed", key, urlCount));
    }
    urlCount = cs.getPendingCtr();
    if (urlCount.getCount() > 0) {
      crawlStatus.pendingItems(getCounter("pending", key, urlCount));
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

  Counter getCounter(String counterName, String jobId, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", counterName);
    String path = UriComponentsBuilder.fromPath(COUNTER_URI).buildAndExpand(uriVariables).toUriString();

    Counter ctr = new Counter();
    ctr.count(urlCount.getCount());
    ctr.itemsLink(path);
    return ctr;
  }

  MimeCounter getMimeCounter(String jobId, String mimeType, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<String, Object>();

    uriVariables.put("jobId", jobId);
    uriVariables.put("mimeType", mimeType);
    String path = UriComponentsBuilder.fromPath(MIME_URI).buildAndExpand(uriVariables).toUriString();
    MimeCounter ctr = new MimeCounter();
    ctr.mimeType(mimeType);
    ctr.count(urlCount.getCount());
    ctr.counterLink(path);
    return ctr;
  }

  RequestCrawlResult doCrawl(String auId, Integer depth, Integer reqPriority, boolean force) {
    if (log.isDebugEnabled()) {
      log.debug(DEBUG_HEADER + "auId = " + auId);
      log.debug(DEBUG_HEADER + "depth = " + depth);
      log.debug(DEBUG_HEADER + "requestedPriority = " + reqPriority);
      log.debug(DEBUG_HEADER + "force = " + force);
    }
    RequestCrawlResult result = null;

    // Get the Archival Unit to be crawled.
    ArchivalUnit au = getPluginManager().getAuFromId(auId);

    // Handle a missing Archival Unit.
    if (au == null) {
      result = getRequestCrawlResult(auId, depth, false, null, NO_SUCH_AU_ERROR_MESSAGE);
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
    } catch (CrawlManagerImpl.NotEligibleException.RateLimiter neerl) {
      String errorMessage = "AU has crawled recently (" + neerl.getMessage()
          + "). " + USE_FORCE_MESSAGE;
      result = getRequestCrawlResult(auId, depth, false, null, errorMessage);
      return result;

    } catch (CrawlManagerImpl.NotEligibleException nee) {
      String errorMessage = "Can't enqueue crawl: " + nee.getMessage();
      result = getRequestCrawlResult(auId, depth, false, null, errorMessage);
      return result;
    }

    String delayReason = null;

    try {
      cmi.checkEligibleForNewContentCrawl(au);
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      delayReason = "Start delayed due to: " + nee.getMessage();
    }

    // Get the crawl priority, specified or configured.
    int priority = 0;

    if (reqPriority != null) {
      priority = reqPriority;
    } else {
      Configuration config = ConfigManager.getCurrentConfig();
      priority = config.getInt(PARAM_CRAWL_PRIORITY, DEFAULT_CRAWL_PRIORITY);
    }

    // Create the crawl request.
    CrawlReq req;

    try {
      req = new CrawlReq(au);
      req.setPriority(priority);

      if (depth != null) {
        req.setRefetchDepth(depth);
      }
    } catch (RuntimeException e) {
      String errorMessage = "Can't enqueue crawl: ";
      log.error(errorMessage + au, e);
      result = getRequestCrawlResult(auId, depth, false, null, errorMessage + e.toString());
      return result;
    }

    // Perform the crawl request.
    cmi.startNewContentCrawl(req, null);
    result = getRequestCrawlResult(auId, depth, true, delayReason, null);
    return result;
  }

  private RequestCrawlResult getRequestCrawlResult(String auId, Integer depth, boolean success,
                                                   String delayReason, String errorMessage) {
    RequestCrawlResult result;
    result = new RequestCrawlResult().auId(auId).
        refetchDepth(depth).accepted(success).delayReason(delayReason).errorMessage(errorMessage);
    if (log.isDebugEnabled()) {
      log.debug(DEBUG_HEADER + "result = " + result);
    }
    return result;
  }

  RequestCrawlResult doRepair(String auId, List urls) {
    RequestCrawlResult result;
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

    //TODO: Add a cmi.startRepair with an exception mechanism instead of a callback.
    CrawlManager.Callback cb = new CrawlManager.Callback() {
      public void signalCrawlAttemptCompleted(boolean success,
                                              Object cookie,
                                              CrawlerStatus status) {
        if (log.isDebugEnabled()) {
          log.debug("Repair crawl complete: " + success + ", fetched: "
              + status.getUrlsFetched());
        }
      }
    };
    cmi.startRepair(au, urls, cb, null, null);
    return result = getRequestCrawlResult(auId, null, true, null, null);
  }

  private CrawlManagerImpl getCrawlManager() {
    if (crawlMgrImpl == null) {
      CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
      crawlMgrImpl = (CrawlManagerImpl) cmgr;
    }
    return crawlMgrImpl;
  }

  private CrawlerStatus getCrawlerStatus(String key) {
    CrawlManagerImpl cmi = getCrawlManager();
    return cmi.getStatus().getCrawlerStatus(key);
  }

  /**
   * Provides the plugin manager.
   *
   * @return the current Lockss PluginManager.
   */
  private PluginManager getPluginManager() {
    if (pluginManager == null) {
      pluginManager = (PluginManager) LockssApp.getManagerByKeyStatic(PLUGIN_MANAGER);
    }
    return pluginManager;
  }

  private UrlPager getUrlPager(String counterName, String jobId, String continuationToken, Integer limit) {
    UrlPager pager = new UrlPager();
    return pager;

  }

  private ErrorPager getErrorPager(String jobId, String continuationToken, Integer limit) {
    ErrorPager pager = new ErrorPager();
    return pager;
  }

  private JobPager getJobsPager(Integer limit, String continuationToken) {
    JobPager pager = new JobPager();
    return pager;
  }
}
