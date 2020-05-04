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

import java.util.Collection;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.plugin.ArchivalUnit;

/**
 * Status of a wget crawler.
 */
public class WgetCrawlerStatus extends CrawlerStatus {
  /**
   * Constructor.
   * 
   * @param auId      A String with the Archival Unit identifier.
   * @param startUrls A Collection<String> with the crawl starting URLs.
   * @param type      A String with the crawl type.
   */
  public WgetCrawlerStatus(String auId, Collection<String> startUrls,
      String type) {
    this.auid = auId;
    this.startUrls = startUrls;
    this.type = type;
    key = nextIdx();
    auName = "WgetCrawl " + auId;
    initCounters();
  }

  /**
   * Provides the Archival Unit.
   * 
   * @return an ArchivalUnit with the Archival Unit.
   */
  @Override
  public ArchivalUnit getAu() {
    return null;
  }

  @Override
  public String toString() {
    return "[WgetCrawlerStatus " + key + "]";
  }
}
