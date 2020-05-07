package org.lockss.laaws.crawler.wget.command;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.lockss.log.L4JLogger;
import org.lockss.test.LockssTestCase4;
import org.lockss.util.PluginPackager;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.test.LockssTestCase5;

public class TestWgetCommandLineBuilder extends LockssTestCase4 {
  private static final L4JLogger log = L4JLogger.getLogger();

  private File tmpDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tmpDir = getTempDir("TestWgetCommandLineBuilder");
    log.info("tmpDir = {}", tmpDir);
  }

  @Test
  public void testWebRequestContinuationTokenConstructor() throws Exception {
    CrawlDesc crawlDesc = new CrawlDesc();
    List<String> command =
	new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);
    log.info("command = {}", command);
  }
}
