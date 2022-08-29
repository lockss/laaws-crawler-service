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
package org.lockss.laaws.crawler.impl.pluggable;

import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssRunnable;
import org.lockss.laaws.crawler.impl.CrawlsApiServiceImpl;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.log.L4JLogger;
import org.lockss.util.io.FileUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ENABLED;

/**
 * A Base implementation of a CmdLineCrawler.
 */
public class CmdLineCrawler implements PluggableCrawler {

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * The Config.
   */
  CrawlerConfig config;

  /**
   * The map of crawls for this crawler.
   */
  HashMap<String, CmdLineCrawl> crawlMap = new HashMap<>();

  PriorityQueue<CrawlJob> crawlQueue = new PriorityQueue<>(new CrawlJobComparator());

  /**
   * Instantiates a new Cmd line crawler.
   */
  public CmdLineCrawler() {
  }

  @Override
  public void updateCrawlerConfig(CrawlerConfig crawlerConfig) {
    this.config = crawlerConfig;
  }

  @Override
  public CrawlerConfig getCrawlerConfig() {
    return config;
  }

  @Override
  public PluggableCrawl requestCrawl(CrawlJob crawlJob, Callback callback) {
    if (crawlQueue.offer(crawlJob)) {
      CmdLineCrawl clCrawl = new CmdLineCrawl(config, crawlJob);
      clCrawl.setCallback(callback);
      crawlMap.put(crawlJob.getJobId(), clCrawl);
      JobStatus status = crawlJob.getJobStatus();
      status.setStatusCode(StatusCodeEnum.QUEUED);
      status.setMsg("Queued.");
      return clCrawl;
    }
    else {
      log.warn("Attempt to queue job {} failed!", crawlJob);
      return null;
    }
  }


  @Override
  public PluggableCrawl startCrawl(CrawlJob crawlJob) {
    PluggableCrawl pCrawl = crawlMap.get(crawlJob.getJobId());
    if (pCrawl == null) {
      log.error("Attempt to start unknown crawlJob {}", crawlJob);
      return null;
    }
    pCrawl.startCrawl();
    JobStatus status = crawlJob.getJobStatus();
    status.setStatusCode(StatusCodeEnum.ACTIVE);
    status.setMsg("Running.");
    return pCrawl;
  }

  @Override
  public PluggableCrawl stopCrawl(String crawlId) {
    CmdLineCrawl clCrawl = crawlMap.get(crawlId);
    if (clCrawl != null) {
      clCrawl.stopCrawl();
      JobStatus status = clCrawl.getJobStatus();
      status.setStatusCode(StatusCodeEnum.ABORTED);
      status.setMsg("Crawl Aborted.");
    }
    return clCrawl;
  }

  @Override
  public PluggableCrawl getCrawl(String crawlId) {
    return crawlMap.get(crawlId);
  }

  @Override
  public void deleteAllCrawls() {
    for (String key : crawlMap.keySet()) {
      stopCrawl(key);
    }
    crawlMap.clear();
    crawlQueue.clear();
  }

  @Override
  public boolean isCrawlerEnabled() {
    Map<String, String> attrs = config.getAttributes();
    return Boolean.parseBoolean(attrs.get(config.getCrawlerId() + ENABLED));
  }

  /**
   * return the next CrawlJob in the queue
   *
   * @return the CrawlJob or null if queue is empty.
   */
  public CrawlJob getNextCrawl() {
    return crawlQueue.poll();
  }


