package org.lockss.laaws.crawler.impl.pluggable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.FileUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.test.LockssTestCase5;

class TestCmdLineCrawl extends LockssTestCase5 {
  static final CrawlDesc.CrawlKindEnum NEWCONTENT = CrawlDesc.CrawlKindEnum.NEWCONTENT;
  static final CrawlDesc.CrawlKindEnum REPAIR = CrawlDesc.CrawlKindEnum.REPAIR;
  private static final String TEST_CRAWLER = "cmdLineCrawler";
  private static final String DEF_JOB_ID = "1000";
  private static final String[] TEST_CMD_LINE ={"wget", "--debug", "--mirror", "https://webscraper.io/test-sites/e-commerce/allinone"};

  private JobStatus QueuedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("Pending");
  private JobStatus ActiveStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ACTIVE).msg("Active");
  private JobStatus AbortedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ABORTED).msg("Aborted");
  private JobStatus SuccessStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.SUCCESSFUL).msg("Successful");
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
    assertEquals(0,crawl.getWarcFiles("warc").size());
  }

  @Test
  @DisplayName("Should return a list of warc file names when the tmpdir is not empty")
  void getWarcFileNamesWhenTmpDirIsNotEmptyThenReturnListOfWarcFiles() throws Exception {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    //add a mock warc file
    crawl.tmpDir = tmpDir;
    List<String> expected = new ArrayList<>();
    for(int ix =0; ix < 3; ix++) {
      expected.add(FileUtil.createTempFile("mock", ".warc", tmpDir).getName());
    }
    when(crawl.getWarcFileNames("warc")).thenCallRealMethod();
    List<String> warcs = crawl.getWarcFileNames("warc");
    assertNotNull(warcs);
    assertEquals(3,warcs.size());
    assertTrue(warcs.containsAll(expected));
  }

 @Test
  @DisplayName("Should return a list of warc files when the tmpdir is not empty")
  void getWarcFilesWhenTmpDirIsNotEmptyThenReturnListOfWarcFiles() throws Exception {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    //add a mock warc file
    crawl.tmpDir = tmpDir;
    List<File> expected = new ArrayList<>();
    for(int ix =0; ix < 3; ix++) {
      expected.add(FileUtil.createTempFile("mock", ".warc", tmpDir));
    }
    when(crawl.getWarcFiles(".warc")).thenCallRealMethod();
    Collection<File> warcs = crawl.getWarcFiles("*.warc");
    assertNotNull(warcs);
    assertEquals(3,warcs.size());
    for(int ix=0; ix < 3; ix++) {
      assertTrue(warcs.contains(expected.get(ix)));
    }
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
    assertEquals("Active.", crawl.getJobStatus().getMsg());
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
    ArchivalUnit au = mock(ArchivalUnit.class);
    when(au.getName()).thenReturn("AU_ID");
    return new CmdLineCrawl(crawler, au, crawlJob);
  }

  private static class TestCommandLineBuilder implements CmdLineCrawler.CommandLineBuilder {

    @Override
    public List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir)  {
      return Arrays.asList(TEST_CMD_LINE);
    }
  }

}