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

import java.util.*;
import org.lockss.config.AuConfiguration;
import org.lockss.config.Configuration;
import org.lockss.config.ConfigManager;
import org.lockss.db.DbException;
import org.lockss.laaws.crawler.impl.ApiUtils;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.utils.ExecutorUtils;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.ClassUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import org.lockss.util.rest.repo.LockssRepository;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ATTR_CRAWLER_ID;
import static org.lockss.laaws.crawler.impl.PluggableCrawlManager.ENABLED;
import static org.lockss.laaws.crawler.utils.ExecutorUtils.DEFAULT_EXECUTOR_SPEC;
import static org.lockss.laaws.crawler.utils.ExecutorUtils.EXEC_PREFIX;

/**
 * A Base implementation of a CmdLineCrawler.
 */
public class CmdLineCrawler implements PluggableCrawler {
  public static final String PREFIX = Configuration.PREFIX + "crawlerservice.";

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Controls the number of AUs running cmd line crawls
   */
  public static final String PARAM_CMDLINE_CRAWL_EXECUTOR_SPEC =
      EXEC_PREFIX + "cmdLineCrawl.spec";
  public static final String DEFAULT_CMDLINE_CRAWL_EXECUTOR_SPEC = "10;2";

  public static final String ATTR_OUTPUT_LOG_LEVEL = "outputLogLevel";
  public static final String DEFAULT_OUTPUT_LOG_LEVEL = "INFO";

  public static final String ATTR_ERROR_LOG_LEVEL = "errorLogLevel";
  public static final String DEFAULT_ERROR_LOG_LEVEL = "ERROR";

  private static final String START_URL_KEY = "start_urls";
  private static final String URL_STEMS_KEY = "url_stems";

  /**
   * The Configuration for this crawler.
   */
  protected CrawlerConfig config;

  /**
   * The level to use when logging output from a process
   */
  protected String outputLogLevel;


  /**
   * The level to use when logging error from a process
   */
  protected String errorLogLevel;

  /**
   * The map of crawls for this crawler.
   */
  protected HashMap<String, CmdLineCrawl> crawlMap = new HashMap<>();
  protected CommandLineBuilder cmdLineBuilder;
  protected PluggableCrawlManager pcManager;

  private LockssRepository v2Repo;
  private ThreadPoolExecutor crawlQueueExecutor;

  private String namespace;
  private boolean joinOutputStreams;


  /**
   * Instantiates a new Cmd line crawler.
   */
  public CmdLineCrawler() {
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
    updateCrawlerConfig(config);
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
    String attrName = "cmdLineBuilder";
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
    if(qspec == null) qspec = DEFAULT_EXECUTOR_SPEC;
    initCrawlScheduler(qspec);

    outputLogLevel= attr.get(PREFIX+crawlerId+ATTR_OUTPUT_LOG_LEVEL);
    if(outputLogLevel == null) outputLogLevel = DEFAULT_OUTPUT_LOG_LEVEL;

    errorLogLevel= attr.get(PREFIX+crawlerId+ATTR_ERROR_LOG_LEVEL);
    if(outputLogLevel == null) outputLogLevel = DEFAULT_OUTPUT_LOG_LEVEL;

    if (outputLogLevel.equals(errorLogLevel)) {
      joinOutputStreams = true;
    }
  }

  @Override
  public CrawlerConfig getCrawlerConfig() {
    return config;
  }

  @Override
  public PluggableCrawl requestCrawl(ArchivalUnit au, CrawlJob crawlJob) {
    //check to see if we have already queued a job to crawl this au

    if (!pcManager.isEligibleForCrawl(crawlJob.getCrawlDesc().getAuId())) {
      log.warn("Crawl request {} ignored! au is not eligible for crawl.", crawlJob);
      return null;
    }
    CmdLineCrawl clCrawl = new CmdLineCrawl(this, au, crawlJob);
    crawlMap.put(crawlJob.getJobId(), clCrawl);
    crawlQueueExecutor.submit(new RunnableCrawlJob(crawlJob, clCrawl));
    JobStatus status = crawlJob.getJobStatus();
    status.setStatusCode(StatusCodeEnum.QUEUED);
    status.setMsg("Pending.");
    return clCrawl;
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
    crawlQueueExecutor.shutdownNow();
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
    if(abortCrawling) {
      // this will abort all running tasks and empty the queue
      List<Runnable> runnables = crawlQueueExecutor.shutdownNow();
      if(log.isDebug2Enabled()) log.debug2("successfullly aborted {}", runnables);
    }
    else {
      // this will empty the queue and wait for threads to complete.
      crawlQueueExecutor.shutdown();
    }
  }

