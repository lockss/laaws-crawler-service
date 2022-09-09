package org.lockss.laaws.crawler.impl.pluggable;

import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.util.rest.crawler.CrawlJob;

/**
 * An interface to defines the basic functions of a pluggable crawler
 */
public interface PluggableCrawler {

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

  PluggableCrawl requestCrawl(CrawlJob crawlJob, Callback callback);

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


  interface Callback {
    /**
     * Callback to call when a crawl attempt completes
     *
     * @param success whether the crawl was successful
     * @param status  the CrawlStatus containting results.
     */
    void signalCrawlAttemptCompleted(boolean success, CrawlStatus status);
  }

}
