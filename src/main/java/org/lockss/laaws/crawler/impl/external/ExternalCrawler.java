package org.lockss.laaws.crawler.impl.external;

import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.laaws.crawler.model.CrawlerConfig;

public class ExternalCrawler {
  private CrawlerStatus crawlStatus;
  private Crawler.Type type;

  private CrawlerConfig crawlerConfig;

  /**
   * Provides the type of crawler.
   *
   * @return a Crawler.Type with the type of crawler.
   */
  public Crawler.Type getType() {
    return type;
  }

  /**
   * Saves the type of crawler.
   *
   * @param type A Crawler.Type with the type of crawler.
   */
  protected void setType(Crawler.Type type) {
    this.type = type;
  }

  /**
   * Provides the crawler status.
   *
   * @return a CrawlerStatus with the crawler status.
   */
  public CrawlerStatus getCrawlerStatus() {
    return crawlStatus;
  }

  /**
   * Saves the crawler status.
   *
   * @param crawlerStatus A CrawlerStatus with the crawler status.
   */
  protected void setCrawlerStatus(CrawlerStatus crawlerStatus) {
    crawlStatus = crawlerStatus;
  }


}
