/*

Copyright (c) 2000-2020 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software without
specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.lockss.laaws.crawler.wget;

import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;

/**
 * The wget crawler.
 */
public class WgetCrawler {
  private CrawlerStatus crawlStatus;
  private Crawler.Type type;

  /**
   * Provides the type of crawler.
   * 
   * @return a Crawler.Type with the type of crawler.
   */
  public Crawler.Type getType() {
    return type;
  }

  /**
   * Saves the type of crawler.
   * 
   * @param type A Crawler.Type with the type of crawler.
   */
  protected void setType(Crawler.Type type) {
    this.type = type;
  }

  /**
   * Provides the crawler status.
   * 
   * @return a CrawlerStatus with the crawler status.
   */
  public CrawlerStatus getCrawlerStatus() {
    return crawlStatus;
  }

  /**
   * Saves the crawler status.
   * 
   * @param crawlerStatus A CrawlerStatus with the crawler status.
   */
  protected void setCrawlerStatus(CrawlerStatus crawlerStatus) {
    crawlStatus = crawlerStatus;
  }
}
