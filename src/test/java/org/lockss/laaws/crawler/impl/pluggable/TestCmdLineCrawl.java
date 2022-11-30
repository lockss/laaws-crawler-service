package org.lockss.laaws.crawler.impl.pluggable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.util.FileUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.test.LockssTestCase5;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCmdLineCrawl extends LockssTestCase5 {
  static final CrawlDesc.CrawlKindEnum NEWCONTENT = CrawlDesc.CrawlKindEnum.NEWCONTENT;
  static final CrawlDesc.CrawlKindEnum REPAIR = CrawlDesc.CrawlKindEnum.REPAIR;
  private static final String TEST_CRAWLER = "cmdLineCrawler";
  private static final String DEF_JOB_ID = "1000";
  private static final String[] TEST_CMD_LINE ={"mycmd", "-p",  "prop1"};

  private JobStatus QueuedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("queued");
  private JobStatus ActiveStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ACTIVE).msg("active");
  private JobStatus AbortedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ABORTED).msg("aborted");
  private JobStatus SuccessStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.SUCCESSFUL).msg("successful");
  File tmpDir;

  @BeforeEach
  public void beforeEach() throws IOException {
    ensureTempTmpDir();
    tmpDir = getTempDir("TestCmdLineCrawl");

  }

  @AfterEach
  public void tearDown() throws Exception {
    afterEachTempDirs();
  }

  @Test
  @DisplayName("Should return null when the tmpdir is empty")
  void getWarcFilesWhenTmpDirIsEmptyThenReturnNull() {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    crawl.tmpDir = tmpDir;
    assertEquals(0,crawl.getWarcFiles().size());
  }

  @Test
  @DisplayName("Should return a list of warc files when the tmpdir is not empty")
  void getWarcFilesWhenTmpDirIsNotEmptyThenReturnListOfWarcFiles() throws Exception {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    //add a mock warc file
    crawl.tmpDir = tmpDir;
    FileUtil.createTempFile("mock",".warc",tmpDir);
    when(crawl.getWarcFiles()).thenCallRealMethod();
    List<String> warcs = crawl.getWarcFiles();
    assertNotNull(warcs);
    assertEquals(1,warcs.size());
  }

  @Test
  @DisplayName("Should set the message to crawl aborted")
  void stopCrawlShouldSetMessageToCrawlAborted() {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    crawl.getCrawlStatus().setJobStatus(ActiveStatus);
    crawl.stopCrawl();
    assertEquals(JobStatus.StatusCodeEnum.ABORTED, crawl.getJobStatus().getStatusCode());
    assertEquals("Crawl Aborted.", crawl.getJobStatus().getMsg());
  }

  @Test
  @DisplayName("Should set the status code to active")
  void startCrawlWhenStatusCodeIsActive() {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    crawl.startCrawl();
    assertEquals(JobStatus.StatusCodeEnum.ACTIVE, crawl.getJobStatus().getStatusCode());
    assertEquals("Running.", crawl.getJobStatus().getMsg());
  }

  CmdLineCrawler makeMockCrawler() {
    CmdLineCrawler crawler = mock(CmdLineCrawler.class);
    when(crawler.getCmdLineBuilder()).thenReturn(new TestCommandLineBuilder());
    return crawler;
  }

  CmdLineCrawl makeMockCrawl(CmdLineCrawler crawler) {
    CrawlJob crawlJob = mock(CrawlJob.class);
    CrawlDesc crawlDesc = mock(CrawlDesc.class);
    when(crawlJob.getCrawlDesc()).thenReturn(crawlDesc);
    when(crawlJob.getJobId()).thenReturn(DEF_JOB_ID);
    when(crawlJob.getJobStatus()).thenReturn(new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("queued"));
    when(crawlDesc.getAuId()).thenReturn("AU_ID");
    when(crawlDesc.getCrawlDepth()).thenReturn(1);
    when(crawlDesc.getPriority()).thenReturn(1);
    when(crawlDesc.getCrawlList()).thenReturn(ListUtil.fromCSV("url1,url2"));
    when(crawlDesc.getAuId()).thenReturn("AU_ID");
    when(crawlDesc.getCrawlKind()).thenReturn(NEWCONTENT);
    when(crawlDesc.getCrawlerId()).thenReturn(TEST_CRAWLER);
    return new CmdLineCrawl(crawler, crawlJob);
  }

  private static class TestCommandLineBuilder implements CmdLineCrawler.CommandLineBuilder {

    @Override
    public List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir)  {
      return Arrays.asList(TEST_CMD_LINE);
    }
  }

}