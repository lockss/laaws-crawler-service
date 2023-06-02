package org.lockss.laaws.crawler.impl;

import org.lockss.app.LockssApp;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.model.*;
import org.lockss.laaws.crawler.utils.ContinuationToken;
import org.lockss.log.L4JLogger;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.JobStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

import javax.ws.rs.NotFoundException;

import static org.lockss.daemon.Crawler.STATUS_QUEUED;
import static org.lockss.laaws.crawler.CrawlerApplication.PLUGGABLE_CRAWL_MANAGER;
import static org.lockss.util.rest.crawler.CrawlDesc.CLASSIC_CRAWLER_ID;

public class ApiUtils {
  private static final L4JLogger log = L4JLogger.getLogger();
  // A template URI for returning a counter for a specific URL list (eg. found
  // or parsed URLs).
  private static final String COUNTER_URI = "crawls/{jobId}/{counterName}";
  // A template URI for returning a counter for a list of URLs of a specific
  // mimeType.
  private static final String MIME_URI = "crawls/{jobId}/mimeType/{mimeType}";
  private static PluggableCrawlManager pluggableCrawlManager;
  private static CrawlManagerImpl lockssCrawlManager;

  /**
   * Checks that a limit field is valid.
   *
   * @param limit An Integer with the limit value to validate.
   * @return an Integer with the validated limit value.
   * @throws IllegalArgumentException if the passed limit value is not valid.
   */
  public static Integer validateLimit(Integer limit) throws IllegalArgumentException {
    // check limit if assigned is greater than 0
    if (limit != null && limit < 0) {
      String errMsg = "Invalid limit: limit must be a non-negative integer; " + "it was '" + limit + "'";
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }

    return limit;
  }

  /**
   * Provides the Pluggable Crawl manager.
   *
   * @return a PluggableCrawlManager with the crawl manager implementation.
   */

  public static PluggableCrawlManager getPluggableCrawlManager() {
    if (pluggableCrawlManager == null) {
      pluggableCrawlManager = (PluggableCrawlManager) LockssApp.getManagerByKeyStatic(PLUGGABLE_CRAWL_MANAGER);
    }
    return pluggableCrawlManager;
  }

  /**
   * Provide the Lockss CrawlManager
   *
   * @return CrawlManagerImpl
   */
  public static CrawlManagerImpl getLockssCrawlManager() {
    CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
    if (cmgr instanceof CrawlManagerImpl) {
      lockssCrawlManager = (CrawlManagerImpl) cmgr;
     }
    return lockssCrawlManager;
  }

