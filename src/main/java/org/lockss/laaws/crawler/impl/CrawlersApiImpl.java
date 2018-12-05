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

import org.lockss.app.LockssApp;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.api.CrawlersApi;
import org.lockss.laaws.crawler.api.CrawlersApiDelegate;
import org.lockss.laaws.crawler.model.CrawlerInfo;
import org.lockss.laaws.crawler.model.InlineResponse200;
import org.lockss.log.L4JLogger;
import org.lockss.util.ListUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

public class CrawlersApiImpl implements CrawlersApiDelegate {
  /**
   * The list of known crawlers.
   */
  public static List<String> crawlers = ListUtil.list("lockss");
  private static final L4JLogger log = L4JLogger.getLogger();
  private CrawlManagerImpl crawlManager;
  /**
   * Return the status and configuration information about a crawler
   *
   * @param crawler The name of the crawler
   * @see CrawlersApi#getCrawlerByName
   */
  @Override
  public ResponseEntity<CrawlerInfo> getCrawlerByName(String crawler) {
    CrawlManagerImpl cmi = getCrawlManager();
    CrawlerInfo status = new CrawlerInfo();
    if (!crawlers.contains(crawler)) {
      return new ResponseEntity<>(status, HttpStatus.NOT_FOUND);
    }
    if(cmi == null) {
      return new ResponseEntity<>(status, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    status.setIsEnabled(cmi.isCrawlerEnabled());
    status.setIsRunning(cmi.isCrawlStarterRunning());
    return new ResponseEntity<>(status, HttpStatus.OK);
  }


  /**
   * Return the list of configured crawlers.
   *
   * @see CrawlersApi#getCrawlers
   */
  @Override
  public ResponseEntity<InlineResponse200> getCrawlers() {
    InlineResponse200 response = new InlineResponse200();
    response.setCrawls(crawlers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private CrawlManagerImpl getCrawlManager() {
    if(crawlManager == null) {
      CrawlManager cmgr = LockssApp.getManagerByTypeStatic(CrawlManager.class);
      if (cmgr instanceof CrawlManagerImpl) {
        crawlManager = (CrawlManagerImpl) cmgr;
      }
    }
    return crawlManager;
  }


}
