/*

Copyright (c) 2000-2020 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software without
specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.lockss.laaws.crawler.wget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.lockss.app.LockssDaemon;
import org.lockss.log.L4JLogger;
import org.lockss.test.LockssTestCase4;
import org.lockss.util.ListUtil;
import org.lockss.util.StringUtil;
import org.lockss.util.rest.crawler.CrawlDesc;

import static org.lockss.laaws.crawler.wget.WgetCommandLineBuilder.WARC_FILE_NAME;
import static org.lockss.laaws.crawler.wget.WgetCommandOptions.*;

/** Test class for WgetCommandLineBuilder. */
public class TestWgetCommandLineBuilder extends LockssTestCase4 {
  private static final L4JLogger log = L4JLogger.getLogger();
  private static final String userAgent = "--user-agent=\""+LockssDaemon.getUserAgent()+"\"";

  private File tmpDir;
  List<String> crawlList = ListUtil.list("http://url1", "https://Url2", "http://URL3");

  String tmpDirPath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tmpDir = getTempDir("TestWgetCommandLineBuilder");
    log.info("tmpDir = {}", tmpDir);
    tmpDirPath=tmpDir.getAbsolutePath();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  /**
   * Tests the buildCommandLine() method.
   *
   * @throws Exception if there are input/output problems.
   */
  @Test
  public void testBuildCommandLineRepair() throws Exception {
    List<String> command = null;
    CrawlDesc crawlDesc = new CrawlDesc();
    crawlDesc.crawlKind(CrawlDesc.CrawlKindEnum.REPAIR);
    List<String> expectedCommand = ListUtil.list("wget");
    expectedCommand.add("--directory-prefix=\""+tmpDirPath+ "\"");
    expectedCommand.addAll(WgetCommandLineBuilder.DEFAULT_CONFIG);
    expectedCommand.add(WARC_FILE_KEY + "=" + tmpDirPath + "/" + WARC_FILE_NAME);
    expectedCommand.add(WARC_TEMPDIR_KEY + "=" + tmpDirPath);
    expectedCommand.addAll(crawlList);

    try {
      command = new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // Expected
    }

    crawlDesc.crawlList(crawlList);

    command = new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);

    assertEquals(expectedCommand.size(), command.size());
    assertIsomorphic(expectedCommand, command);
  }
  @Test
  public void testBuildCommandLineNewContent() throws Exception {
    List<String> command = null;
    CrawlDesc crawlDesc = new CrawlDesc();
    crawlDesc.crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.crawlList(crawlList);

    List<String> expectedCommand = ListUtil.list("wget");
    expectedCommand.add("--directory-prefix=\""+tmpDirPath+ "\"");
    expectedCommand.add("-r");
    expectedCommand.addAll(WgetCommandLineBuilder.DEFAULT_CONFIG);
    expectedCommand.add(WARC_FILE_KEY + "=" + tmpDirPath + "/" + WARC_FILE_NAME);
    expectedCommand.add(WARC_TEMPDIR_KEY + "=" + tmpDirPath);
    expectedCommand.addAll(crawlList);

    command = new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);

    assertEquals(expectedCommand.size(), command.size());
    assertIsomorphic(expectedCommand, command);
  }
  @Test
  public void testBuildCommandWithExtraData() throws Exception {
    List<String> command = null;
    CrawlDesc crawlDesc = new CrawlDesc();
    crawlDesc.crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.crawlList(crawlList);
    List<String> inputFileUrls = ListUtil.list("https://ip1","https://ip2","http://ip3");

    List<String> expectedCommand = ListUtil.list("wget");
    expectedCommand.add("--directory-prefix=\""+tmpDirPath+ "\"");
    expectedCommand.add("-r");
    expectedCommand.addAll(WgetCommandLineBuilder.DEFAULT_CONFIG);
    expectedCommand.add(WARC_FILE_KEY + "=" + tmpDirPath + "/" + WARC_FILE_NAME);
    expectedCommand.add(WARC_TEMPDIR_KEY + "=" + tmpDirPath);
    expectedCommand.add(INPUT_FILE_KEY + "=" + tmpDirPath + "/" + INPUT_FILE_KEY.substring(2));
    expectedCommand.add(userAgent);
    expectedCommand.addAll(crawlList);

    Map<String, Object> extraCrawlerMap = new HashMap<>();
    extraCrawlerMap.put(INPUT_FILE_KEY.substring(2), inputFileUrls);
    log.info("extraCrawlerData = {}", extraCrawlerMap);
    crawlDesc.extraCrawlerData(extraCrawlerMap);

    command = new WgetCommandLineBuilder().buildCommandLine(crawlDesc, tmpDir);

    log.info("command = {}", command);

    assertEquals(expectedCommand.size(), command.size());
    assertIsomorphic(expectedCommand, command);

    validateFileOption(INPUT_FILE_KEY, inputFileUrls, command);

    String userAgentOption = findOption(USER_AGENT_KEY, command);
    assertEquals(userAgent, userAgentOption);
  }

  /**
   * Validates an option that involves a file.
   *
   * @param optionKey A String with the long key of the option.
   * @param fileLines A List<String> with the expected lines in the file.
   * @param command A List<String> with the command line.
   * @throws IOException if there are input/output problems.
   */
  private void validateFileOption(String optionKey, List<String> fileLines, List<String> command)
      throws IOException {
    // Find the option in the command line.
    String commandOption = findOption(optionKey, command);

    // Validate the format of the option.
    String[] inputFileTokens = commandOption.split("=");
    assertEquals(2, inputFileTokens.length);
    assertEquals(optionKey, inputFileTokens[0]);

    // Compare the expected contents with the actual contents from the file.
    assertEquals(
        StringUtil.separatedString(fileLines, System.lineSeparator()) + System.lineSeparator(),
        StringUtil.fromFile(inputFileTokens[1]));
  }

  /**
   * Provides an option in a command line.
   *
   * @param optionKey A String with the long key of the option.
   * @param command A List<String> with the command line.
   * @return a String with the option.
   */
  private String findOption(String optionKey, List<String> command) {
    String result = null;

    // Loop through all the elements in the command line.
    for (String option : command) {
      // Check whether this element corresponds to the sought option.
      if (option.startsWith(optionKey)) {
        // Yes: return it.
        result = option;
        break;
      }
    }

    assertNotNull(result);
    return result;
  }
}