  public static PageInfo getPageInfo(Integer resultsPerPage, Long lastElement, int totalCount, Long timeStamp) {
    log.debug2("resultsPerPage = {}", resultsPerPage);
    log.debug2("lastElement = {}", lastElement);
    log.debug2("totalCount = {}", totalCount);
    log.debug2("timeStamp = {}", timeStamp);

    PageInfo pi = new PageInfo();

    pi.setTotalCount(totalCount);
    pi.setResultsPerPage(resultsPerPage);

    ServletUriComponentsBuilder builder = getServletUrlBuilder();

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


  public static CrawlDesc makeCrawlDesc(CrawlerStatus cs) {
    CrawlDesc desc = new CrawlDesc()
      .auId(cs.getAuId())
      .crawlDepth(cs.getDepth())
      .crawlList((List<String>) cs.getStartUrls())
      .crawlerId(cs.getCrawlerId())
      .refetchDepth(cs.getRefetchDepth())
      .priority(cs.getPriority());

    String crawlType = cs.getType().toLowerCase();
    log.debug2("Found crawl type string: {}", crawlType);
    if (crawlType.startsWith("new")) {
      desc.setCrawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    }
    else {
      desc.setCrawlKind(CrawlDesc.CrawlKindEnum.REPAIR);
    }

    return desc;
  }

  public static JobStatus makeJobStatus(CrawlerStatus crawlerStatus) {
    JobStatus js = new JobStatus();
    JobStatus.StatusCodeEnum statusCode;
    switch (crawlerStatus.getCrawlStatus()) {
      case STATUS_QUEUED:
        statusCode = JobStatus.StatusCodeEnum.QUEUED;
        break;
      case org.lockss.daemon.Crawler.STATUS_ACTIVE:
        statusCode = JobStatus.StatusCodeEnum.ACTIVE;
        break;
      case org.lockss.daemon.Crawler.STATUS_SUCCESSFUL:
        statusCode = JobStatus.StatusCodeEnum.SUCCESSFUL;
        break;
      case org.lockss.daemon.Crawler.STATUS_ERROR:
        statusCode = JobStatus.StatusCodeEnum.ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_ABORTED:
        statusCode = JobStatus.StatusCodeEnum.ABORTED;
        break;
      case org.lockss.daemon.Crawler.STATUS_WINDOW_CLOSED:
        statusCode = JobStatus.StatusCodeEnum.WINDOW_CLOSED;
        break;
      case org.lockss.daemon.Crawler.STATUS_FETCH_ERROR:
        statusCode = JobStatus.StatusCodeEnum.FETCH_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_NO_PUB_PERMISSION:
        statusCode = JobStatus.StatusCodeEnum.NO_PUB_PERMISSION;
        break;
      case org.lockss.daemon.Crawler.STATUS_PLUGIN_ERROR:
        statusCode = JobStatus.StatusCodeEnum.PLUGIN_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_REPO_ERR:
        statusCode = JobStatus.StatusCodeEnum.REPO_ERR;
        break;
      case org.lockss.daemon.Crawler.STATUS_RUNNING_AT_CRASH:
        statusCode = JobStatus.StatusCodeEnum.RUNNING_AT_CRASH;
        break;
      case org.lockss.daemon.Crawler.STATUS_EXTRACTOR_ERROR:
        statusCode = JobStatus.StatusCodeEnum.EXTRACTOR_ERROR;
        break;
      case org.lockss.daemon.Crawler.STATUS_CRAWL_TEST_SUCCESSFUL:
        statusCode = JobStatus.StatusCodeEnum.CRAWL_TEST_SUCCESSFUL;
        break;
      case org.lockss.daemon.Crawler.STATUS_CRAWL_TEST_FAIL:
        statusCode = JobStatus.StatusCodeEnum.CRAWL_TEST_FAIL;
        break;
      case org.lockss.daemon.Crawler.STATUS_INELIGIBLE:
        statusCode = JobStatus.StatusCodeEnum.INELIGIBLE;
        break;
      case org.lockss.daemon.Crawler.STATUS_INACTIVE_REQUEST:
        statusCode = JobStatus.StatusCodeEnum.INACTIVE_REQUEST;
        break;
      case org.lockss.daemon.Crawler.STATUS_INTERRUPTED:
        statusCode = JobStatus.StatusCodeEnum.INTERRUPTED;
        break;
      default:
        statusCode = JobStatus.StatusCodeEnum.UNKNOWN;
    }
    js.setStatusCode(statusCode);
    js.setMsg(crawlerStatus.getCrawlStatusMsg());
    return js;
  }

  public static CrawlStatus makeCrawlStatus(CrawlerStatus cs) {
    String key = cs.getKey();
    CrawlStatus crawlStatus = new CrawlStatus()
      .jobId(cs.getKey())
      .auId(cs.getAuId())
      .auName(cs.getAuName())
      .type(cs.getType())
      .crawlerId(cs.getCrawlerId())
      .startTime(cs.getStartTime())
      .endTime(cs.getEndTime())
      .jobStatus(ApiUtils.makeJobStatus(cs))
      .isWaiting(cs.isCrawlWaiting())
      .isActive(cs.isCrawlActive())
      .isError(cs.isCrawlError())
      .priority(cs.getPriority())
      .bytesFetched(cs.getContentBytesFetched())
      .depth(cs.getDepth())
      .refetchDepth(cs.getRefetchDepth())
      .proxy(cs.getProxy())
      .fetchedItems(makeCounter(COUNTER_KIND.fetched, key, cs.getFetchedCtr()))
      .excludedItems( makeCounter(COUNTER_KIND.excluded, key, cs.getExcludedCtr()))
      .notModifiedItems( makeCounter(COUNTER_KIND.notmodified, key, cs.getNotModifiedCtr()))
      .parsedItems(makeCounter(COUNTER_KIND.parsed, key, cs.getParsedCtr()))
      .sources((List<String>) cs.getSources())
      .pendingItems(makeCounter(COUNTER_KIND.pending, key, cs.getPendingCtr()))
      .errors(makeCounter(COUNTER_KIND.errors, key, cs.getErrorCtr()))
      .startUrls((List<String>) cs.getStartUrls());

    // Add the MIME types array if needed.
    Collection<String> mimeTypes = cs.getMimeTypes();

    if (mimeTypes != null && !mimeTypes.isEmpty()) {
      List<MimeCounter> typeList = new ArrayList<>();

      for (String mtype : mimeTypes) {
        typeList.add(makeMimeCounter(key, mtype, cs.getMimeTypeCtr(mtype)));
      }

      crawlStatus.setMimeTypes(typeList);
    }

    return crawlStatus;
  }

  static UrlInfo makeUrlInfo(String url, CrawlerStatus status) {
    UrlInfo uInfo = new UrlInfo();
    uInfo.url(url);
    CrawlerStatus.UrlErrorInfo errInfo = status.getErrorInfoForUrl(url);
    if (errInfo != null) {
      UrlError error = new UrlError();
      error.setMessage(errInfo.getMessage());
      error.setSeverity(UrlError.SeverityEnum.fromValue(errInfo.getSeverity().name()));
      uInfo.setError(error);
    }
    uInfo.setReferrers(status.getReferrers(url));
    return uInfo;
  }

  public static List<String> getCrawlerIds() {
    return getPluggableCrawlManager().getCrawlerIds();
  }

  /**
   * Checks that a continuation token is valid.
   *
   * @param timeStamp A long with the timestamp used to validate the continuation token.
   * @param continuationToken A ContinuationToken with the continuation token to be validated.
   * @throws IllegalArgumentException if the passed continuation token is not valid.
   */
  public static void validateContinuationToken(long timeStamp, ContinuationToken continuationToken) throws IllegalArgumentException {
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
   * @param kind the type of counter we will be returning
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param urlCount the number of urls
   * @return an newly constructed Counter
   */
  public static Counter makeCounter(COUNTER_KIND kind, String jobId, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<>();
    uriVariables.put("jobId", jobId);
    uriVariables.put("counterName", kind.name());
    String path = UriComponentsBuilder.fromPath(COUNTER_URI).buildAndExpand(uriVariables).toUriString();
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
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @param mimeType The mine type we are counting
   * @param urlCount The number of urls
   * @return A newly constructed MimeCounter of mimeType
   */
  static MimeCounter makeMimeCounter(String jobId, String mimeType, CrawlerStatus.UrlCount urlCount) {
    // create path and map variables
    final Map<String, Object> uriVariables = new HashMap<>();

    uriVariables.put("jobId", jobId);
    uriVariables.put("mimeType", mimeType);
    String path = UriComponentsBuilder.fromPath(MIME_URI).buildAndExpand(uriVariables).toUriString();
    MimeCounter ctr = new MimeCounter();
    ctr.mimeType(mimeType);
    ctr.count(urlCount.getCount());
    ctr.counterLink(path);
    return ctr;
  }

  /* Return a CrawlStatus for the jobId.
   *
   * @param jobId A String with the identifier assigned to the crawl when added.
   */
  public static CrawlStatus getCrawlStatus(String jobId) {
    log.debug2("jobId = {}", jobId);

    CrawlerStatus cs = getCrawlerStatus(jobId);
    return makeCrawlStatus(cs);
  }

  /**
   * @param jobId A String with the identifier assigned to the crawl when added.
   * @return The CrawlerStatus for thi if the s job
   * @throws NotFoundException if there is no crawl status for this job
   */
  public static CrawlerStatus getCrawlerStatus(String jobId) throws NotFoundException {
    CrawlerStatus cs = getLockssCrawlManager().getStatus().getCrawlerStatus(jobId);
    if (cs == null) {
      String message = "No Job found for '" + jobId + "'";
      log.warn(message);
      throw new NotFoundException();
    }
    return cs;
  }

  static ServletUriComponentsBuilder getServletUrlBuilder() {
    return ServletUriComponentsBuilder.fromCurrentRequest();
  }


  enum COUNTER_KIND {
    errors, excluded, fetched, notmodified, parsed, pending
  }

}
