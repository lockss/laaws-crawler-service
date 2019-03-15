/*
 * Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lockss.app.LockssApp;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlManagerStatus;
import org.lockss.laaws.crawler.api.CrawlersApi;
import org.lockss.laaws.crawler.api.CrawlersApiDelegate;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.model.CrawlerStatus;
import org.lockss.laaws.crawler.model.InlineResponse200;
import org.lockss.log.L4JLogger;
import org.lockss.util.ListUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class CrawlersApiImpl implements CrawlersApiDelegate {

  private static final L4JLogger log = L4JLogger.getLogger();
  /**
   * The list of known crawlers.
   */
  public static List<String> CRAWLERS = ListUtil.list("lockss");
  private CrawlManagerImpl crawlManager;

  private static Map<String, CrawlerConfig> configMap;

  /**
   * Return the list of configured crawlers.
   *
   * @see CrawlersApi#getCrawlers
   */
  @Override
  public ResponseEntity<InlineResponse200> getCrawlers() {
    CrawlManagerImpl cmi = getCrawlManager();
    if (cmi == null) {
      return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    InlineResponse200 response = new InlineResponse200();
    Map<String, CrawlerStatus> crawlers = new HashMap<>();
    // we only have one for now so no need to iterate.
    CrawlerStatus status = new CrawlerStatus().isEnabled(cmi.isCrawlerEnabled())
        .isRunning(cmi.isCrawlStarterRunning());
    crawlers.put("lockss", status);
    response.setCrawlers(crawlers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * @see CrawlersApi#getCrawlerConfig
   */
  @Override
  public ResponseEntity<CrawlerConfig> getCrawlerConfig(String crawler) {
    CrawlManagerImpl cmi = getCrawlManager();
    if (cmi == null) {
      return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    CrawlerConfig config = getConfigMap().get(crawler);
    if (config == null) {
      return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    }
    return new ResponseEntity<>(config, HttpStatus.OK);
  }

  private CrawlManagerImpl getCrawlManager() {
    if (crawlManager == null) {
      CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
      if (cmgr instanceof CrawlManagerImpl) {
        crawlManager = (CrawlManagerImpl) cmgr;
      }
    }
    return crawlManager;
  }

  private Map<String, CrawlerConfig> getConfigMap() {
    if (configMap == null) {
      configMap = new HashMap<String, CrawlerConfig>();
      for (String crawler : CRAWLERS) {
        // TODO: add useful subset of the config params here.

        CrawlerConfig config = new CrawlerConfig();
        HashMap<String,String> crawlerMap = new HashMap<>();
        config.setConfigMap(crawlerMap);
        crawlerMap.put("crawlerEnabled",String.valueOf(crawlManager.isCrawlerEnabled()));
        crawlerMap.put("starterEnabled",String.valueOf(crawlManager.isCrawlStarterEnabled()));
        //todo: add more later.
        configMap.put(crawler, config);
      }
    }
    return configMap;
  }
}