  public interface CommandLineBuilder {
    List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir) throws IOException;
  }

  /**
   * A class to wrap a single CommandLineCrawl
   */
  public static class CmdLineCrawl extends PluggableCrawl {
    /*
    The command line as a list to exexute.
     */
    private List<String> command = null;
    /**
     * The temp directory used to store any files.
     */
    private File tmpDir = null;

    private CommandLineBuilder cmdLineBuilder;

    /**
     * Instantiates a new Cmd line crawl.
     *
     * @param crawlerConfig the crawler config
     * @param crawlJob      the crawl job
     */
    public CmdLineCrawl(CrawlerConfig crawlerConfig, CrawlJob crawlJob) {
      super(crawlerConfig, crawlJob);
    }

    @Override
    public CrawlerStatus startCrawl() {
      CrawlerStatus cs = getCrawlerStatus();
      JobStatus js = getJobStatus();
      try {
        tmpDir = FileUtil.createTempDir("laaws-pluggable-crawler", "");
        command = getCommandLineBuilder().buildCommandLine(getCrawlDesc(), tmpDir);
      }
      catch (IOException ioe) {
        log.error("Unable to create output directory for crawl:", ioe);
        js.setStatusCode(StatusCodeEnum.ERROR);
      }
      return cs;
    }

    @Override
    public CrawlerStatus stopCrawl() {
      return getCrawlerStatus();
    }

    /**
     * Gets tmp dir.
     *
     * @return the tmp dir
     */
    public File getTmpDir() {
      return tmpDir;
    }

    /**
     * Gets command.
     *
     * @return the command
     */
    public List<String> getCommand() {
      return command;
    }

    protected CommandLineBuilder getCommandLineBuilder() {
      return cmdLineBuilder;
    }

    protected void setCommandLineBuilder(CommandLineBuilder cmdLineBuilder) {
      this.cmdLineBuilder = cmdLineBuilder;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      sb.append("(I): ");
      sb.append(getAuId());
      sb.append(", pri: ");
      sb.append(getCrawlDesc().getPriority());
      if (getCrawlDesc().getRefetchDepth() >= 0) {
        sb.append(", depth: ");
        sb.append(getCrawlDesc().getRefetchDepth());
      }
      sb.append(", crawlDesc: ");
      sb.append(getCrawlDesc());
      sb.append(", tmpDir: ");
      sb.append(tmpDir);
      sb.append(", command: ");
      sb.append(command);
      sb.append(", crawlerStatus: ");
      sb.append(getCrawlerStatus());
      sb.append("]");
      return sb.toString();
    }
  }

  /**
   * A separate-thread runner for a command.
   */
  public static class CrawlRunner extends LockssRunnable {
    private final CrawlDesc crawlDesc;
    private CmdLineCrawl req = null;
    private List<String> command = null;
    private File tmpDir = null;

    /**
     * Constructor.
     *
     * @param req A CmdLineCrawl with the crawl request information.
     */
    public CrawlRunner(CmdLineCrawl req) {
      super(makeThreadName(req));
      this.req = req;
      command = req.getCommand();
      tmpDir = req.getTmpDir();
      crawlDesc = req.getCrawlDesc();
    }

    /**
     * Make thread name string.
     *
     * @param req the req
     * @return the string
     */
    static String makeThreadName(CmdLineCrawl req) {
      return req.getCrawlKind() + " " + req.getCrawlerId() + " " + req.getCrawlKey();
    }

    @Override
    public String toString() {
      return "[CrawlRunner" + makeThreadName(req) + "]";
    }

    /**
     * Code that runs in a separate thread.
     */
    public void lockssRun() {
      log.debug2("{} started", this);
      CrawlerStatus crawlerStatus = req.getCrawlerStatus();

      try {
        nowRunning();

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(command);
        builder.inheritIO();

        log.trace("external crawl process started");
        Process process = builder.start();
        crawlerStatus.signalCrawlStarted();

        int exitCode = process.waitFor();
        log.trace("external crawl process finished: exitCode = {}", exitCode);
        if (exitCode == 0) {
          //Todo: Add a call to the repository to store this data.
          crawlerStatus.setCrawlStatus(Crawler.STATUS_SUCCESSFUL);
        }
        else {
          crawlerStatus.setCrawlStatus(
            Crawler.STATUS_ERROR, "crawl exited with code: " + exitCode);
        }
        Callback callback = req.getCallback();
        if (callback != null) {
          CrawlStatus cs = CrawlsApiServiceImpl.getCrawlStatus(crawlerStatus);
          callback.signalCrawlAttemptCompleted(exitCode == 0, cs);
        }
      }
      catch (IOException ioe) {
        log.error("Exception caught running process", ioe);
      }
      catch (InterruptedException ignore) {
        // no action
      }
      finally {
        crawlerStatus.signalCrawlEnded();
        setThreadName(makeThreadName(req) + ": idle");
        log.trace("Deleting tree at {}", tmpDir);
        boolean isDeleted = FileUtil.delTree(tmpDir);
        log.trace("isDeleted = {}", isDeleted);
        if (!isDeleted) {
          log.warn("Temporary directory {} cannot be deleted after processing", tmpDir);
        }
        log.debug2("{} terminating", this);
      }
    }
  }

  static class CrawlJobComparator implements Comparator<CrawlJob> {
    @Override
    public int compare(CrawlJob o1, CrawlJob o2) {
      {
        int p1 = o1.getCrawlDesc().getPriority();
        int p2 = o2.getCrawlDesc().getPriority();
        if (p1 < p2) {return 1;}
        if (p1 > p2) {return -1;}
        // if they are equal return the one that was requested first.
        return o1.getRequestDate().compareTo(o2.getRequestDate());
      }
    }
  }
}
