package org.lockss.laaws.crawler.impl.pluggable;

import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.rest.crawler.CrawlJob;

/**
 * An interface to defines the basic functions of a pluggable crawler
 */
public interface PluggableCrawler {

  /**
   * Return the unique Id for this crawler.
   * @return the id of the crawler
   */
  String getCrawlerId();

  /**
   * set the configuration parameters for this crawler
   *
   * @param crawlerConfig the configuration parameters to use
   */
  void updateCrawlerConfig(CrawlerConfig crawlerConfig);

  /**
   * Return the configuration for this crawler
   *
   * @return the configuration parameters in use by this crawler.
   */
  CrawlerConfig getCrawlerConfig();

  PluggableCrawl requestCrawl(ArchivalUnit au, CrawlJob crawlJob);

  /**
   * Stop a crawl a specific crawl
   *
   * @param crawlId The crawl id of the crawl to stop
   * @return The PluggableCrawl containing the results of this crawl attempt.
   */
  PluggableCrawl stopCrawl(String crawlId);

  /**
   * Get a Crawl for a given crawl id.
   *
   * @param crawlId The crawl id of the crawl to stop
   * @return The PluggableCrawl that matches a crawl id.
   */
  PluggableCrawl getCrawl(String crawlId);

  /**
   * Stop all crawls and clear the crawl queue managed by this crawler
   */
  void deleteAllCrawls();

  /**
   * is this crawler enabled
   *
   * @return true if this crawler is set to enabled.
   */
  boolean isCrawlerEnabled();

  /**
   * Shutdown the crawler.
    */
  void shutdown();

  /**
   * disable this crawler clearing any queued crawls.
   * if the crawler was running is now marked as disabled
   * or is missing from the supported crawler ids in the configuration
    * @param abortCrawling abort the currently running crawls.
   */
  void disable(boolean abortCrawling);

  /**
   * Set the  Crawl Manager which created and maintains this crawler.
   * @param pluggableCrawlManager
   */
  void setPluggableCrawlManager(PluggableCrawlManager pluggableCrawlManager);

}
