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

import org.lockss.app.LockssApp;
import org.lockss.config.Configuration;
import org.lockss.config.CurrentConfig;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.log.L4JLogger;
import org.lockss.util.ClassUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.lockss.laaws.crawler.CrawlerApplication.PLUGGABLE_CRAWL_MANAGER;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ATTR_CRAWLER_ID;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ENABLED;

/**
 * A Base implementation of a CmdLineCrawler.
 */
public class CmdLineCrawler implements PluggableCrawler {
  public static final String PREFIX = Configuration.PREFIX + "crawlerservice.";
  public static final String PARAM_MAX_RUNNING_CRAWLS = PREFIX + "maxRunningCrawls";
  public static final int DEFAULT_MAX_RUNNING_CRAWLS = 1;
  public static final int DEFAULT_MAX_QUEUE_SIZE = 1000;
  private static final String PARAM_MAX_QUEUE_SIZE = PREFIX + "maxQueueSize";
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * The Configuration for this crawler.
   */
  protected CrawlerConfig config;

  /**
   * The map of crawls for this crawler.
   */
  protected HashMap<String, CmdLineCrawl> crawlMap = new HashMap<>();
  protected CommandLineBuilder cmdLineBuilder;
  protected PluggableCrawlManager pcManager;
  private ExecutorService priorityJobPoolExecutor;
  private ExecutorService priorityJobScheduler;
  private final PriorityBlockingQueue<CrawlJob> crawlQueue;
  /**
   * The maximum number of crawls allowed for this crawler
   */
  private int maxRunningCrawls;
  private final int maxQueueSize;


  /**
   * Instantiates a new Cmd line crawler.
   */
  public CmdLineCrawler() {
    pcManager = (PluggableCrawlManager) LockssApp.getManagerByKeyStatic(PLUGGABLE_CRAWL_MANAGER);
    maxQueueSize = CurrentConfig.getCurrentConfig().getInt((PARAM_MAX_QUEUE_SIZE),
      DEFAULT_MAX_QUEUE_SIZE);
    crawlQueue = new PriorityBlockingQueue<>(maxQueueSize, new CrawlJobComparator());
    //XXXX TODO - do we want to allow this to be changed after starting.
    maxRunningCrawls = CurrentConfig.getCurrentConfig().getInt(PARAM_MAX_RUNNING_CRAWLS,
      DEFAULT_MAX_RUNNING_CRAWLS);
    initCrawlScheduler(maxRunningCrawls);
  }

  public static Integer toInteger(String value, Integer defValue) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException nfe) {
      return defValue;
    }
  }


  @Override
  public void updateCrawlerConfig(CrawlerConfig crawlerConfig) {
    this.config = crawlerConfig;
    Map<String, String> attr = crawlerConfig.getAttributes();
    String crawlerId = attr.get(ATTR_CRAWLER_ID);
    // check to see if we have a defined Command Processor
    String attrName = PREFIX + crawlerId + ".cmdLineBuilder";
    String builderClassName = config.getAttributes().get(attrName);
    if (builderClassName != null) {
      try {
        log.debug2("Instantiating " + builderClassName);
        final Class<?> builderClass = Class.forName(builderClassName);
        CommandLineBuilder clb = (CommandLineBuilder) ClassUtil.instantiate(builderClassName, builderClass);
        setCommandLineBuilder(clb);
      }
      catch (Exception ex) {
        log.error("Unable to instantiate CommandLineBuilder: {} for Crawler {} ", builderClassName, crawlerId);
      }
    }
  }

  @Override
  public CrawlerConfig getCrawlerConfig() {
    return config;
  }

  @Override
  public PluggableCrawl requestCrawl(CrawlJob crawlJob, Callback callback) {
    //check to see if we have already queued a job to crawl this au
    if (pcManager.isEligibleForCrawl(crawlJob.getCrawlDesc().getAuId())) {
      log.warn("Crawl request {} ignored! au is not eligible for crawl.", crawlJob);
      return null;
    }
    if (crawlQueue.offer(crawlJob)) {
      CmdLineCrawl clCrawl = new CmdLineCrawl(this, crawlJob);
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
  public PluggableCrawl stopCrawl(String crawlId) {
    CmdLineCrawl clCrawl = crawlMap.get(crawlId);
    if (clCrawl != null) {
      clCrawl.stopCrawl();
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

  @Override
  public void shutdown() {
    shutdownWithWait(priorityJobScheduler);
    shutdownWithWait(priorityJobPoolExecutor);
  }

  protected CommandLineBuilder getCommandLineBuilder() {
    return cmdLineBuilder;
  }

  protected void setCommandLineBuilder(CommandLineBuilder cmdLineBuilder) {
    this.cmdLineBuilder = cmdLineBuilder;
  }

  protected void initCrawlScheduler(int poolSize) {
    priorityJobPoolExecutor = Executors.newFixedThreadPool(poolSize);
    priorityJobScheduler = Executors.newSingleThreadExecutor();
    priorityJobScheduler.execute(() -> {
      while (true) {
        try {
          CrawlJob job = crawlQueue.take();
          CmdLineCrawl cmdLineCrawl = new CmdLineCrawl(this, job);
          priorityJobPoolExecutor.execute(cmdLineCrawl.getRunnable());
        }
        catch (InterruptedException e) {
          // exception needs special handling
          log.error("Job interrupted!");
          break;
        }
      }
    });
  }

  protected void shutdownWithWait(ExecutorService scheduler) {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
        scheduler.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
          log.error("Pool did not terminate");
        }
      }
    }
    catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      scheduler.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  public interface CommandLineBuilder {
    List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir) throws IOException;
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
