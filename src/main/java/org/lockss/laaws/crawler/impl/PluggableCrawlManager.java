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

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.dizitart.no2.IndexOptions;
import org.dizitart.no2.IndexType;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.WriteResult;
import org.dizitart.no2.objects.Cursor;
import org.dizitart.no2.objects.ObjectRepository;
import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.crawler.*;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawl;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.base.BaseArchivalUnit;
import org.lockss.util.ClassUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.dizitart.no2.objects.filters.ObjectFilters.eq;
import static org.lockss.util.rest.crawler.CrawlDesc.CLASSIC_CRAWLER_ID;

/**
 * The type Pluggable crawl manager.
 */
public class PluggableCrawlManager extends BaseLockssDaemonManager implements ConfigurableManager {
  /**
   * The constant PREFIX.
   */
  public static final String PREFIX = Configuration.PREFIX + "crawlerservice.";

  public static final String CRAWLER_PREFIX = CrawlManagerImpl.PREFIX;

  /**
   * The constant PARAM_CRAWL_DB_PATH.
   */
  public static final String PARAM_CRAWL_DB_PATH = PREFIX + "dbPath";
  /**
   * The constant DEFAULT_CRAWL_DB_PATH.
   */
  public static final String DEFAULT_CRAWL_DB_PATH = "data/db";

  public static final String PARAM_REQUEUE_ON_RESTART = PREFIX + "requeueOnRestart";

  public static boolean DEFAULT_REQUEUE_ON_RESTART = false;
  /**
   * The constant DB_FILENAME.
   */
  public static final String DB_FILENAME = "crawlerServiceDb";


  /**
   * The constant CRAWLER_IDS.
   */
  public static final String CRAWLER_IDS = PREFIX + "crawlers";
  /**
   * The default list of known crawlers.
   */
  public static final List<String> defaultCrawlerIds = ListUtil.list(CLASSIC_CRAWLER_ID);


// ------------------
  // Attribute Map Keys
  // ------------------
  /**
   * The constant CRAWLER_ID.
   */
  public static final String ATTR_CRAWLER_ID = "crawlerId";
  /**
   * The constant CRAWLER_NAME.
   */
  public static final String ATTR_CRAWLER_NAME = "crawlerName";

  /**
   * The constant CRAWLING_ENABLED.
   */
  public static final String ATTR_CRAWLING_ENABLED = "crawlingEnabled";
  /**
   * The constant CRAWL_STARTER_ENABLED.
   */
  public static final String ATTR_STARTER_ENABLED = "starterEnabled";

  /**
   * The constant ENABLED.
   */
  public static final String ENABLED = "Enabled";
  /**
   * The constant CRAWLER_ENABLED.
   */
  private static final L4JLogger log = L4JLogger.getLogger();
  private static final String CRAWLER = "crawler";
  private final Map<String, PluggableCrawler> pluggableCrawlers = new HashMap<>();
  /**
   * The Pluggable crawls.
   */
  ObjectRepository<CrawlJob> pluggableCrawls;
  private List<String> crawlerIds = defaultCrawlerIds;
  private Map<String, CrawlerConfig> crawlerConfigMap = new HashMap<>();
  private boolean crawlerEnabled;
  private boolean crawlStarterEnabled;
  private Nitrite crawlServiceDb;

  private CrawlManagerImpl lockssCrawlMgr;
  private CrawlEventHandler crawlEventHandler;
  private PluginManager lockssPluginMgr;
  private int maxRetries;
  private long retryDelay;
  private long connectTimeout;
  private long readTimeout;
  private long fetchDelay;
  private boolean starting;
  List<CrawlJob> interruptedCrawls = new ArrayList<>();
  private boolean requeueOnStart;


