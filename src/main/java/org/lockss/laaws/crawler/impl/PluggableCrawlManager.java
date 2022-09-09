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
import org.dizitart.no2.objects.Cursor;
import org.dizitart.no2.objects.ObjectRepository;
import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.impl.pluggable.PluggableCrawler;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.ClassUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dizitart.no2.objects.filters.ObjectFilters.eq;
import static org.lockss.util.rest.crawler.CrawlDesc.LOCKSS_CRAWLER_ID;

/**
 * The type Pluggable crawl manager.
 */
public class PluggableCrawlManager extends BaseLockssDaemonManager implements ConfigurableManager {
  /**
   * The constant PREFIX.
   */
  public static final String PREFIX = Configuration.PREFIX + "crawlerservice.";

  /**
   * The constant PARAM_CRAWL_DB_PATH.
   */
  public static final String PARAM_CRAWL_DB_PATH = PREFIX + "dbPath";
  /**
   * The constant DEFAULT_CRAWL_DB_PATH.
   */
  public static final String DEFAULT_CRAWL_DB_PATH = "/data/db";

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
  public static final List<String> defaultCrawlerIds = ListUtil.list(LOCKSS_CRAWLER_ID);


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
  public static final String STARTER_ENABLED = "starterEnabled";

  /**
   * The constant ENABLED.
   */
  public static final String ENABLED = "Enabled";
  /**
   * The constant CRAWLER_ENABLED.
   */
  private static final L4JLogger log = L4JLogger.getLogger();
  private static final String CRAWLER = "crawlerClass";
  private final Map<String, PluggableCrawler> pluggableCrawlers = new HashMap<>();
  /**
   * The Pluggable crawls.
   */
  ObjectRepository<CrawlJob> pluggableCrawls;
  private List<String> crawlerIds = defaultCrawlerIds;
  private Map<String, CrawlerConfig> crawlerConfigMap;
  private boolean crawlerEnabled;
  private boolean crawlStarterEnabled;
  private String crawlQueuePath;
  private Nitrite crawlServiceDb;

  private CrawlManagerImpl lockssCrawlMgr;

  private ConfigManager lockssConfigMgr;

  public void startService() {
    super.startService();
    lockssConfigMgr = getDaemon().getConfigManager();
    File dbDir = lockssConfigMgr.findConfiguredDataDir(PARAM_CRAWL_DB_PATH,
      DEFAULT_CRAWL_DB_PATH);
    crawlQueuePath = new File(dbDir, DB_FILENAME).getAbsolutePath();
    //find configured data dir);
    try {
      crawlServiceDb = Nitrite.builder()
        .registerModule(new Jdk8Module()) // add jackson support
        .registerModule(new JavaTimeModule())
        .filePath(crawlQueuePath)
        .openOrCreate();
      // create a repo for crawls with and index on key
      pluggableCrawls = crawlServiceDb.getRepository(CrawlJob.class);
      // create an index on 'jobId' and an index on 'auId'
      pluggableCrawls.createIndex("jobId", IndexOptions.indexOptions(IndexType.Unique));
      pluggableCrawls.createIndex("crawlDesc.auId", IndexOptions.indexOptions(IndexType.NonUnique));
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void stopService() {
    shuttingDown = true;
    for (PluggableCrawler crawler : pluggableCrawlers.values()) {
      crawler.shutdown();
    }
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
    if (changedKeys.contains(PREFIX)) {
      crawlerIds = newConfig.getList(CRAWLER_IDS, defaultCrawlerIds);
      crawlerEnabled =
        newConfig.getBoolean(CrawlManagerImpl.PARAM_CRAWLER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWLER_ENABLED);
      crawlStarterEnabled =
        newConfig.getBoolean(CrawlManagerImpl.PARAM_CRAWL_STARTER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWL_STARTER_ENABLED);
      crawlerConfigMap = updateConfigMap(newConfig);
    }
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
   * Is eligible for crawl boolean.
   *
   * @param auId the au id
   * @return the boolean
   */
  public boolean isEligibleForCrawl(String auId) {
    ArchivalUnit au = getDaemon().getPluginManager().getAuFromId(auId);
    if (au != null) {
      try {
        getLockssCrawlManager().checkEligibleToQueueNewContentCrawl(au);
        return true;
      }
      catch (CrawlManagerImpl.NotEligibleException e) {
        return false;
      }
    }
    // We are a pluggable crawl so check each crawler
    Cursor<CrawlJob> jobCursor = getCrawlJobsWithAuId(auId);
    for (CrawlJob crawlJob : jobCursor) {
      if (crawlJob.getEndDate() != null) {
        return true;
      }
    }
    return false;
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
   * @return boolean boolean
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
    if (!cursor.idSet().isEmpty()) {
      throw new IllegalStateException("Attempt to add jobId " + jobId + "failed. It already exists in queue.");
    }
    pluggableCrawls.insert(crawlJob);
    crawlServiceDb.commit();
  }

  /**
   * Insert crawl Job into persistent store.
   * throws if crawl job already exists.
   *
   * @param crawlJob the crawl job.
   */
  public void updateCrawlJob(CrawlJob crawlJob) {
    String jobId = crawlJob.getJobId();
    Cursor<CrawlJob> cursor = pluggableCrawls.find(eq("jobId", jobId));
    // remove any old CrawlStatus because we only allow one
    if (cursor.idSet().isEmpty()) {
      throw new IllegalStateException("Update to jobId " + jobId + " No such job exists.");
    }
    pluggableCrawls.update(crawlJob);
    crawlServiceDb.commit();
  }

  /**
   * restart unfinished crawls.
   */
  public void restartCrawls() {
    Cursor<CrawlJob> cursor = pluggableCrawls.find();
    for (CrawlJob job : cursor) {
      // if the job never ended - we need to send it back to the crawler.
      if (job.getEndDate() == null) {
        CrawlDesc desc = job.getCrawlDesc();
        PluggableCrawler crawler = pluggableCrawlers.get(desc.getCrawlerId());
        if (crawler != null && crawler.isCrawlerEnabled()) {
          crawler.requestCrawl(job, null);
        }
      }
    }
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
    String crawlerClassName = config.getAttributes().get(CRAWLER);
    if (crawlerClassName == null) {return null;}
    try {
      log.debug2("Instantiating pluggable crawler class " + crawlerClassName);
      final Class<?> crawlerClass = Class.forName(crawlerClassName);
      crawler = (PluggableCrawler) ClassUtil.instantiate(crawlerClassName, crawlerClass);
      crawler.updateCrawlerConfig(config);
      pluggableCrawlers.put(crawlerId, crawler);
      return crawler;
    }
    catch (Exception ex) {
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
      // add the long name of the crawler if given.
      String val = config.get(crawlerConfigRoot + ATTR_CRAWLER_NAME, crawlerId);
      attrMap.put(ATTR_CRAWLER_NAME, val);
      boolean isEnabled = config.getBoolean(crawlerConfigRoot + "enabled", true);
      attrMap.put(ENABLED, String.valueOf(isEnabled));
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
      PluggableCrawler crawler = getCrawler(crawlerId);
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

}
