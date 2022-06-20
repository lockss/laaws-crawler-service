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

import static org.lockss.util.rest.crawler.CrawlDesc.LOCKSS_CRAWLER_ID;
import static org.lockss.util.rest.crawler.CrawlDesc.WGET_CRAWLER_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lockss.app.LockssApp;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.api.CrawlersApi;
import org.lockss.laaws.crawler.api.CrawlersApiDelegate;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.model.CrawlerStatus;
import org.lockss.laaws.crawler.model.CrawlerStatuses;
import org.lockss.log.L4JLogger;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.spring.base.LockssConfigurableService;
import org.lockss.util.ListUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service for accessing crawlers.
 */
@Service
public class CrawlersApiServiceImpl extends BaseSpringApiServiceImpl
    implements CrawlersApiDelegate , LockssConfigurableService {

  private static final L4JLogger log = L4JLogger.getLogger();
  public static final String PREFIX = Configuration.PREFIX + "crawler.";
  public static final String CRAWLER_IDS = PREFIX + "crawlers";

  /**
   * The default list of known crawlers.
   */
  public static final List<String> defaultCrawlerIds =
      ListUtil.list(LOCKSS_CRAWLER_ID, WGET_CRAWLER_ID);
  // ------------------
  // Attribute Map Keys
  // ------------------
  public static final String CRAWLER_ID = "crawlerId";
  public static final String CRAWLER_NAME = "crawlerName";
  public static final String CRAWLING_ENABLED = "crawlingEnabled";
  public static final String CRAWL_STARTER_ENABLED = "starterEnabled";
  public static final String ENABLED= "Enabled";

  public static final String CAN_CRAWL= "canCrawl";

  public static final String CRAWLER_ENABLED = CrawlManagerImpl.PARAM_CRAWLER_ENABLED;
  public static final String STARTER_ENABLED = CrawlManagerImpl.PARAM_CRAWL_STARTER_ENABLED;
  private static final String CRAWLER = "crawler";

  private List<String> crawlerIds = defaultCrawlerIds;

  private Map<String, CrawlerConfig> crawlerConfigMap;
  private boolean crawlerEnabled;
  private boolean crawlStarterEnabled;


  @Override
  public void setConfig(Configuration newConfig, Configuration prevConfig,
      Differences changedKeys) {
    if (changedKeys.contains(PREFIX)) {
      crawlerIds = newConfig.getList(CRAWLER_IDS, defaultCrawlerIds);
      crawlerEnabled =
          newConfig.getBoolean(CRAWLER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWLER_ENABLED);
      crawlStarterEnabled =
          newConfig.getBoolean(STARTER_ENABLED, CrawlManagerImpl.DEFAULT_CRAWL_STARTER_ENABLED);
      crawlerConfigMap = updateConfigMap(newConfig);
    }
  }

  public List<String> getCrawlerIds() {
    return crawlerIds;
  }

  public Map<String, CrawlerConfig> getCrawlerConfigMap() {
    return crawlerConfigMap;
  }

  public boolean isCrawlerEnabled() {return crawlerEnabled;}

  public boolean isCrawlStarterEnabled() {return crawlStarterEnabled;}


  /**
   * Return the list of configured crawlers.
   *
   * @see CrawlersApi#getCrawlers
   */
  @Override
  public ResponseEntity<CrawlerStatuses> getCrawlers() {
    log.debug2("Invoked");

    // Check whether the service has not been fully initialized.
    if (!waitReady()) {
      // Yes: Notify the client.
      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    }

    CrawlerStatuses response = new CrawlerStatuses();
    CrawlerStatus status = new CrawlerStatus().isEnabled(crawlerEnabled)
        .isRunning(crawlStarterEnabled);

    for (String crawlerId : crawlerIds) {
      response.putCrawlerMapItem(crawlerId, status);
    }

    log.debug2("response = {}", response);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * @see CrawlersApi#getCrawlerConfig
   */
  @Override
  public ResponseEntity<CrawlerConfig> getCrawlerConfig(String crawler) {
    log.debug2("crawler = {}", crawler);

    // Check whether the service has not been fully initialized.
    if (!waitReady()) {
      // Yes: Notify the client.
      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    }

    CrawlManagerImpl cmi = getCrawlManager();
    log.trace("cmi = {}", cmi);

    if (cmi == null) {
      return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    CrawlerConfig config = crawlerConfigMap.get(crawler);
    log.trace("config = {}", config);

    if (config == null) {
      log.debug2("NOT_FOUND");
      return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    }

    log.debug2("OK");
    return new ResponseEntity<>(config, HttpStatus.OK);
  }


  /**
   * Provides the crawl manager.
   * 
   * @return a CrawlManagerImpl with the crawl manager implementation.
   */
  private CrawlManagerImpl getCrawlManager() {
    CrawlManagerImpl crawlManager = null;
    CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
 
    if (cmgr instanceof CrawlManagerImpl) {
      crawlManager = (CrawlManagerImpl) cmgr;
    }
    return crawlManager;
  }

  /**
   * Provides the map of crawler configurations.
   * 
   * @return a Map<String, CrawlerConfig> with the map of crawler
   *         configurations.
   */
  private Map<String, CrawlerConfig> updateConfigMap(Configuration config) {
    Map<String, CrawlerConfig> configMap = new HashMap<>();
    // get the crawler id from the config
    for (String crawlerId : crawlerIds) {
      // load the configuration for each supported crawler
      log.trace("crawlerId = {}", crawlerId);
      CrawlerConfig crawlerConfig = new CrawlerConfig();
      HashMap<String,String> crawlerMap = new HashMap<>();
      String crawlerConfigRoot = PREFIX + crawlerId+ ".";
      // add the short id for the crawler.
      crawlerMap.put(CRAWLER_ID, crawlerId);
      // add the long name of the crawler if given.
      String val = config.get(crawlerConfigRoot+ CRAWLER_NAME,crawlerId);
      crawlerMap.put(CRAWLER_NAME,val);
      // add the settings for crawler
      crawlerMap.put(CRAWLING_ENABLED, String.valueOf(crawlerEnabled));
      crawlerMap.put(CRAWL_STARTER_ENABLED, String.valueOf(crawlStarterEnabled));
      boolean isEnabled = config.getBoolean(crawlerConfigRoot+ CRAWLER_ENABLED, true);
      crawlerMap.put(crawlerId+ENABLED, String.valueOf(isEnabled));
      crawlerMap.put(CAN_CRAWL, String.valueOf(isEnabled && crawlerEnabled));
      String crawler = config.get(crawlerConfigRoot + CRAWLER);
      if(crawler != null) {
        crawlerMap.put(CRAWLER, crawler);
      }
      //todo: add more later.

      // update our crawler config and add it to the map
      crawlerConfig.setCrawlerId(crawlerId);
      crawlerConfig.setAttributes(crawlerMap);
      configMap.put(crawlerId, crawlerConfig);
    }
    return configMap;
  }

}
