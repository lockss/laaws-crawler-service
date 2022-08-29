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

import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.api.CrawlersApi;
import org.lockss.laaws.crawler.api.CrawlersApiDelegate;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.model.CrawlerStatus;
import org.lockss.laaws.crawler.model.CrawlerStatuses;
import org.lockss.log.L4JLogger;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for accessing crawlers.
 */
@Service
public class CrawlersApiServiceImpl extends BaseSpringApiServiceImpl implements CrawlersApiDelegate {
  /**
   * The default list of known crawlers.
   */
  private static final L4JLogger log = L4JLogger.getLogger();
  private PluggableCrawlManager pluggableCrawlManager;


  public List<String> getCrawlerIds() {
    return getPluggableCrawlManager().getCrawlerIds();
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
    CrawlerConfig config = pluggableCrawlManager.getCrawlerConfig(crawler);
    log.trace("config = {}", config);

    if (config == null) {
      log.debug2("NOT_FOUND");
      return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    }

    log.debug2("OK");
    return new ResponseEntity<>(config, HttpStatus.OK);
  }

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

    PluggableCrawlManager pcm = getPluggableCrawlManager();
    boolean crawlingEnabled = pcm.isCrawlerEnabled();
    boolean isRunning = pcm.isCrawlStarterEnabled();
    List<String> crawlerIds = pcm.getCrawlerIds();
    CrawlerStatuses crawlerStatuses = new CrawlerStatuses();
    for (String id : crawlerIds) {
      boolean isEnabled = pcm.isCrawlerEnabled(id);
      CrawlerStatus status = new CrawlerStatus().isEnabled(isEnabled).isAutoCrawlEnabled(Boolean.FALSE);
      crawlerStatuses.putCrawlerMapItem(id, status);
    }
    log.debug2("crawlerStatuses = {}", crawlerStatuses);
    return new ResponseEntity<>(crawlerStatuses, HttpStatus.OK);
  }

  /**
   * Provides the crawl manager.
   *
   * @return a CrawlManagerImpl with the crawl manager implementation.
   */
  private CrawlManagerImpl getCrawlManager() {
    CrawlManagerImpl crawlManager = null;
    CrawlManager cmgr = getRunningLockssDaemon().getCrawlManager();

    if (cmgr instanceof CrawlManagerImpl) {
      crawlManager = (CrawlManagerImpl) cmgr;
    }
    return crawlManager;
  }

  private PluggableCrawlManager getPluggableCrawlManager() {
    if (pluggableCrawlManager == null) {
      pluggableCrawlManager = getRunningLockssDaemon().getManagerByType(PluggableCrawlManager.class);
    }
    return pluggableCrawlManager;
  }

}
