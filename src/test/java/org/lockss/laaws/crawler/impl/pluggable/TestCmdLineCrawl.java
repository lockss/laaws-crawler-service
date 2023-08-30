package org.lockss.laaws.crawler.impl.pluggable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.state.AuState;
import org.lockss.util.FileUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.MimeUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.test.LockssTestCase5;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;

import static org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawl.errorPattern;
import static org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawl.successPattern;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

class TestCmdLineCrawl extends LockssTestCase5 {
  static final CrawlDesc.CrawlKindEnum NEWCONTENT = CrawlDesc.CrawlKindEnum.NEWCONTENT;
  static final CrawlDesc.CrawlKindEnum REPAIR = CrawlDesc.CrawlKindEnum.REPAIR;
  private static final String TEST_CRAWLER = "cmdLineCrawler";
  private static final String DEF_JOB_ID = "1000";
  private static final String[] TEST_CMD_LINE ={"wget", "--debug", "--mirror", "https://webscraper.io/test-sites/e-commerce/allinone"};
  private static final String SUCCESS_STRING=
    "2023-06-19 15:24:09 URL:https://www.example.com/wrapper/test1.html [7108/7108] -> \"data/temp/dtmp/laaws-pluggable-crawler57856/www.example.com/wrapper/test1.html.tmp\" [1]";
  private static final String FAIL_STRING="2023-06-22 15:08:52 ERROR 404: Not Found.";
  private static final String BASIC_URL = "https://www.example.com/wrapper/test1.html";
  private JobStatus QueuedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("Pending");
  private JobStatus ActiveStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ACTIVE).msg("Active");
  private JobStatus AbortedStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.ABORTED).msg("Crawl Aborted.");
  private JobStatus SuccessStatus = new JobStatus().statusCode(JobStatus.StatusCodeEnum.SUCCESSFUL).msg("Successful");
  File tmpDir;
  static String UNCOMPRESSED_WARC_FILE_EXT = ".warc";
  static String COMPRESSED_WARC_FILE_EXT = ".warc.gz";
  private static String URL_PREFIX= "https://assets.lockss.org/";
  private static String SUCCESS_URL= "https://assets.lockss.org/javadoc/1.75/daemon/overview-frame.html";
  private static String ERROR_URL="https://assets.lockss.org/robots.txt";
  private static String SUCCESS_STR="2023-07-31 18:33:42 URL:https://assets.lockss.org/javadoc/1.75/daemon/overview-frame.html [9159/9159] -> \"./assets.lockss.org/javadoc/1.75/daemon/overview-frame.html.tmp\" [1]";
  private static String ERROR_STR= "2023-07-31 18:33:41 ERROR 404: Not Found.";
  private static String ERROR_PRE= "https://assets.lockss.org/robots.txt:";

  
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
    assertEquals(0,crawl.getWarcFiles(ListUtil.list(UNCOMPRESSED_WARC_FILE_EXT)).size());
  }

  @Test
  @DisplayName("Should return a list of warc files when the tmpdir is not empty")
  void getWarcFilesWhenTmpDirIsNotEmptyThenReturnListOfWarcFiles() throws Exception {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    //add a mock warc file
    crawl.tmpDir = tmpDir;
   List<File> uncompressed = new ArrayList<>();
   List<File> compressed = new ArrayList<>();
    for(int ix =0; ix < 3; ix++) {
      uncompressed.add(FileUtil.createTempFile("mock", UNCOMPRESSED_WARC_FILE_EXT, tmpDir));
      compressed.add(FileUtil.createTempFile("mock", COMPRESSED_WARC_FILE_EXT, tmpDir));
    }

   // check getting the compressed files only
   List<String> exts = ListUtil.list("*"+UNCOMPRESSED_WARC_FILE_EXT);
   Collection<File> warcs = crawl.getWarcFiles(exts);
   assertNotNull(warcs);
   assertEquals(3,warcs.size());
   for(int ix=0; ix < 3; ix++) {
     assertTrue(warcs.contains(uncompressed.get(ix)));
   }

   // now check getting only uncompressed files
   exts = ListUtil.list("*"+COMPRESSED_WARC_FILE_EXT);
   warcs = crawl.getWarcFiles(exts);
   assertNotNull(warcs);
   assertEquals(3,warcs.size());
   for(int ix=0; ix < 3; ix++) {
     assertTrue(warcs.contains(compressed.get(ix)));
   }

   //finally check getting both uncompressed and compressed warcs.
   exts = ListUtil.list("*"+UNCOMPRESSED_WARC_FILE_EXT,"*"+COMPRESSED_WARC_FILE_EXT);
   warcs = crawl.getWarcFiles(exts);
   assertNotNull(warcs);
   assertEquals(6,warcs.size());
   for(int ix=0; ix < 3; ix++) {
     assertTrue(warcs.contains(uncompressed.get(ix)));
     assertTrue(warcs.contains(compressed.get(ix)));
   }
  }

  @Test
  @DisplayName("Should set the message to crawl aborted")
  void stopCrawlShouldSetMessageToCrawlAborted() {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    AuState auState = mock(AuState.class);
    crawl.setAuState(auState);
    crawl.getJobStatus().statusCode(ActiveStatus.getStatusCode()).msg(ActiveStatus.getMsg());
    crawl.stopCrawl();
    assertEquals(AbortedStatus.getStatusCode(), crawl.getJobStatus().getStatusCode());
    assertEquals(AbortedStatus.getMsg(), crawl.getJobStatus().getMsg());
    //should set status in queued crawls.
    crawl = makeMockCrawl(crawler);
    crawl.setAuState(auState);
    crawl.getJobStatus().statusCode(QueuedStatus.getStatusCode()).msg(QueuedStatus.getMsg());
    crawl.stopCrawl();
    assertEquals(AbortedStatus.getStatusCode(), crawl.getJobStatus().getStatusCode());
    assertEquals(AbortedStatus.getMsg(), crawl.getJobStatus().getMsg());

  }

  @Test
  @DisplayName("Should not set the message to crawl aborted, when crawl is not active or pending")
  void stopCrawlShouldNotSetMessageToCrawlAborted() {
    CmdLineCrawler crawler = makeMockCrawler();
    CmdLineCrawl crawl = makeMockCrawl(crawler);
    crawl.getJobStatus().statusCode(SuccessStatus.getStatusCode()).msg(SuccessStatus.getMsg());
    crawl.stopCrawl();
    assertEquals(SuccessStatus.getStatusCode(), crawl.getJobStatus().getStatusCode());
    assertEquals(SuccessStatus.getMsg(), crawl.getJobStatus().getMsg());

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

  @Test
  @DisplayName("Should find url in successful line.")
  void filterLineForInfoWhenSuccessful() {
    Matcher matcher = successPattern.matcher(SUCCESS_STRING);
    assertTrue(matcher.matches());
    matcher = successPattern.matcher(FAIL_STRING);
    assertFalse(matcher.matches());
  }
  @Test
  void extractUrlsFromError() {
    Matcher matcher = errorPattern.matcher(SUCCESS_STRING);
    assertFalse(matcher.matches());
    matcher = errorPattern.matcher(FAIL_STRING);
    assertTrue(matcher.matches());
  }

  @Test
  void extractBytesFromMsg() {
    long foundBytes = CmdLineCrawl.extractBytes(SUCCESS_STRING);
    assertNotEquals(0,foundBytes);
    foundBytes = CmdLineCrawl.extractBytes(FAIL_STRING);
    assertEquals(0,foundBytes);
  }

  @Test
  @DisplayName("Should correctly parse the line when the line contains an error pattern")
  void parseLineWhenLineContainsErrorPattern() {
    CmdLineCrawl cmdLineCrawl = makeMockCrawl(makeMockCrawler());
    String mimeType = MimeUtil.getMimeTypeFromExtension("txt");
    cmdLineCrawl.parseLine(ERROR_PRE, ERROR_STR);
    CrawlerStatus status = cmdLineCrawl.getCrawlerStatus();
    assertFalse(status.getUrlsFetched().contains(ERROR_URL));
    assertFalse(cmdLineCrawl.getStems().contains(URL_PREFIX));
    assertTrue(status.getMimeTypes().contains(mimeType));
    assertTrue(status.getUrlsOfMimeType(mimeType).contains((ERROR_URL)));
    assertTrue(status.getUrlsWithErrors().containsKey(ERROR_URL));
    assertEquals(1,status.getNumUrlsWithErrors());
    assertEquals("404: Not Found.", status.getErrorForUrl(ERROR_URL));
  }

  @Test
  @DisplayName("Should correctly parse the line when the line contains a success pattern")
  void parseLineWhenLineContainsSuccessPattern() {
    CmdLineCrawl cmdLineCrawl = makeMockCrawl(makeMockCrawler());
    String mimeType = MimeUtil.getMimeTypeFromExtension("html");
    cmdLineCrawl.parseLine(null, SUCCESS_STR);
    CrawlerStatus status = cmdLineCrawl.getCrawlerStatus();
    assertEquals(status.getUrlsFetched().size(), 1);
    assertTrue(status.getUrlsFetched().contains(SUCCESS_URL));
    assertTrue(cmdLineCrawl.getStems().contains(URL_PREFIX));
    assertTrue(status.getMimeTypes().contains(mimeType));
    assertTrue(status.getUrlsOfMimeType(mimeType).contains((SUCCESS_URL)));
  }

  CmdLineCrawler makeMockCrawler() {
    CmdLineCrawler crawler = mock(CmdLineCrawler.class);
    PluggableCrawlManager pcm = mock(PluggableCrawlManager.class);
    when(crawler.getCmdLineBuilder()).thenReturn(new TestCommandLineBuilder());
    when(crawler.getPluggableCrawlManager()).thenReturn(pcm );

    return crawler;
  }

  CmdLineCrawl makeMockCrawl(CmdLineCrawler crawler) {
    CrawlJob crawlJob = mock(CrawlJob.class);
    CrawlDesc crawlDesc = mock(CrawlDesc.class);
    JobStatus js = new JobStatus().statusCode(JobStatus.StatusCodeEnum.QUEUED).msg("queued");
    when(crawlJob.getCrawlDesc()).thenReturn(crawlDesc);
    when(crawlJob.getJobId()).thenReturn(DEF_JOB_ID);
    when(crawlJob.getJobStatus()).thenReturn(js);
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