  public void startService() {
    super.startService();
    //find configured data dir);
    File dbDir = getDaemon().getConfigManager().findConfiguredDataDir(PARAM_CRAWL_DB_PATH,
            DEFAULT_CRAWL_DB_PATH);
    // register for crawlevent callbacks
    crawlEventHandler = new CrawlEventHandler.Base() {
      @Override
      protected void handleNewContentCompleted(CrawlEvent event) {
        handleCrawlComplete(event);
      }

      @Override
      protected void handleRepairCompleted(CrawlEvent event) {
        handleCrawlComplete(event);
      }
    };
    getLockssCrawlManager().registerCrawlEventHandler(crawlEventHandler);

    // initialize the database
    try {
      initDb(new File(dbDir, DB_FILENAME));
      log.info("crawl manager db inited! Checking for interrupted crawls.");
      Cursor<CrawlJob> cursor = pluggableCrawls.find();
      for (CrawlJob job : cursor) {
        JobStatus js = job.getJobStatus();
        // if the job never ended - we need to send it back to the crawler.
        if (js.getStatusCode() == JobStatus.StatusCodeEnum.QUEUED ||
            js.getStatusCode() == JobStatus.StatusCodeEnum.ACTIVE) {
          interruptedCrawls.add(job);
        }
      }
      if(requeueOnStart) {
        log.info("Requeueing crawls from previous session.");
        restartCrawls();
      }
      else {
        //mark any crawls that would be requeued as INTERRUPTED.
        log.info("Setting pending and running crawls to INTERRUPTED ");
        markInterruptedCrawls();
      }
      interruptedCrawls.clear();
   } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  public void stopService() {
    shuttingDown = true;
    // call shutdown on all pluggable crawlers.
    for (PluggableCrawler crawler : pluggableCrawlers.values()) {
      crawler.shutdown();
    }
    // commit any unsaved changes and close the database.
    if (!crawlServiceDb.isClosed()) {
      if (crawlServiceDb.hasUnsavedChanges()) {
        crawlServiceDb.commit();
      }
      crawlServiceDb.close();
    }
    super.stopService();
  }


  @Override
  public void setConfig(Configuration newConfig, Configuration prevConfig,
                        Differences changedKeys) {
    if (changedKeys.contains(CRAWLER_PREFIX)) {
      crawlerEnabled =
        newConfig.getBoolean(CrawlManagerImpl.PARAM_CRAWLER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWLER_ENABLED);
      crawlStarterEnabled =
        newConfig.getBoolean(CrawlManagerImpl.PARAM_CRAWL_STARTER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWL_STARTER_ENABLED);
      maxRetries = newConfig.getInt(BaseCrawler.PARAM_MAX_RETRY_COUNT,
          BaseCrawler.DEFAULT_MAX_RETRY_COUNT);
      retryDelay = newConfig.getLong(BaseCrawler.PARAM_DEFAULT_RETRY_DELAY,
          BaseCrawler.DEFAULT_DEFAULT_RETRY_DELAY);
      connectTimeout = newConfig.getTimeInterval(BaseCrawler.PARAM_CONNECT_TIMEOUT,
          BaseCrawler.DEFAULT_CONNECT_TIMEOUT);
      readTimeout = newConfig.getTimeInterval(BaseCrawler.PARAM_DATA_TIMEOUT,
          BaseCrawler.DEFAULT_DATA_TIMEOUT);
    }
    fetchDelay = newConfig.getTimeInterval(BaseArchivalUnit.PARAM_MIN_FETCH_DELAY,
        BaseArchivalUnit.DEFAULT_FETCH_DELAY);
    log.info("setConfig: crawlerEnabled:{} starterEnabled:{}",crawlerEnabled,crawlStarterEnabled);
    if (changedKeys.contains(PREFIX)) {
      crawlerIds = newConfig.getList(CRAWLER_IDS, defaultCrawlerIds);
      log.info("setting config: {}", newConfig.toStringMap());
      ListUtil.immutableListOfType(newConfig.getList(CRAWLER_IDS, defaultCrawlerIds), String.class);
      crawlerIds = ListUtil.immutableListOfType(newConfig.getList(CRAWLER_IDS, defaultCrawlerIds), String.class);
      // If we remove a crawler, we complete any active crawls and disable the crawler
       List<String> removedCrawlers = pluggableCrawlers.keySet().stream()
               .filter(key-> !crawlerIds.contains(key))
               .collect(Collectors.toList());
       // removed crawlers should be marked as disabled but crawls are allowed to complete
      for (String key : removedCrawlers) {
        pluggableCrawlers.get(key).disable(false);
      }
      crawlerConfigMap = updateConfigMap(newConfig);
      requeueOnStart = newConfig.getBoolean(PARAM_REQUEUE_ON_RESTART,DEFAULT_REQUEUE_ON_RESTART);

    }
  }
  public int getMaxRetries() {
    return maxRetries;
  }

  public long getRetryDelay() {
    return retryDelay;
  }

  public long getConnectTimeout() {
    return connectTimeout;
  }

  public long getReadTimeout() {
    return readTimeout;
  }

  public long getFetchDelay() {
    return fetchDelay;
  }

  /**
   * Gets crawler ids.
   *
   * @return the crawler ids
   */
  public List<String> getCrawlerIds() {
    return crawlerIds;
  }

  /**
   * Is eligible Au elgible for crawl boolean.
   *
   * @param auId the au id
   * @return the boolean
   */
  public boolean isEligibleForCrawl(String auId) {

    // We are a pluggable crawl so check each crawler
    Cursor<CrawlJob> jobCursor = getCrawlJobsWithAuId(auId);
    if(jobCursor.totalCount()==0) {
      return true;
    }
    for (CrawlJob crawlJob : jobCursor) {
      log.debug("Checking job {}", crawlJob);
      if(crawlJob.getJobStatus() != null) {
        JobStatus status = crawlJob.getJobStatus();
        JobStatus.StatusCodeEnum statusCode = status.getStatusCode();
        if(statusCode == JobStatus.StatusCodeEnum.ACTIVE ||
          statusCode == JobStatus.StatusCodeEnum.QUEUED) {
          return false;
        }
      } else {
        log.debug("CrawlJob {} misssing a jobStatus", crawlJob);
      }
    }
    return true;
  }

  /**
   * is crawling enabled
   *
   * @return true iff crawler (global is enabled)
   */
  public boolean isCrawlerEnabled() {
    return crawlerEnabled;
  }

  /**
   * is the crawl starter enabled
   *
   * @return boolean are crawls allowed to start
   */
  public boolean isCrawlStarterEnabled() {
    return crawlStarterEnabled;
  }

  /**
   * Gets crawl status.
   *
   * @param jobId the id
   * @return the crawl status
   */
  public CrawlJob getCrawlJob(String jobId) {
    Cursor<CrawlJob> cursor = pluggableCrawls.find(eq("jobId", jobId));
    return cursor.firstOrDefault();
  }

  /**
   * Gets crawl jobs with au id.
   *
   * @param auId the au id
   * @return the crawl jobs with au id
   */
  public Cursor<CrawlJob> getCrawlJobsWithAuId(String auId) {

    return pluggableCrawls.find(eq("crawlDesc.auId", auId));
  }

  /**
   * Insert crawl Job into persistent store.
   * throws if crawl job already exists.
   *
   * @param crawlJob the crawl job.
   */
  public void addCrawlJob(CrawlJob crawlJob) {
    String jobId = crawlJob.getJobId();
    Cursor<CrawlJob> cursor = pluggableCrawls.find(eq("jobId", jobId));
    if (cursor.size() > 0) {
      throw new IllegalStateException("Attempt to add jobId " + jobId + "failed. It already exists in queue.");
    }
    pluggableCrawls.insert(crawlJob);
    crawlServiceDb.commit();
  }

  /**
   * Update a  crawl Jobs status.
   * throws if crawl job does not exist.
   *
   * @param crawlJob the crawl job.
   */
  public void updateCrawlJob(CrawlJob crawlJob) {
    String jobId = crawlJob.getJobId();
    Cursor<CrawlJob> cursor = pluggableCrawls.find(eq("jobId", jobId));
    // If we don't have a matching object for jobId throw.
    if (cursor.size() <= 0) {
      throw new IllegalStateException("Update to jobId " + jobId + " No such job exists.");
    }
    WriteResult result = pluggableCrawls.update((eq("jobId", jobId)),crawlJob);
    if(result.getAffectedCount() <= 0) {
      log.error("Attempt to update db for with crawljob {} failed",jobId);
    }
    crawlServiceDb.commit();
  }

  /**
   * restart unfinished crawls.
   */
  public void restartCrawls() {
    for (CrawlJob job : interruptedCrawls) {
      CrawlDesc desc = job.getCrawlDesc();
      PluggableCrawler crawler = pluggableCrawlers.get(desc.getCrawlerId());
      if (crawler != null && crawler.isCrawlerEnabled()) {
        ArchivalUnit au = getLockssPluginMgr().getAuFromId(desc.getAuId());
        PluggableCrawl crawl = crawler.requestCrawl(au,job);
      }
    }
  }
  public void markInterruptedCrawls() {
    for (CrawlJob job : interruptedCrawls) {
      JobStatus js = job.getJobStatus();
        js.statusCode(JobStatus.StatusCodeEnum.INTERRUPTED).msg("Interrupted by Service Exit.");
        pluggableCrawls.update((eq("jobId", job.getJobId())),job);
    }
    crawlServiceDb.commit();
  }
  /**
   * Delete all crawls.
   */
  public void deleteAllCrawls() {
    for (PluggableCrawler crawler : pluggableCrawlers.values()) {
      crawler.deleteAllCrawls();
    }
  }

  /**
   * Gets crawler config.
   *
   * @param crawlerId the crawler id
   * @return the crawler config
   */
  public CrawlerConfig getCrawlerConfig(String crawlerId) {
    return crawlerConfigMap.get(crawlerId);
  }

  /**
   * Gets crawler.
   *
   * @param crawlerId the crawler id
   * @return the crawler
   */
  public PluggableCrawler getCrawler(String crawlerId) {
    PluggableCrawler crawler = pluggableCrawlers.get(crawlerId);
    if (crawler != null) {
      return crawler;
    }
    CrawlerConfig config = getCrawlerConfig(crawlerId);
    if (config == null) return null;
    String crawlerClassName = config.getAttributes().get(CRAWLER);
    if (crawlerClassName == null) {
      log.error("Crawler does not have classname");
      return null;
    }
    try {
      log.debug2("Instantiating pluggable crawler class " + crawlerClassName);
      final Class<?> crawlerClass = Class.forName(crawlerClassName);
      crawler = (PluggableCrawler) ClassUtil.instantiate(crawlerClassName, crawlerClass);
      crawler.updateCrawlerConfig(config);
      crawler.setPluggableCrawlManager(this);
      pluggableCrawlers.put(crawlerId, crawler);
      return crawler;
    } catch (Exception ex) {
      log.error("Unable to instantiate Pluggable Crawler: {}", crawlerClassName);
    }
    return null;
  }

  /**
   * Is a specific crawler enabled
   *
   * @param crawlerId the id of the crawler
   * @return true if the crawler is found in the configuration map and is marked enabled.
   */
  public boolean isCrawlerEnabled(String crawlerId) {
    CrawlerConfig config = crawlerConfigMap.get(crawlerId);
    if (config != null) {
      Map<String, String> attrs = config.getAttributes();
      return Boolean.parseBoolean(attrs.get(crawlerId + ENABLED));
    }
    return false;
  }

  /**
   * handle the complete crawl.
   * @param event the crawl complete event.
   */
  public void handleCrawlComplete(CrawlEvent event) {
    String key = event.getCrawlerId();
    CrawlJob job = getCrawlJob(key);
    if(job != null) {
      CrawlerStatus status = ApiUtils.getCrawlerStatus(key);
      JobsApiServiceImpl.updateCrawlJob(job, status);
      updateCrawlJob(job);
    }
  }

  /**
   * handle the complete crawl.
   * @param status the status of the completed crawl.
   */
  public void handleCrawlComplete(CrawlerStatus status) {
    String key = status.getKey();
    CrawlJob job = getCrawlJob(key);
    if(job != null) {
      JobsApiServiceImpl.updateCrawlJob(job,status);
      updateCrawlJob(job);
    }
  }

  /**
   * Provides the map of crawler configurations.
   *
   * @return a Map<String, CrawlerConfig> with the map of crawler configurations.
   */
  private Map<String, CrawlerConfig> updateConfigMap(Configuration config) {
    Map<String, CrawlerConfig> configMap = new HashMap<>();
    // get the crawler id from the config
    for (String crawlerId : crawlerIds) {
      log.trace("crawlerId = {}", crawlerId);
      CrawlerConfig crawlerConfig = new CrawlerConfig();
      // read from the properties the configuration for each crawler
      String crawlerConfigRoot = PREFIX + crawlerId + ".";
      HashMap<String, String> attrMap = new HashMap<>();
      // add the short id for the crawler.
      attrMap.put(ATTR_CRAWLER_ID, crawlerId);
      attrMap.put(ATTR_STARTER_ENABLED, String.valueOf(crawlStarterEnabled));
      attrMap.put(ATTR_CRAWLING_ENABLED, String.valueOf(crawlerEnabled));
      // add the long name of the crawler if given.
      String val = config.get(crawlerConfigRoot + ATTR_CRAWLER_NAME, crawlerId);
      attrMap.put(ATTR_CRAWLER_NAME, val);
      boolean isEnabled = config.getBoolean(crawlerConfigRoot + "enabled", true);
      attrMap.put(crawlerId+ENABLED, String.valueOf(isEnabled));
      Configuration crawlerTree = config.getConfigTree(crawlerConfigRoot);
      if (!crawlerTree.isEmpty()) {
        attrMap.putAll(crawlerTree.toStringMap());
        String crawlerDefClass = config.get(crawlerConfigRoot + CRAWLER);
        if (crawlerDefClass != null) {
          attrMap.put(CRAWLER, crawlerDefClass);
        }
      }
      // update our crawler config and add it to the map
      crawlerConfig.setCrawlerId(crawlerId);
      crawlerConfig.setAttributes(attrMap);
      configMap.put(crawlerId, crawlerConfig);
      // update the crawlers with the most new config
      PluggableCrawler crawler = pluggableCrawlers.get(crawlerId);
      if (crawler != null) {
        crawler.updateCrawlerConfig(crawlerConfig);
      }
     }
    return configMap;
  }

  private CrawlManagerImpl getLockssCrawlManager() {
    if (lockssCrawlMgr == null) {
      CrawlManager cmgr = getDaemon().getCrawlManager();
      if (cmgr instanceof CrawlManagerImpl) {
        lockssCrawlMgr = (CrawlManagerImpl) cmgr;
      }
    }
    return lockssCrawlMgr;
  }

  private PluginManager getLockssPluginMgr() {
    if(lockssPluginMgr == null) {
      lockssPluginMgr = getDaemon().getPluginManager();
    }
    return lockssPluginMgr;
  }

  void initDb(File dbDir) {
    crawlServiceDb = Nitrite.builder()
      .registerModule(new Jdk8Module()) // add jackson support
      .registerModule(new JavaTimeModule())
      .filePath(dbDir)
      .openOrCreate();
    // create a repo for crawls with and index on key
    pluggableCrawls = crawlServiceDb.getRepository(CrawlJob.class);
    // create an index on 'jobId' and an index on 'auId'
    if(!pluggableCrawls.hasIndex("jobId"))
      pluggableCrawls.createIndex("jobId", IndexOptions.indexOptions(IndexType.Unique));
    if(!pluggableCrawls.hasIndex("crawlDesc.auId"))
      pluggableCrawls.createIndex("crawlDesc.auId", IndexOptions.indexOptions(IndexType.NonUnique));
  }

  Nitrite getCrawlServiceDb() {return crawlServiceDb;}
  ObjectRepository<CrawlJob> getPluggableCrawls() {return pluggableCrawls;}

}
