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

import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManager;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.utils.ExecutorUtils;
import org.lockss.laaws.rs.core.LockssRepository;
import org.lockss.log.L4JLogger;
import org.lockss.util.ClassUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ATTR_CRAWLER_ID;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ENABLED;

/**
 * A Base implementation of a CmdLineCrawler.
 */
public class CmdLineCrawler implements PluggableCrawler {
  public static final String PREFIX = Configuration.PREFIX + "crawlerservice.";

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * The Configuration for this crawler.
   */
  protected CrawlerConfig config;
  public static final String DEFAULT_EXECUTOR_SPEC = "100;2";
  private final ExecutorUtils.ExecSpec defExecSpec;

  /**
   * The map of crawls for this crawler.
   */
  protected HashMap<String, CmdLineCrawl> crawlMap = new HashMap<>();
  protected CommandLineBuilder cmdLineBuilder;
  protected PluggableCrawlManager pcManager;
  private PriorityBlockingQueue<Runnable> crawlQueue;

  private LockssRepository v2Repo;
  private ThreadPoolExecutor crawlQueueExecutor;

  private String namespace;

  /**
   * Instantiates a new Cmd line crawler.
   */
  public CmdLineCrawler() {
    defExecSpec = ExecutorUtils.parsePoolSpecInto(DEFAULT_EXECUTOR_SPEC, new ExecutorUtils.ExecSpec());
  }

/*---------------
  Builder Methods
  ---------------
 */
  public CmdLineCrawler setCrawlManager(PluggableCrawlManager pcManager) {
    this.pcManager = pcManager;
    return this;
  }

  public CmdLineCrawler setV2Repo(LockssRepository v2Repo) {
    this.v2Repo = v2Repo;
    return this;
  }

  public CmdLineCrawler setNamespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public CmdLineCrawler setConfig(CrawlerConfig config) {
    this.config = config;
    return this;
  }

  public CmdLineCrawler setCmdLineBuilder(CommandLineBuilder cmdLineBuilder) {
    this.cmdLineBuilder = cmdLineBuilder;
    return this;
  }

  public CrawlerConfig getConfig() {
    return config;
  }

  protected CommandLineBuilder getCmdLineBuilder() {
    return cmdLineBuilder;
  }

  protected String getNamespace() {
    return namespace;
  }

  @Override
  public String getCrawlerId() {
    return config.getCrawlerId();
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
        setCmdLineBuilder(clb);
      }
      catch (Exception ex) {
        log.error("Unable to instantiate CommandLineBuilder: {} for Crawler {} ", builderClassName, crawlerId);
      }
    }
    String qspec= attr.get(PREFIX+crawlerId+".executor.spec");
    initCrawlScheduler(qspec);
  }

  @Override
  public CrawlerConfig getCrawlerConfig() {
    return config;
  }

  @Override
  public PluggableCrawl requestCrawl(CrawlJob crawlJob, CrawlManager.Callback callback) {
    //check to see if we have already queued a job to crawl this au

    if (!pcManager.isEligibleForCrawl(crawlJob.getCrawlDesc().getAuId())) {
      log.warn("Crawl request {} ignored! au is not eligible for crawl.", crawlJob);
      return null;
    }
    CmdLineCrawl clCrawl = new CmdLineCrawl(this, crawlJob);
    clCrawl.setCallback(callback);
    crawlMap.put(crawlJob.getJobId(), clCrawl);
    if (crawlQueue.offer(new RunnableCrawlJob(crawlJob,clCrawl))) {
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
    CmdLineCrawl clCrawl = crawlMap.remove(crawlId);
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
    crawlQueue.clear();
  }

  @Override
  public boolean isCrawlerEnabled() {
    Map<String, String> attrs = config.getAttributes();
    return Boolean.parseBoolean(attrs.get(config.getCrawlerId() + ENABLED));
  }

  @Override
  public void shutdown() {
    shutdownWithWait(crawlQueueExecutor);
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

  @Override
  public void disable(boolean abortCrawling) {
    // we need clear the queue
    if(abortCrawling) {
      shutdown();
    }
    crawlQueue.clear();
  }

  public void storeInRepository (String auId, File warcFile, boolean isCompressed) throws IOException {
    BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(warcFile.toPath()));
    v2Repo.addArtifacts(namespace, auId, bis, LockssRepository.ArchiveType.WARC,isCompressed);
  }

  protected void initCrawlScheduler(String reqSpec) {
    ExecutorUtils.ExecSpec crawlQSpec = ExecutorUtils.getCurrentSpec(reqSpec, defExecSpec);
    if (crawlQueue == null) {
      crawlQueue = new PriorityBlockingQueue<>(crawlQSpec.queueSize);
   }
    crawlQueueExecutor = ExecutorUtils.createOrReConfigureExecutor(crawlQueueExecutor,crawlQSpec,crawlQueue);
  }

  public interface CommandLineBuilder {
    List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir) throws IOException;
  }

  public static class RunnableCrawlJob implements Runnable, Comparable<RunnableCrawlJob> {
    private final CmdLineCrawl cmdLineCrawl;
    public final CrawlJob crawlJob;

    public RunnableCrawlJob(CrawlJob crawlJob, CmdLineCrawl cmdLineCrawl) {
      this.crawlJob = crawlJob;
      this.cmdLineCrawl = cmdLineCrawl;
    }
    public int getPriority() {
      return crawlJob.getCrawlDesc().getPriority();
    }

    public long getRequestDate() {
      return crawlJob.getRequestDate();
    }

    @Override
    public int compareTo(RunnableCrawlJob other) {
        int p1 = getPriority();
        int p2 = other.getPriority();
        if (p1 < p2) {return 1;}
        if (p1 > p2) {return -1;}
        // if they are equal return the one that was requested first.
        return Long.compare(getRequestDate(),other.getRequestDate());
    }

    @Override
    public void run() {
      cmdLineCrawl.getRunnable().run();
    }
  }

}