package org.lockss.laaws.crawler.impl.external;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.lockss.config.Configuration;
import org.lockss.crawler.FollowLinkCrawler;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.CrawlRule;
import org.lockss.daemon.LoginPageChecker;
import org.lockss.daemon.PermissionChecker;
import org.lockss.plugin.base.BaseArchivalUnit;
import org.lockss.util.Logger;

public class ExternalCrawlerArchivalUnit extends BaseArchivalUnit {

  private static final Logger log = Logger.getLogger();
  private String auDescr = null;

  public ExternalCrawlerArchivalUnit(ExternalCrawlerPlugin plugin) {
    super(plugin);
  }

  // Called by ExternalCrawlerPlugin iff any config below ExternalCrawlerPlugin.PREFIX
  // has changed
  protected void setConfig(Configuration config,
      Configuration prevConfig,
      Configuration.Differences changedKeys) {
  }

  @Override
  public void loadAuConfigDescrs(Configuration auConfig)
      throws ConfigurationException {
    super.loadAuConfigDescrs(auConfig);
    auDescr = auConfig.get(ConfigParamDescr.BASE_URL.getKey());
    if (log.isDebug3()) {
      log.debug3("auConfig descriptor = " + auDescr);
    }
  }

  /**
   * Provides a name for the Archival Unit.
   *
   * @return The name for the Archival Unit.
   */
  @Override
  protected String makeName() {
    return "ExternalCrawler AU " + auDescr;
  }

  /**
   * return a string that points to the plugin registry page.
   *
   * @return a string that points to the plugin registry page for this registry.  This is just the
   * base URL.
   */
  @Override
  public Collection<String> getStartUrls() {
    return new ArrayList<>();
  }


  @Override
  public int getRefetchDepth() {
    return FollowLinkCrawler.DEFAULT_MAX_CRAWL_DEPTH;
  }

  @Override
  public LoginPageChecker getLoginPageChecker() {
    return null;
  }

  @Override
  public String getCookiePolicy() {
    return null;
  }

  /**
   * return the collection of crawl rules used to crawl and cache a list of Plugin JAR files.
   *
   * @return CrawlRule
   */
  @Override
  protected CrawlRule makeRule() {
    return new ExternalCrawlerArchivalUnit.ExternalCrawlerRule();
  }

  @Override
  public List<PermissionChecker> makePermissionCheckers() {
    return null;
  }

  // ExternalCrawler AU crawl rule implementation
  private class ExternalCrawlerRule implements CrawlRule {

    public int match(String url) {
      return CrawlRule.INCLUDE;
    }
  }
}
