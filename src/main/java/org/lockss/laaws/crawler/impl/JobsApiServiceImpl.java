/*
 * Copyright (c) 2000-2022, Board of Trustees of Leland Stanford Jr. University
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

package org.lockss.laaws.crawler.impl;

import static org.lockss.laaws.crawler.impl.ApiUtils.*;
import static org.lockss.servlet.DebugPanel.DEFAULT_CRAWL_PRIORITY;
import static org.lockss.servlet.DebugPanel.PARAM_CRAWL_PRIORITY;
import static org.lockss.util.rest.crawler.CrawlDesc.CLASSIC_CRAWLER_ID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.api.JobsApi;
import org.lockss.laaws.crawler.api.JobsApiDelegate;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawl;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.JsonUtil;
import org.lockss.util.RateLimiter;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import org.lockss.util.time.TimeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class JobsApiServiceImpl extends BaseSpringApiServiceImpl implements JobsApiDelegate {
  private static final L4JLogger log = L4JLogger.getLogger();
  // Error Codes
  private static final String NO_REPAIR_URLS = "No urls for repair.";
  private static final String NO_URLS = "No urls to crawl.";
  private static final String NO_SUCH_AU_ERROR_MESSAGE = "No such Archival Unit:";
  private static final String USE_FORCE_MESSAGE = "Use the 'force' parameter to override.";
  private static final String NOT_INITIALIZED_MESSAGE = "The service has not been fully initialized";
  private static final String UNKNOWN_CRAWLER_MESSAGE = "No registered crawler with id:";
  private static final String DISABLED_CRAWLER_MESSAGE = "The requested crawler is disabled:";
  private static final String UNKNOWN_CRAWL_TYPE = "Unknown crawl kind:";
  public static final String AU_HAS_QUEUED_OR_ACTIVE_CRAWL = "AU has queued or active crawl";
  private final HttpServletRequest request;

  @Autowired
  public JobsApiServiceImpl(HttpServletRequest request) {
    this.request = request;
  }

  /**
   * Get all crawl jobs
   */
  @Override
  public ResponseEntity<JobPager> getJobs(Integer limit,
                                   String continuationToken) {
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

      JobPager pager = getJobsPager(limit, continuationToken);
      log.debug2("pager = {}", pager);
      return new ResponseEntity<>(pager, HttpStatus.OK);
    }
    catch (IllegalArgumentException iae) {
      String message = "Cannot get crawls with limit = " + limit + ", continuationToken = " + continuationToken;
      log.error(message, iae);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    catch (Exception ex) {
      String message = "Cannot get crawls with limit = " + limit + ", continuationToken = " + continuationToken;
      log.error(message, ex);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  /**
   * Deletes all the currently queued and active crawl requests.
   *
   * @return a {@code ResponseEntity<Void>}.
   * @see JobsApi#deleteJobs
   */
  @Override
  public ResponseEntity<Void> deleteJobs() {
    log.debug2("Invoked");

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        log.error(NOT_INITIALIZED_MESSAGE);
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
      }

      ApiUtils.getLockssCrawlManager().deleteAllCrawls();
      ApiUtils.getPluggableCrawlManager().deleteAllCrawls();
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
   * @see JobsApi#queueJob
   */
  @Override
  public ResponseEntity<CrawlJob> queueJob(CrawlDesc crawlDesc) {
    log.debug2("crawlDesc = {}", crawlDesc);
    HttpStatus httpStatus;
    CrawlJob crawlJob = new CrawlJob().crawlDesc(crawlDesc);
    String crawlerId = crawlDesc.getCrawlerId();
    CrawlDesc.CrawlKindEnum crawlKind = crawlDesc.getCrawlKind();

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        logCrawlError(NOT_INITIALIZED_MESSAGE, crawlJob);
        return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
      }
      // Get the crawler Id and Crawl kind
      // Validate the specified crawlerId.
      if (!ApiUtils.getCrawlerIds().contains(crawlerId)) {
        logCrawlError(UNKNOWN_CRAWLER_MESSAGE + crawlerId, crawlJob);
        return new ResponseEntity<>(crawlJob, HttpStatus.BAD_REQUEST);
      }
      ArchivalUnit au = getPluginManager().getAuFromId(crawlDesc.getAuId());
      if (au == null) {
        if(getPluginManager().areAusStarted()) {
          logCrawlError(NO_SUCH_AU_ERROR_MESSAGE, crawlJob);
          return new ResponseEntity<>(crawlJob, HttpStatus.NOT_FOUND);
        }
        else {
          logCrawlError(NOT_INITIALIZED_MESSAGE, crawlJob);
          return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
        }
      }
      // Determine which crawler to use.
      if (crawlerId.equals(CLASSIC_CRAWLER_ID)) {
        // Get the Archival Unit to be crawled.
        // Handle a missing Archival Unit.
        // Determine which kind of crawl is being requested.
        switch (crawlKind) {
          case NEWCONTENT:
            httpStatus = startClassicCrawl(au, crawlJob);
            break;
          case REPAIR:
            httpStatus = startClassicRepair(au, crawlJob);
            break;
          default:
            httpStatus = HttpStatus.BAD_REQUEST;
            logCrawlError(UNKNOWN_CRAWL_TYPE + crawlKind, crawlJob);
        }
      }
      else {
        if(crawlDesc.getAuId() == null) {
          logCrawlError(NO_SUCH_AU_ERROR_MESSAGE, crawlJob);
          return new ResponseEntity<>(crawlJob, HttpStatus.NOT_FOUND);
        }
        // Determine which kind of crawl is being requested.
        switch (crawlKind) {
          case NEWCONTENT:
          case REPAIR:
            httpStatus = startExternalCrawl(au, crawlJob);
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
      String message = "Attempted crawl of '" + crawlDesc.getAuId() + "' failed:" +ex.getMessage();
      logCrawlError(message, crawlJob,ex);
      return new ResponseEntity<>(crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Provides the status of a requested crawl.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return a {@code ResponseEntity<CrawlJob>} with the status of the crawl.
   * @see JobsApi#getCrawlJob
   */
  @Override
  public ResponseEntity<CrawlJob> getCrawlJob(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlJob crawlJob;
    JobStatus jobStatus;

    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        String message = "The service has not been fully initialized";
        log.error(message);
        log.error("jobId = {}", jobId);
        jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
        crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
        return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
      }
      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      crawlJob = makeCrawlJob(crawlerStatus);
      log.debug2("CrawlJob = {}", crawlJob);
      return new ResponseEntity<>(crawlJob, HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlJob, HttpStatus.NOT_FOUND);
    }
    catch (Exception e) {
      String message = "Cannot getCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  /**
   * Deletes a crawl previously added to the crawl queue, stopping the crawl if already running.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return a {@code ResponseEntity<CrawlJob>} with the status of the deleted crawl.
   * @see JobsApi#deleteCrawlJob(String)
   */
  @Override
  public ResponseEntity<CrawlJob> deleteCrawlJob(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlJob crawlJob;
    JobStatus jobStatus;
    try {
      // Check whether the service has not been fully initialized.
      if (!waitReady()) {
        // Yes: Report the problem.
        log.error(NOT_INITIALIZED_MESSAGE);
        log.error("jobId = {}", jobId);
        jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(NOT_INITIALIZED_MESSAGE);
        crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
        return new ResponseEntity<>(crawlJob, HttpStatus.SERVICE_UNAVAILABLE);
      }

      CrawlerStatus crawlerStatus = getCrawlerStatus(jobId);
      log.debug2("crawlerStatus = {}", crawlerStatus);
      String crawlerId = crawlerStatus.getCrawlerId();

      if (crawlerStatus.isCrawlWaiting() || crawlerStatus.isCrawlActive()) {
        if(crawlerId.equals(CLASSIC_CRAWLER_ID))
          getLockssCrawlManager().deleteCrawl(crawlerStatus.getAu());
        else {
          PluggableCrawler crawler = getPluggableCrawlManager().getCrawler(crawlerStatus.getCrawlerId());
          if(crawler != null) {
            crawler.stopCrawl(jobId);
          }
        }
      }
      return new ResponseEntity<>(makeCrawlJob(crawlerStatus), HttpStatus.OK);
    }
    catch (NotFoundException nfe) {
      String message = "No crawl found for jobId '" + jobId + "'.";
      log.warn(message);
      HttpStatus httpStatus = HttpStatus.NOT_FOUND;
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlJob, httpStatus);
    }
    catch (Exception e) {
      String message = "Cannot deleteCrawlById() for jobId = '" + jobId + "'";
      log.error(message, e);
      jobStatus = new JobStatus().statusCode(StatusCodeEnum.ERROR).msg(message);
      crawlJob = new CrawlJob().jobId(jobId).jobStatus(jobStatus);
      return new ResponseEntity<>(crawlJob, HttpStatus.INTERNAL_SERVER_ERROR);
    }
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
    List<CrawlerStatus> allJobs = ApiUtils.getLockssCrawlManager().getStatus().getCrawlerStatusList();
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

  HttpStatus startClassicCrawl(ArchivalUnit au, CrawlJob crawlJob) {
    CrawlDesc crawlDesc = crawlJob.getCrawlDesc();
    Integer depth = crawlDesc.getCrawlDepth();
    Integer requestedPriority = crawlDesc.getPriority();
    boolean force = crawlDesc.isForceCrawl();

    log.debug2("au = {}", au);
    log.debug2("depth = {}", depth);
    log.debug2("requestedPriority = {}", requestedPriority);
    log.debug2("force = {}", force);

    CrawlManagerImpl cmi = ApiUtils.getLockssCrawlManager();
    // Reset the rate limiter if the request is forced.
    if (force) {
      RateLimiter limiter = cmi.getNewContentRateLimiter(au);
      log.trace("limiter = {}", limiter);

      if (!limiter.isEventOk()) {
        limiter.unevent();
      }
    }
    JobStatus jobStatus = crawlJob.getJobStatus();
    if(jobStatus == null) {
      jobStatus = new JobStatus();
      crawlJob.jobStatus(jobStatus);
    }

    String msg;
    // Handle eligibility for queuing the crawl.
    try {
      cmi.checkEligibleToQueueNewContentCrawl(au);
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
    String delayReason;

    try {
      cmi.checkEligibleForNewContentCrawl(au);
    } catch (CrawlManagerImpl.NotEligibleException nee) {
      delayReason = "Start delayed due to: " + nee.getMessage();
      crawlJob.getJobStatus().msg(delayReason);
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

    if (request != null) {
      String crawlerServiceUrl = request.getRequestURI();
      crawlJob.result(crawlerServiceUrl);
    }
    crawlJob.jobStatus(makeJobStatus(lockssCrawlStatus));
    log.debug2("result = {}", crawlJob);
    getPluggableCrawlManager().addCrawlJob(crawlJob);
    return HttpStatus.ACCEPTED;
  }

  HttpStatus startClassicRepair(ArchivalUnit au, CrawlJob crawlJob) {
    CrawlManagerImpl cmi = ApiUtils.getLockssCrawlManager();
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
    Map<String, Object> extraData = crawlJob.getCrawlDesc().getExtraCrawlerData();
    CrawlerStatus status = cmi.startRepair(au, urls, extraData);
    updateCrawlJob(crawlJob,status);
    getPluggableCrawlManager().addCrawlJob(crawlJob);
    return HttpStatus.ACCEPTED;
  }

  HttpStatus startExternalCrawl(ArchivalUnit au, CrawlJob crawlJob) {
    CrawlDesc crawlDesc = crawlJob.getCrawlDesc();
    log.debug2("crawlDesc = {}", crawlDesc);
    String msg;
    String auId = crawlDesc.getAuId();
    boolean isRepair = crawlDesc.getCrawlKind() == CrawlDesc.CrawlKindEnum.NEWCONTENT;
    PluggableCrawlManager pcMgr = getPluggableCrawlManager();
    Collection<String> urls = crawlDesc.getCrawlList();
    if(!isRepair && !pcMgr.isEligibleForCrawl(auId)) {
      logCrawlError(AU_HAS_QUEUED_OR_ACTIVE_CRAWL, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }

    if (urls == null || urls.isEmpty()) {
      // try to get the urls from the au.
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
    if (!crawler.isCrawlerEnabled()) {
      logCrawlError(DISABLED_CRAWLER_MESSAGE + crawlerId, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    crawlJob.requestDate(TimeBase.nowMs());
    try {
      // add the requested crawlJob to the CrawlQueue for that crawler.
      PluggableCrawl crawl = crawler.requestCrawl(au, crawlJob);
      CrawlerStatus crawlerStatus = crawl.getCrawlerStatus();
      updateCrawlJob(crawlJob, crawlerStatus);
      JobStatus jobStatus = crawl.getJobStatus();
      if (jobStatus.getStatusCode().equals(StatusCodeEnum.ERROR)) {
        msg = "Can't perform crawl for " + crawlDesc.getAuId()
          + ": " + jobStatus.getMsg();
        logCrawlError(msg, crawlJob);
        return HttpStatus.INTERNAL_SERVER_ERROR;
      }
      if (request != null) {
        String crawlerServiceUrl = request.getRequestURI();
        log.debug2("requestURI = {}", crawlerServiceUrl);
        crawlJob.result(crawlerServiceUrl);
      }
      log.debug2("result = {}", crawlJob);
      pcMgr.addCrawlJob(crawlJob);
      getLockssCrawlManager().getStatus().addCrawlStatus(crawlerStatus);
      return HttpStatus.ACCEPTED;
    }
    catch (IllegalArgumentException iae) {
      msg = "Invalid crawl specification for AU " + crawlDesc.getAuId() + ": " + iae.getMessage();
      logCrawlError(msg, crawlJob);
      return HttpStatus.BAD_REQUEST;
    }
    catch (RuntimeException e) {
      msg = "Can't enqueue crawl for AU ";
      logCrawlError(msg, crawlJob, e);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

  }

  static CrawlJob makeCrawlJob(CrawlerStatus cs) {
    CrawlJob crawlJob = getPluggableCrawlManager().getCrawlJob(cs.getKey());
    if(crawlJob == null) {
      crawlJob = new CrawlJob();
    }
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
    crawlJob.result(ApiUtils.makeCrawlLink(cs.getKey()));
  }


  private void logCrawlError(String message, CrawlJob crawlJob) {
    logCrawlError(message, crawlJob, null);
  }
  private void logCrawlError(String message,CrawlJob crawlJob, Throwable ex) {
    if(ex != null) {
      log.error(message, ex);
    } else {
      log.error(message);
    }
    log.error("crawlDesc = {}", crawlJob.getCrawlDesc());
    crawlJob.jobStatus(new JobStatus().statusCode(JobStatus.StatusCodeEnum.ERROR).msg(message));
    log.debug2("crawlJob = {}", crawlJob);
  }
  /**
   * Provides the response entity when there is an error.
   *
   * @param status
   *          An HttpStatus with the error HTTP status.
   * @param message
   *          A String with the error message.
   * @param e
   *          An Exception with theerror exception.
   * @return a {@code ResponseEntity<String>} with the error response entity.
   */
  private ResponseEntity<String> getErrorResponseEntity(HttpStatus status,
    String message, Exception e) {
    String errorMessage = message;

    if (e != null) {
      if (errorMessage == null) {
        errorMessage = e.getMessage();
      } else {
        errorMessage = errorMessage + " - " + e.getMessage();
      }
    }

    return new ResponseEntity<String>(JsonUtil.toJsonError(status.value(),
      errorMessage), status);
  }
}
