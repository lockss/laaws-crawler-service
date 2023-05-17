package org.lockss.laaws.crawler.impl;

import org.dizitart.no2.objects.Cursor;
import org.dizitart.no2.objects.ObjectRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.test.ConfigurationUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.test.LockssTestCase5;
import org.lockss.util.time.TimeBase;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.dizitart.no2.objects.filters.ObjectFilters.eq;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.CRAWLER_IDS;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.PREFIX;
import static org.mockito.Mockito.*;

class TestPluggableCrawlManager  extends LockssTestCase5 {
    private PluggableCrawlManager pluggableCrawlManager;

    private File tmpDir;
    private File dbFile;
    private Configuration config;
    private Configuration prevConfig;
    private Configuration.Differences changedKeys;
    private PluggableCrawler crawler;
    private CrawlManagerImpl lockssCrawlMgr;

    private ObjectRepository<CrawlJob> testRepository;

    @BeforeEach
    public void setUp() throws IOException {
        pluggableCrawlManager = new PluggableCrawlManager();
        tmpDir = getTempDir();
        dbFile = new File(tmpDir, "testDb");
    }

    @AfterEach
    public void tearDown() {
        pluggableCrawlManager.getCrawlServiceDb().close();
    }

    @Test
    @DisplayName("Should save the crawl job when the jobid does not exist")
    void addCrawlJobWhenJobIdDoesNotExistThenSaveTheCrawlJob() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        pluggableCrawlManager.addCrawlJob(crawlJob);
        Cursor<CrawlJob> cursor = testRepository.find(eq("jobId", "job1"));
        assertEquals(1, cursor.size());
    }


    @Test
    @DisplayName("Should throw an exception when the jobid already exists")
    void addCrawlJobWhenJobIdAlreadyExistsThenThrowException() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        testRepository.insert(crawlJob);
        assertThrows(
          IllegalStateException.class, (Executable) () -> pluggableCrawlManager.addCrawlJob(crawlJob));
    }

    @Test
    @DisplayName("Should return a cursor of crawl jobs with the given auid")
    void getCrawlJobsWithAuIdShouldReturnCursorOfCrawlJobsWithGivenAuId() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        testRepository.insert(makeCrawlJob("au3", "job3"));

        Cursor<CrawlJob> cursor = pluggableCrawlManager.getCrawlJobsWithAuId("au1");

        Assertions.assertNotNull(cursor);
    }

    @Test
    @DisplayName("Should return an empty cursor when there are no crawl jobs with the given auid")
    void getCrawlJobsWithAuIdShouldReturnEmptyCursorWhenNoCrawlsForGivenAuId() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        testRepository.insert(makeCrawlJob("au3", "job3"));

        Cursor<CrawlJob> cursor = pluggableCrawlManager.getCrawlJobsWithAuId("au4");

        Assertions.assertFalse(cursor.iterator().hasNext());
    }
    @Test
    @DisplayName("Should return the crawl job when the jobid exists")
    void getCrawlJobWhenJobIdExists() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        testRepository.insert(crawlJob);
        CrawlJob result = pluggableCrawlManager.getCrawlJob("job1");
        Assertions.assertEquals(crawlJob, result);
    }

    @Test
    @DisplayName("Should return null when the jobid does not exist")
    void getCrawlJobWhenJobIdDoesNotExist() {
        pluggableCrawlManager.initDb(dbFile);
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        pluggableCrawlManager.addCrawlJob(crawlJob);
        CrawlJob result = pluggableCrawlManager.getCrawlJob("job21");
        Assertions.assertNull(result);
    }

    @Test
    @DisplayName(
      "Should return false when there is a crawl job with the given auid and no end date")
    void
    isEligibleForCrawlNoEnd() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        pluggableCrawlManager.addCrawlJob(crawlJob);
        crawlJob.setStartDate(TimeBase.nowMs());
        crawlJob.setEndDate(null);
        pluggableCrawlManager.updateCrawlJob(crawlJob);
        Assertions.assertFalse(pluggableCrawlManager.isEligibleForCrawl("au1"));
        crawlJob.setStartDate(TimeBase.nowMs());
        crawlJob.setEndDate(TimeBase.nowMs());
        pluggableCrawlManager.updateCrawlJob(crawlJob);
        Assertions.assertTrue(pluggableCrawlManager.isEligibleForCrawl("au1"));
    }

    @Test
    @DisplayName(
      "Should return false when there is a crawl job with the given auid and no end date")
    void
    isEligibleForCrawlEnded() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        CrawlJob crawlJob = makeCrawlJob("au1", "job1");
        pluggableCrawlManager.addCrawlJob(crawlJob);
        crawlJob.setStartDate(TimeBase.nowMs());
        crawlJob.setEndDate(TimeBase.nowMs());
        pluggableCrawlManager.updateCrawlJob(crawlJob);
        Assertions.assertTrue(pluggableCrawlManager.isEligibleForCrawl("au1"));
    }

    @Test
    @DisplayName("Should close the database")
    void stopServiceShouldCloseTheDatabase() {
        pluggableCrawlManager.initDb(dbFile);
        pluggableCrawlManager.stopService();
        assert(pluggableCrawlManager.getPluggableCrawls().isClosed());
        assert(pluggableCrawlManager.getCrawlServiceDb().isClosed());
    }

    @Test
    @DisplayName("Should commit the database")
    void stopServiceShouldCommitTheDatabase() {
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        testRepository.insert(makeCrawlJob("au3", "job3"));
        Assertions.assertTrue(pluggableCrawlManager.getCrawlServiceDb().hasUnsavedChanges());
        pluggableCrawlManager.stopService();
        Assertions.assertFalse(pluggableCrawlManager.getCrawlServiceDb().hasUnsavedChanges());
    }


    @Test
    @DisplayName("Should create a new database when the database does not exist")
    void initDbWhenDatabaseDoesNotExist() {
        Assertions.assertNull(pluggableCrawlManager.getCrawlServiceDb());
        pluggableCrawlManager.initDb(dbFile);
        Assertions.assertNotNull(pluggableCrawlManager.getCrawlServiceDb());
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        Assertions.assertEquals(0, testRepository.size());
        // add some entries and commit them before closing the db.
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        testRepository.insert(makeCrawlJob("au3", "job3"));
        pluggableCrawlManager.getCrawlServiceDb().commit();
        pluggableCrawlManager.getCrawlServiceDb().close();
        // Check that db does exist
        pluggableCrawlManager.initDb(dbFile);
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        assertEquals(3,testRepository.size());
    }

    @Test
    @DisplayName("Should update crawlerids when the crawlerids key is changed")
    void setConfigWhenCrawlerIdsKeyIsChangedThenUpdateCrawlerIds() {
        // ConfiguManager.CONFIGMANAGER_EMPTY_CONFIGURTION
        pluggableCrawlManager.initDb(dbFile);
        config = ConfigurationUtil.fromArgs(CRAWLER_IDS,"classic;wget");
        prevConfig = ConfigurationUtil.fromArgs(CRAWLER_IDS, "classic");
        changedKeys = mock(Configuration.Differences.class);
        crawler = mock(PluggableCrawler.class);
        lockssCrawlMgr = mock(CrawlManagerImpl.class);
        when(changedKeys.contains(PREFIX)).thenReturn(true);
        List<String> expected = ListUtil.list("classic", "wget");
        pluggableCrawlManager.setConfig(config, prevConfig, changedKeys);
        assertArrayEquals(expected.toArray(), pluggableCrawlManager.getCrawlerIds().toArray());
    }

    @Test
    @DisplayName("Should disable removed crawlers when the crawlerids key is changed")
    void setConfigWhenCrawlerIdsKeyIsChangedThenDisableRemovedCrawlers() {
        pluggableCrawlManager.initDb(dbFile);
        config = ConfigurationUtil.fromArgs(CRAWLER_IDS, "crawler1;crawler2;crawler3");
        prevConfig = ConfigurationUtil.fromArgs(CRAWLER_IDS,"crawler1;crawler2");
        changedKeys = mock(Configuration.Differences.class);
        crawler = mock(PluggableCrawler.class);
        lockssCrawlMgr = mock(CrawlManagerImpl.class);
        when(changedKeys.contains(PREFIX)).thenReturn(true);
        // Set it to three crawlers
        List<String> expected = ListUtil.list("crawler1", "crawler2", "crawler3");
        pluggableCrawlManager.setConfig(config, prevConfig, changedKeys);
        Assertions.assertArrayEquals(expected.toArray(), pluggableCrawlManager.getCrawlerIds().toArray());
        // Set it to two crawlers
        expected = ListUtil.list("crawler1", "crawler2");
        pluggableCrawlManager.setConfig(prevConfig, config, changedKeys);
        assertEquals(expected, pluggableCrawlManager.getCrawlerIds());
    }

    @Test
    @DisplayName("Should throw an exception when the jobid does not exist")
    void updateCrawlJobWhenJobIdDoesNotExistThenThrowException() {
        pluggableCrawlManager.initDb(dbFile);
        assertNotNull(pluggableCrawlManager.getCrawlServiceDb());
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        // add some entries and commit them before closing the db.
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        CrawlJob crawlJob = makeCrawlJob("au3", "job3");

        assertThrows(
          IllegalStateException.class, (Executable) () -> pluggableCrawlManager.updateCrawlJob(crawlJob));
    }

    @Test
    @DisplayName("Should update the crawl job when the jobid exists")
    void updateCrawlJobWhenJobIdExistsThenUpdateTheCrawlJob() {
        pluggableCrawlManager.initDb(dbFile);
        Assertions.assertNotNull(pluggableCrawlManager.getCrawlServiceDb());
        testRepository = pluggableCrawlManager.getPluggableCrawls();
        // add some entries and commit them before closing the db.
        testRepository.insert(makeCrawlJob("au1", "job1"));
        testRepository.insert(makeCrawlJob("au2", "job2"));
        CrawlJob crawlJob = makeCrawlJob("au3", "job3");
        testRepository.insert(crawlJob);
        CrawlJob job = pluggableCrawlManager.getCrawlJob("job3");
        Assertions.assertNull(job.getEndDate());
        crawlJob.endDate(TimeBase.nowMs());
        pluggableCrawlManager.updateCrawlJob(crawlJob);
        // check job1 and job2 have not changed
        job = pluggableCrawlManager.getCrawlJob("job3");
        Assertions.assertNotNull(job.getEndDate());
    }
    /*
     @Test
     @DisplayName("Should call shutdown when abortcrawling is true")
     void disableWhenAbortCrawlingIsTrueThenCallShutdown() {
         PluggableCrawler pluggableCrawler = mock(PluggableCrawler.class);
         pluggableCrawler.disable(true);
         verify(pluggableCrawler, times(1)).shutdown();
     }

     @Test
     @DisplayName("Should not call shutdown when abortcrawling is false")
     void disableWhenAbortCrawlingIsFalseThenNotCallShutdown() {
         PluggableCrawler pluggableCrawler = mock(PluggableCrawler.class);
         pluggableCrawler.disable(false);
         verify(pluggableCrawler, never()).shutdown();
     }


     @Test
     void restartCrawls() {
     }

     @Test
     void deleteAllCrawls() {
     }

     @Test
     void getCrawlerConfig() {
     }
 */
    private CrawlJob makeCrawlJob(String auId, String jobId)
    {
        CrawlDesc cd = new CrawlDesc()
          .auId(auId)
          .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT)
          .crawlerId("crawlerId")
          .crawlDepth(1);
        return new CrawlJob()
          .jobId(jobId)
          .requestDate(TimeBase.nowMs())
          .crawlDesc(cd);
    }

}