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

package org.lockss.laaws.crawler;

import static org.lockss.app.LockssApp.PARAM_START_PLUGINS;
import static org.lockss.app.LockssApp.managerKey;
import static org.lockss.app.ManagerDescs.*;

import org.lockss.app.LockssApp;
import org.lockss.app.LockssApp.AppSpec;
import org.lockss.app.LockssApp.ManagerDesc;
import org.lockss.app.LockssDaemon;
import org.lockss.app.ServiceDescr;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.plugin.PluginManager;
import org.lockss.spring.base.BaseSpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring-Boot application.
 */
@SpringBootApplication()
public class CrawlerApplication extends BaseSpringBootApplication implements CommandLineRunner {

  public static final String PLUGGABLE_CRAWL_MANAGER = managerKey(PluggableCrawlManager.class);
  private static final Logger logger = LoggerFactory.getLogger(CrawlerApplication.class);
  public static ManagerDesc PLUGGABLE_CRAWL_MANAGER_DESC =
    new ManagerDesc(PLUGGABLE_CRAWL_MANAGER,
      "org.lockss.laaws.crawler.impl.PluggableCrawlManager");

  // Manager descriptors.  The order of this table determines the order in
  // which managers are initialized and started.
  private static final ManagerDesc[] myManagerDescs = {
    ACCOUNT_MANAGER_DESC,
      CONFIG_DB_MANAGER_DESC,
    // start plugin manager after generic services
    PLUGIN_MANAGER_DESC,
    STATE_MANAGER_DESC,
    CRAWL_MANAGER_DESC,
    PLUGGABLE_CRAWL_MANAGER_DESC,
    REPOSITORY_MANAGER_DESC,
    SERVLET_MANAGER_DESC,
    PLATFORM_CONFIG_STATUS_DESC,
    CONFIG_STATUS_DESC,
    ARCHIVAL_UNIT_STATUS_DESC,
    OVERVIEW_STATUS_DESC
  };

  /**
   * The entry point of the application.
   *
   * @param args A String[] with the command line arguments.
   */
  public static void main(String[] args) {
    logger.info("Starting the application");
    configure();

    // Start the REST service.
    SpringApplication.run(CrawlerApplication.class, args);
  }

  /**
   * Callback used to run the application starting the LOCKSS daemon.
   *
   * @param args A String[] with the command line arguments.
   */
  public void run(String... args) {
    // Check whether there are command line arguments available.
    if (args != null && args.length > 0) {
      // Yes: Start the LOCKSS daemon.
      logger.info("Starting the LOCKSS Crawler Service");
      try {
        AppSpec spec = new AppSpec()
          .setService(ServiceDescr.SVC_CRAWLER)
          .setName("Crawler Service")
          .setArgs(args)
          .setSpringApplicatonContext(getApplicationContext())
          .setAppManagers(myManagerDescs)
          .addAppConfig(CrawlManagerImpl.PARAM_ENABLE_JMS_SEND,"true")
          .addAppConfig(PARAM_START_PLUGINS, "true")
          .addAppConfig(PluginManager.PARAM_START_ALL_AUS, "true");
        logger.info("Calling LockssApp.startStatic...");
        LockssApp.startStatic(LockssDaemon.class, spec);
      }
      catch(Exception ex) {
        logger.error("LockssApp.startStatic failed: ", ex);
      }
    }
    else {
      logger.info("No args provided, daemon not started.");
      // No: Do nothing. This happens when a test is started and before the
      // test setup has got a chance to inject the appropriate command line
      // parameters.
    }
  }
}
