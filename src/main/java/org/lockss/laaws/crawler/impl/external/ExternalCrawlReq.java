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
package org.lockss.laaws.crawler.impl.external;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.lockss.crawler.CrawlReq;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.wget.WgetCommandLineBuilder;
import org.lockss.util.io.FileUtil;
import org.lockss.util.rest.crawler.CrawlDesc;

/** An external crawl request. */
public class ExternalCrawlReq extends CrawlReq {
  private CrawlDesc crawlDesc = null;
  private File tmpDir = null;
  private List<String> command = null;

  /**
   * Constructor.
   *
   * @param crawlDesc A CrawlDesc with the crawl description.
   * @param crawlerStatus A ExteranalCrawlerStatus with the crawl status.
   * @throws IOException if there are problems
   */
  public ExternalCrawlReq(CrawlDesc crawlDesc, CrawlerStatus crawlerStatus) throws IOException {
    this.crawlDesc = crawlDesc;
    this.crawlerStatus = crawlerStatus;
    auid = crawlDesc.getAuId();
    tmpDir = FileUtil.createTempDir("laaws-external-crawler", "");
    command = new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);
  }

  /**
   * Provides the temporary directory where files related to wget options are stored.
   *
   * @return a File with the temporary directory where files related to wget options are stored.
   */
  public File getTmpDir() {
    return tmpDir;
  }

  /**
   * Provides the wget command line.
   *
   * @return a List<String> with the wget command line.
   */
  public List<String> getCommand() {
    return command;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[ExternalCrawlReq");
    sb.append("(I): ");
    sb.append(auid);
    sb.append(", pri: ");
    sb.append(getPriority());
    if (getRefetchDepth() >= 0) {
      sb.append(", depth: ");
      sb.append(getRefetchDepth());
    }
    sb.append(", crawlDesc: ");
    sb.append(crawlDesc);
    sb.append(", tmpDir: ");
    sb.append(tmpDir);
    sb.append(", command: ");
    sb.append(command);
    sb.append(", crawlerStatus: ");
    sb.append(crawlerStatus);
    sb.append("]");
    return sb.toString();
  }
}
