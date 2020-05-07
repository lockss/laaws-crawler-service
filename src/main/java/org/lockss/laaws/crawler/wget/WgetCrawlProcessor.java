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

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssRunnable;
import org.lockss.log.L4JLogger;
import org.lockss.util.FileUtil;

/**
 * The processor of a wget crawl.
 */
public class WgetCrawlProcessor {
  private static final L4JLogger log = L4JLogger.getLogger();

  private CrawlManagerImpl crawlManagerImpl = null;
  private CrawlerStatus crawlerStatus = null;

  /**
   * Constructor.
   * 
   * @param crawlManagerImpl A CrawlManagerImpl with the crawl manager.
   */
  public WgetCrawlProcessor(CrawlManagerImpl crawlManagerImpl) {
    this.crawlManagerImpl = crawlManagerImpl;
  }

  /**
   * Starts a new content wget crawl.
   * 
   * @param req A CrawlReq with the crawl request information.
   * @return a CrawlerStatus with the status of the crawl.
   */
  public CrawlerStatus startNewContentCrawl(WgetCrawlReq req) {
    log.debug2("req = {}", req);

    WgetCrawler crawler = null;
    WgetRunner runner = null;

    try {
      crawler = new WgetCrawler();
      crawler.setType(Crawler.Type.NEW_CONTENT);
      req.getCrawlerStatus().setType(crawler.getType().toString());
      crawler.setCrawlerStatus(req.getCrawlerStatus());
      crawlerStatus = crawler.getCrawlerStatus();

      runner = new WgetRunner(req);
      log.trace("{} set to start", runner);

      crawlManagerImpl.getStatus().addCrawlStatus(crawlerStatus);
      new Thread(runner).start();
    } catch (RuntimeException e) {
      String crawlerRunner = "no crawler" + " " +
              (runner == null ? "no runner" : runner.toString());
      log.error("Unexpected error attempting to start/schedule " +
	  req.getAuId() + " crawl " + crawlerRunner, e);
      req.getCrawlerStatus().setCrawlStatus(Crawler.STATUS_ERROR,
	  "Unexpected error");

      crawlerStatus = req.getCrawlerStatus();
    }

    log.debug2("req.getCrawlerStatus() = {}", req.getCrawlerStatus());
    log.debug2("crawlerStatus = {}", crawlerStatus);
    return crawlerStatus;
  }

  static String makeThreadName(WgetCrawlReq req) {
    return req.getCrawlerStatus().getType() + " WgetCrawl "
	+ req.getCrawlerStatus().getKey();
  }

  /**
   * A separate-thread runner of a wget command.
   */
  public class WgetRunner extends LockssRunnable {
    private WgetCrawlReq req = null;
    private List<String> command = null;
    private File tmpDir = null;

    /**
     * Constructor.
     * 
     * @param req A WgetCrawlReq with the crawl request information.
     */
    public WgetRunner(WgetCrawlReq req) {
      super(makeThreadName(req));
      this.req = req;
      command = req.getCommand();
      tmpDir = req.getTmpDir();
    }

    @Override
    public String toString() {
      return "[WgetRunner" + req.getCrawlerStatus().getKey() + "]";
    }

    /**
     * Code that runs in a separate thread.
     */
    public void lockssRun() {
      log.debug2("{} started", this);

      try {
	nowRunning();

	ProcessBuilder builder = new ProcessBuilder();
	builder.command(command);
	builder.inheritIO();

	log.trace("wget process started");
	Process process = builder.start();
	crawlerStatus.signalCrawlStarted();

	int exitCode = process.waitFor();
	log.trace("wget process finished: exitCode = {}", exitCode);

	if (exitCode == 0) {
	  crawlerStatus.setCrawlStatus(Crawler.STATUS_SUCCESSFUL);
	} else {
	  crawlerStatus.setCrawlStatus(Crawler.STATUS_ERROR,
	      "wget exit code: " + exitCode);
	}
      } catch (IOException ioe) {
        log.error("Exception caught running wget process", ioe);
      } catch (InterruptedException ignore) {
        // no action
      } finally {
        crawlerStatus.signalCrawlEnded();
        setThreadName(makeThreadName(req) + ": idle");

        log.trace("Deleting tree at {}", tmpDir);
        boolean isDeleted = FileUtil.delTree(tmpDir);
	log.trace("isDeleted = {}", isDeleted);

	if (!isDeleted) {
          log.warn("Temporary directory {} cannot be deleted after processing",
              tmpDir);
        }

        log.debug2("{} terminating", this);
      }
    }
  }
}
