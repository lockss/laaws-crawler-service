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

import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.laaws.crawler.impl.ApiUtils;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;

import java.util.UUID;

/**
 * Basic Pluggable crawl - extend to provide functionality for a specific crawler
 */
public abstract class PluggableCrawl {

  /**
   * The job for this crawl.
   */
  protected final CrawlJob crawlJob;
  /**
   * Description of the crawl requested.
   */
  protected final CrawlDesc crawlDesc;
  /**
   * The configuration of the crawler when this crawl began.  This is
   * stored so that a crawl will run with the parameters is was enqueued with.
   */
  protected final CrawlerConfig crawlerConfig;
  /**
   * The current status of a crawl as understood by the internal LOCKSS crawler.
   */
  protected CrawlerStatus crawlerStatus;
  /**
   * The callback to use when the crawl terminates with or without error.
   */
  protected CrawlManager.Callback callback;


  /**
   * Instantiates a new Pluggable crawl.
   * This will initialize the basic structures the concrete implementations
   * of this
   *
   * @param crawlerConfig the crawler config
   * @param crawlJob      the crawl job
   */
  public PluggableCrawl(CrawlerConfig crawlerConfig,
                        CrawlJob crawlJob) {
    this.crawlerConfig = crawlerConfig;
    this.crawlJob = crawlJob;
    this.crawlDesc = crawlJob.getCrawlDesc();
    crawlJob.setJobId(generateKey());
    crawlJob.setJobStatus(new JobStatus());
    crawlerStatus = new PluggableCrawlerStatus(this);
  }


  /**
   * Provides the crawler status.
   *
   * @return a CrawlerStatus with the crawler status.
   */
  public CrawlerStatus getCrawlerStatus() {
    return crawlerStatus;
  }

  /**
   * Saves the crawler status.
   *
   * @param crawlerStatus A CrawlerStatus with the crawler status.
   */
  protected void setCrawlerStatus(CrawlerStatus crawlerStatus) {
    this.crawlerStatus = crawlerStatus;
  }

  /**
   * Gets crawl status.
   *
   * @return the crawl status
   */
  public CrawlStatus getCrawlStatus() {
    return ApiUtils.makeCrawlStatus(crawlerStatus);
  }

  /**
   * Gets au id.
   *
   * @return the au id
   */
  public String getAuId() {
    return crawlDesc.getAuId();
  }

  /**
   * Gets crawler id.
   *
   * @return the crawler id
   */
  public String getCrawlerId() {
    return crawlDesc.getCrawlerId();
  }

  /**
   * Gets crawl desc.
   *
   * @return the crawl desc
   */
  public CrawlDesc getCrawlDesc() {
    return crawlDesc;
  }

  /**
   * Gets crawler config.
   *
   * @return the crawler config
   */
  public CrawlerConfig getCrawlerConfig() {
    return crawlerConfig;
  }

  /**
   * Gets crawl key.
   *
   * @return the crawl key
   */
  public String getCrawlKey() {
    return crawlJob.getJobId();
  }

  /**
   * Gets crawl kind.
   *
   * @return the crawl kind
   */
  public String getCrawlKind() {
    return crawlDesc.getCrawlKind().toString();
  }

  public CrawlManager.Callback getCallback() {
    return callback;
  }

  public void setCallback(CrawlManager.Callback callback) {
    this.callback = callback;
  }

  /**
   * Enqueue a crawl request.
   *
   * @return the crawler status
   */
  public abstract CrawlerStatus startCrawl();

  /**
   * Stop crawl crawler status.
   *
   * @return the crawler status
   */
  public abstract CrawlerStatus stopCrawl();

  /**
   * Gets au name.
   *
   * @param crawlerId the crawler id
   * @return the au name
   */
  public String getAuName(String crawlerId) {
    return crawlerId + ":" + getAuId();
  }

  /**
   * Generate key string.
   *
   * @return the string
   */
  protected String generateKey() {
    return UUID.randomUUID().toString();
  }

  /**
   * Gets job status.
   *
   * @return the job status
   */
  public JobStatus getJobStatus() {
    return crawlJob.getJobStatus();
  }

  /**
   * A wrapper around a Rest CrawlStatus that can be used inside the daemon code.
   * This notably allows for null ArchivalUnits.  This could be replaced by
   * a generic plugin that provides //getArchivalUnit but may not be necessary in
   * the short-term.
   */
  public static class PluggableCrawlerStatus extends CrawlerStatus {
    /**
     * Instantiates a new Pluggable crawler status.
     *
     * @param crawl the crawl
     */
    public PluggableCrawlerStatus(PluggableCrawl crawl) {
      CrawlDesc desc = crawl.getCrawlDesc();
      this.auid = crawl.getAuId();
      this.startUrls = desc.getCrawlList();
      this.crawlerId = desc.getCrawlerId();
      this.auName = crawl.getAuName(auid);
      this.key = crawl.getCrawlKey();
      setPriority(desc.getPriority());
      setDepth(desc.getCrawlDepth());
      setRefetchDepth(desc.getRefetchDepth());
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
      return "PluggableCrawlerStatus{" +
        "key='" + key + '\'' +
        ", startTime=" + startTime +
        ", endTime=" + endTime +
        ", statusMessage='" + statusMessage + '\'' +
        ", status=" + status +
        ", startUrls=" + startUrls +
        ", auid='" + auid + '\'' +
        ", auName='" + auName + '\'' +
        ", depth=" + depth +
        ", priority=" + priority +
        ", type='" + type + '\'' +
        ", crawlerId='" + crawlerId + '\'' +
        '}';
    }
  }
}