  @Override
  public void setPluggableCrawlManager(PluggableCrawlManager pluggableCrawlManager) {
    pcManager = pluggableCrawlManager;
  }

  public void storeInRepository (String auId, File warcFile, boolean isCompressed) throws IOException {
    BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(warcFile.toPath()));
    ensureRepo();
    log.debug2("Calling Repository with warc for auid {}", auId);
    v2Repo.addArtifacts(namespace, auId, bis, LockssRepository.ArchiveType.WARC, isCompressed);
    log.debug2("Returned from call to repo");
  }


  public void updateAuConfig(ArchivalUnit au, boolean isRepairCrawl, List<String>reqUrls,
                             List<String> crawlStems) throws IOException {
    log.debug("updating config for {}");
    ConfigManager cm = pcManager.getConfigManager();
    AuConfiguration au_config;
    try {
      au_config = cm.retrieveArchivalUnitConfiguration(au.getAuId());
      if(!isRepairCrawl) {
        log.debug2("Updating AuConfig for start urls.");
        updateAuConfigItem(au_config, START_URL_KEY, getCheckedStartUrls(au,reqUrls));
      }
      log.debug2("Updating AuConfig for url stems: {}", reqUrls);
      updateAuConfigItem(au_config, URL_STEMS_KEY, crawlStems);
      cm.storeArchivalUnitConfiguration(au_config, true);
    }
    catch (DbException dbe) {
      throw new IOException("Unable update AU configuration",dbe);
    }
  }

  List<String> getCheckedStartUrls(ArchivalUnit au, List<String> inUrls) {
    List<String> outUrls = new ArrayList<>();
    if(inUrls != null && !inUrls.isEmpty()) {
      for(String url : inUrls) {
        outUrls.add(checkStartUrl(au, url));
      }
    }
    return outUrls;
  }

  String checkStartUrl(ArchivalUnit au, String startUrl) {
    if(!startUrl.endsWith("/")) {
      CachedUrl cu = au.makeCachedUrl(startUrl);
      if(!cu.hasContent()) {
        String newUrl = startUrl + "/";
        cu = au.makeCachedUrl(newUrl);
        if(cu.hasContent()) return newUrl;
      }
    }
    return startUrl;
  }

  void updateAuConfigItem(AuConfiguration auConfig, String key, List<String> updateList) {
    Map<String, String> configMap = auConfig.getAuConfig();
    List<String> configList;
    String config_str = configMap.get(key);
    if(config_str != null) {
      configList = new ArrayList<>(Arrays.asList(config_str.split(";")));
      log.debug2("Current config string: {} a list of {} elements.", config_str, configList.size());
    }
    else {
      configList = new ArrayList<>();
    }
    for(String elem: updateList) {
      if(!configList.contains(elem)) {
        configList.add(elem);
      }
    }
    auConfig.putAuConfigItem(key,String.join(";", configList));
  }


  protected void initCrawlScheduler(String reqSpec) {
    crawlQueueExecutor = ExecutorUtils.createOrReConfigureExecutor(crawlQueueExecutor,
        reqSpec, DEFAULT_CMDLINE_CRAWL_EXECUTOR_SPEC);
  }

  protected boolean didCrawlSucceed(int exitCode) {
    return exitCode == 0;
  }

  public String getOutputLogLevel() {
    return outputLogLevel;
  }

  public String getErrorLogLevel() {
    return errorLogLevel;
  }

  public boolean isJoinOutputStreams() {
    return joinOutputStreams;
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
  private void ensureRepo() throws IOException{
    if(v2Repo == null) {
      v2Repo = ApiUtils.getV2Repo();
      namespace = ApiUtils.getV2Namespace();
    }
    if(v2Repo == null || !v2Repo.isReady()) {
      log.error("Unable to store warc artifacts - Repository is not ready for connections.");
      throw new IOException("Unable to store warc artifacts - Repository is not ready for connections.");
    }
  }
}
