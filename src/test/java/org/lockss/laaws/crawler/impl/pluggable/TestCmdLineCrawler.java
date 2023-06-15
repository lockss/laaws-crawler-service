package org.lockss.laaws.crawler.impl.pluggable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.rest.repo.LockssRepository;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.test.LockssTestCase5;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class TestCmdLineCrawler extends LockssTestCase5 {
  private CmdLineCrawler cmdLineCrawler;
  private static final String PREFIX = CmdLineCrawler.PREFIX;
  private static final String DEF_CRAWLER_ID = "test";
  private static final String ATTR_CRAWLER_ID = PREFIX +  "." + DEF_CRAWLER_ID + ".crawlerId";
  private static final String ATTR_CRAWLING_ENABLED = PREFIX +  "." + DEF_CRAWLER_ID + ".crawlingEnabled";
  private static final String ATTR_STARTER_ENABLED = PREFIX + "." + DEF_CRAWLER_ID + ".starterEnabled";
  private static final String ATTR_CMDLINE_BUILDER = PREFIX + "." +DEF_CRAWLER_ID +".cmdLineBuilder";
  private static final String DEF_NAMESPACE = "testrepo";
  private static final String DEF_JOB_ID = "1000";
  private static final String DEF_AU_ID = "auid";
  private static final String[] TEST_CMD_LINE ={"mycmd", "-p",  "prop1"};

  private static final List<String> START_URLS = ListUtil.fromCSV("url1,url2");
  static final CrawlDesc.CrawlKindEnum NEWCONTENT = CrawlDesc.CrawlKindEnum.NEWCONTENT;
  static final CrawlDesc.CrawlKindEnum REPAIR = CrawlDesc.CrawlKindEnum.REPAIR;

  private PluggableCrawlManager pluggableCrawlManager;
  private LockssRepository lockssRepository;

  private File tmpDir;
  private File dbFile;

  private CrawlerConfig crawlerConfig;

  @BeforeEach
  public void beforeEach() throws IOException {
    ensureTempTmpDir();

    tmpDir = getTempDir("TestCmdLineCrawler");
    dbFile = new File(tmpDir, "testDb");
    Map<String, String> attrs = new HashMap<>();
    attrs.put(ATTR_CRAWLER_ID, DEF_CRAWLER_ID);
    attrs.put(ATTR_CRAWLING_ENABLED, "true");
    attrs.put(ATTR_STARTER_ENABLED, "true");
    crawlerConfig = new CrawlerConfig();
    crawlerConfig.setCrawlerId(DEF_CRAWLER_ID);
    crawlerConfig.setAttributes(attrs);
    pluggableCrawlManager = mock(PluggableCrawlManager.class);
    lockssRepository = mock(LockssRepository.class);
    cmdLineCrawler = new CmdLineCrawler()
      .setCrawlManager(pluggableCrawlManager)
      .setNamespace("lockss")
      .setConfig(crawlerConfig)
      .setV2Repo(lockssRepository)
      .setCmdLineBuilder(new TestCommandLineBuilder());
    cmdLineCrawler.updateCrawlerConfig(crawlerConfig);
    when(pluggableCrawlManager.isEligibleForCrawl(DEF_AU_ID)).thenReturn(true);
  }

  @AfterEach
  public void afterEach() throws Exception{
    //cmdLineCrawler.shutdown();
    afterEachTempDirs();
  }


  @Test
  @DisplayName("Should stop all crawls")
  void deleteAllCrawlsShouldStopAllCrawls() {
    when(pluggableCrawlManager.isEligibleForCrawl(DEF_AU_ID)).thenReturn(true);
    CrawlJob crawlJob = makeMockCrawlJob(DEF_AU_ID, DEF_CRAWLER_ID);
    ArchivalUnit au = mock(ArchivalUnit.class);
    when(au.getName()).thenReturn(DEF_AU_ID);
    cmdLineCrawler.requestCrawl(au,crawlJob);
    cmdLineCrawler.deleteAllCrawls();
    assertEquals(0, cmdLineCrawler.crawlMap.size());
  }

  @Test
  @DisplayName("Should throw an exception when the warc file is not found")
  void storeInRepositoryWhenWarcFileNotFoundThenThrowException() {
    CrawlJob crawlJob = makeMockCrawlJob(DEF_AU_ID, DEF_CRAWLER_ID);
    assertThrows(
      IOException.class,
      () ->
        cmdLineCrawler.storeInRepository(
          DEF_AU_ID, new File("/tmp/warcFile"), false));
  }

  @Test
  @DisplayName("Should store the warc file in the repository")
  void storeInRepositoryShouldStoreWarcFileInRepository() {
    try {
      File warcfile = getTempFile("test",".warc");
      CrawlJob crawlJob = makeMockCrawlJob(DEF_AU_ID, DEF_CRAWLER_ID);
      when(lockssRepository.isReady()).thenReturn(true);
      cmdLineCrawler.storeInRepository(DEF_AU_ID, warcfile, false);
      verify(lockssRepository,times(1)).addArtifacts(anyString(),anyString(),anyObject(),
        anyObject(),anyBoolean());
    }
    catch (IOException e) {
      fail("store Warc failed");
    }
  }

  @Test
  @DisplayName("Should return null when the crawl is not in the map")
  void stopCrawlShouldReturnNullWhenTheCrawlIsNotInTheMap() {
    String crawlId = "crawlId";
    PluggableCrawl pluggableCrawl = cmdLineCrawler.stopCrawl(crawlId);
    assertNull(pluggableCrawl);
  }

  @Test
  @DisplayName("Should remove the crawl from the map")
  void stopCrawlShouldRemoveTheCrawlFromTheMap() {
    when(pluggableCrawlManager.isEligibleForCrawl(DEF_AU_ID)).thenReturn(true);
    CrawlJob crawlJob = makeMockCrawlJob(DEF_AU_ID,DEF_CRAWLER_ID);
    ArchivalUnit au = mock(ArchivalUnit.class);
    when(au.getName()).thenReturn(DEF_AU_ID);
    cmdLineCrawler.requestCrawl(au,crawlJob);
    assertEquals(1, cmdLineCrawler.crawlMap.size());
    cmdLineCrawler.stopCrawl(DEF_JOB_ID);
    assertEquals(0, cmdLineCrawler.crawlMap.size());
  }

  @Test
  @DisplayName("Should return null when the au is not eligible for crawl")
  void requestCrawlWhenAuIsNotEligibleForCrawlThenReturnNull() {
    when(pluggableCrawlManager.isEligibleForCrawl(DEF_AU_ID)).thenReturn(false);
    CrawlJob crawlJob = makeMockCrawlJob(DEF_AU_ID,DEF_CRAWLER_ID);
    ArchivalUnit au = mock(ArchivalUnit.class);
    when(au.getName()).thenReturn(DEF_AU_ID);
    when(au.getAuId()).thenReturn(DEF_AU_ID);
    PluggableCrawl pluggableCrawl = cmdLineCrawler.requestCrawl(au,crawlJob);
    assertNull(pluggableCrawl);
  }

  @Test
  @DisplayName("Should set the crawler config")
  void updateCrawlerConfigShouldSetTheCrawlerConfig() {
    CrawlerConfig crawlerConfig = new CrawlerConfig();
    Map<String, String> attr = new HashMap<>();
    attr.put(ATTR_CRAWLER_ID, "classic");
    attr.put(ATTR_CRAWLING_ENABLED, "true");
    attr.put(ATTR_STARTER_ENABLED, "true");
    crawlerConfig.setAttributes(attr);
    cmdLineCrawler.updateCrawlerConfig(crawlerConfig);
    assertEquals(crawlerConfig, cmdLineCrawler.getCrawlerConfig());
  }

  @Test
  @DisplayName(
    "Should instantiate the command line builder when the builder class name is not null")
  void
  updateCrawlerConfigShouldInstantiateTheCommandLineBuilderWhenTheBuilderClassNameIsNotNull() {
    Map<String, String> attr = new HashMap<>();
    attr.put(ATTR_CRAWLER_ID, "classic");
    attr.put(ATTR_CRAWLING_ENABLED, "true");
    attr.put(ATTR_STARTER_ENABLED, "true");
    attr.put(ATTR_CMDLINE_BUILDER, "org.lockss.laaws.crawler.TestCmdLineCrawler.TestCommandLineBuilder");
    CrawlerConfig crawlerConfig = new CrawlerConfig();
    crawlerConfig.setAttributes(attr);
    cmdLineCrawler.updateCrawlerConfig(crawlerConfig);
    assertNotNull(cmdLineCrawler.getCmdLineBuilder());
  }



  CrawlJob makeMockCrawlJob(String auId, String crawlerId) {
    CrawlJob crawlJob = mock(CrawlJob.class);
    CrawlDesc crawlDesc = makeMockCrawlDesc(auId, crawlerId);
    JobStatus js = new JobStatus();
    when(crawlJob.getCrawlDesc()).thenReturn(crawlDesc);
    when(crawlJob.getJobStatus()).thenReturn(js);
    when(crawlJob.getJobId()).thenReturn(DEF_JOB_ID);
    return crawlJob;
  }

  CrawlDesc makeMockCrawlDesc(String auId, String crawlerId) {
    CrawlDesc crawlDesc = mock(CrawlDesc.class);
    when(crawlDesc.getCrawlDepth()).thenReturn(1);
    when(crawlDesc.getPriority()).thenReturn(1);
    when(crawlDesc.getCrawlList()).thenReturn(START_URLS);
    when(crawlDesc.getAuId()).thenReturn(DEF_AU_ID);
    when(crawlDesc.getCrawlKind()).thenReturn(NEWCONTENT);
    when(crawlDesc.getCrawlerId()).thenReturn(crawlerId);
    return crawlDesc;
  }

  static class TestCommandLineBuilder implements CmdLineCrawler.CommandLineBuilder {

    @Override
    public List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir) {
      return Arrays.asList(TEST_CMD_LINE);
    }
  }
}