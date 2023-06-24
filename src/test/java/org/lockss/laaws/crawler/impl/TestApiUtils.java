package org.lockss.laaws.crawler.impl;

import static org.lockss.util.rest.crawler.CrawlDesc.CrawlKindEnum.NEWCONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawl;
import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawler;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.test.LockssTestCase5;
import org.lockss.util.time.TimeBase;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TestApiUtils extends LockssTestCase5 {

  @Mock
  PluggableCrawlManager pluggableCrawlManager;
  @Mock
  CrawlManagerImpl lockssCrawlManager;
  @InjectMocks
  ApiUtils apiUtils;
  @InjectMocks
  private ApiUtils underTest;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private CrawlJob makeCrawlJob(String auId, String jobId) {
    CrawlDesc cd =
      new CrawlDesc()
        .auId(auId)
        .crawlKind(NEWCONTENT)
        .crawlerId("crawlerId")
        .crawlDepth(1);
    return new CrawlJob().jobId(jobId).requestDate(TimeBase.nowMs()).crawlDesc(cd);
  }

  CmdLineCrawl makeMockCrawl(CmdLineCrawler crawler) {
    CrawlJob crawlJob = mock(CrawlJob.class);
    CrawlDesc crawlDesc = mock(CrawlDesc.class);
    when(crawlJob.getCrawlDesc()).thenReturn(crawlDesc);
    when(crawlJob.getJobId()).thenReturn("1001");
    when(crawlJob.getJobStatus())
      .thenReturn(
        new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("queued"));
    when(crawlDesc.getAuId()).thenReturn("AU_ID");
    when(crawlDesc.getCrawlDepth()).thenReturn(1);
    when(crawlDesc.getPriority()).thenReturn(1);
    when(crawlDesc.getCrawlList()).thenReturn(ListUtil.fromCSV("url1,url2"));
    when(crawlDesc.getAuId()).thenReturn("AU_ID");
    when(crawlDesc.getCrawlKind()).thenReturn(NEWCONTENT);
    when(crawlDesc.getCrawlerId()).thenReturn("classic");
    ArchivalUnit au = mock(ArchivalUnit.class);
    when(au.getName()).thenReturn("AU_ID");
    return new CmdLineCrawl(crawler, au, crawlJob);
  }


  @Test
  @DisplayName("Should return error when the crawl status is status_error")
  void makeJobStatusMirrorsCrawlStatus() {
    CrawlerStatus crawlerStatus = mock(CrawlerStatus.class);
    // err
    when(crawlerStatus.getCrawlStatus()).thenReturn(Crawler.STATUS_ERROR);
    when(crawlerStatus.getCrawlStatusMsg()).thenReturn("error");
    JobStatus jobStatus = ApiUtils.makeJobStatus(crawlerStatus);
    assertEquals(JobStatus.StatusCodeEnum.ERROR, jobStatus.getStatusCode());
    // queued
    when(crawlerStatus.getCrawlStatus()).thenReturn(Crawler.STATUS_QUEUED);
    jobStatus = ApiUtils.makeJobStatus(crawlerStatus);
    assertEquals(JobStatus.StatusCodeEnum.QUEUED, jobStatus.getStatusCode());

    when(crawlerStatus.getCrawlStatus()).thenReturn(Crawler.STATUS_ACTIVE);
    jobStatus = ApiUtils.makeJobStatus(crawlerStatus);
    assertEquals(JobStatus.StatusCodeEnum.ACTIVE, jobStatus.getStatusCode());

    when(crawlerStatus.getCrawlStatus()).thenReturn(Crawler.STATUS_SUCCESSFUL);
    jobStatus = ApiUtils.makeJobStatus(crawlerStatus);
    assertEquals(JobStatus.StatusCodeEnum.SUCCESSFUL, jobStatus.getStatusCode());

    when(crawlerStatus.getCrawlStatus()).thenReturn(Crawler.STATUS_ABORTED);
    jobStatus = ApiUtils.makeJobStatus(crawlerStatus);
    assertEquals(JobStatus.StatusCodeEnum.ABORTED, jobStatus.getStatusCode());

  }

  @Test
  @DisplayName("Should return a crawldesc object with the correct crawlkind when the type is new")
  void makeCrawlDescWhenTypeIsNewThenReturnCrawlDescWithCorrectCrawlKind() {
    CrawlerStatus crawlerStatus = mock(CrawlerStatus.class);
    when(crawlerStatus.getType()).thenReturn("newContent");

    CrawlDesc crawlDesc = ApiUtils.makeCrawlDesc(crawlerStatus);

    assertEquals(NEWCONTENT, crawlDesc.getCrawlKind());
  }

  @Test
  @DisplayName(
    "Should return a crawldesc object with the correct crawlkind when the type is repair")
  void makeCrawlDescWhenTypeIsRepairThenReturnCrawlDescWithCorrectCrawlKind() {
    CrawlerStatus crawlerStatus = mock(CrawlerStatus.class);;
    when(crawlerStatus.getType()).thenReturn("repair");

    CrawlDesc crawlDesc = ApiUtils.makeCrawlDesc(crawlerStatus);

    assertEquals(CrawlDesc.CrawlKindEnum.REPAIR, crawlDesc.getCrawlKind());
  }


  @Test
  @DisplayName("Should return the limit when the limit is not negative")
  void validateLimitWhenLimitIsNotNegativeThenReturnTheLimit() {
    Integer limit = 10;
    Integer result = apiUtils.validateLimit(limit);
    assertEquals(limit, result);
  }

  @Test
  @DisplayName("Should throw an exception when the limit is negative")
  void validateLimitWhenLimitIsNegativeThenThrowException() {
    Integer limit = -1;
    assertThrows(IllegalArgumentException.class, () -> apiUtils.validateLimit(limit));
  }

}
