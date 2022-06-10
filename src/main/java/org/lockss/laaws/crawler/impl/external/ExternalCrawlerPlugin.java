package org.lockss.laaws.crawler.impl.external;

import java.util.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ContentValidationException;
import org.lockss.plugin.base.BasePlugin;
import org.lockss.util.Logger;
import org.lockss.util.urlconn.CacheSuccess;
import org.lockss.util.urlconn.HttpResultMap;

public class ExternalCrawlerPlugin extends BasePlugin {

  /** The key of the ExternalCrawler plugin */
  static final String PLUGIN_ID = "org.lockss.plugin.ExternalCrawlerPlugin";
  private static final Logger log = Logger.getLogger();
  /** Configuration prefix. */
  private static final String PREFIX = Configuration.PREFIX + "plugin.extenralcrawler.";

  /** List of defining properties (obtain from the crawl request). */
  private static final List<ConfigParamDescr> configDescrs =
      new ArrayList<>(Collections.singletonList(ConfigParamDescr.BASE_URL));

  private final String pluginName = "ExternalCrawler";
  private final String currentVersion = "1";

  /**
   * Default constructor.
   */
  public ExternalCrawlerPlugin() {

  }

  protected ArchivalUnit createAu0(Configuration auConfig)
      throws ArchivalUnit.ConfigurationException {
    // create a new archival unit
    ExternalCrawlerArchivalUnit au = new ExternalCrawlerArchivalUnit(this);

    // Now configure it.
    au.setConfiguration(auConfig);
    Configuration curConfig = ConfigManager.getCurrentConfig();
    au.setConfig(curConfig, ConfigManager.EMPTY_CONFIGURATION,
        curConfig.differences(null));  // all differences
    return au;
  }

  /**
   * The global config has changed
   * @param newConfig new Configuration
   * @param prevConfig previous Configuration
   * @param changedKeys the Differecne between old and new config.
   */
  protected void setConfig(Configuration newConfig,
      Configuration prevConfig,
      Configuration.Differences changedKeys) {
    if (changedKeys.contains(PREFIX)) {
      for (Iterator<ArchivalUnit> iter = getAllAus().iterator(); iter.hasNext();
      ) {
        try {
          ExternalCrawlerArchivalUnit au = (ExternalCrawlerArchivalUnit) iter.next();
          au.setConfig(newConfig, prevConfig, changedKeys);
        }
        catch (Exception e) {
          log.warning("setConfig: " + this, e);
        }
      }
    }
  }

  /**
   * ExternalCrawlerPlugin does not have a configuration. This is overridden to force no
   * implementation.
   */
  protected void setTitleConfigFromConfig(Configuration allTitles) {
    // Not implemented.
  }

  @Override
  public String getVersion() {
    return currentVersion;
  }

  @Override
  public String getPluginName() {
    return pluginName;
  }

  /**
   * We only have one defining attribute, a base URL.
   */
  public List<ConfigParamDescr> getLocalAuConfigDescrs() {
    return configDescrs;
  }

  protected void initResultMap() {
    // Empty files are used to retract plugins; don't warn when collected.
    HttpResultMap hResultMap = new HttpResultMap();
    hResultMap.storeMapEntry(ContentValidationException.EmptyFile.class,
        CacheSuccess.class);
    resultMap = hResultMap;
  }
}
