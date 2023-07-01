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

import static org.lockss.laaws.crawler.wget.WgetCommandOptions.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lockss.app.LockssDaemon;
import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawler;
import org.lockss.laaws.crawler.impl.pluggable.command.BooleanCommandOption;
import org.lockss.laaws.crawler.impl.pluggable.command.FileCommandOption;
import org.lockss.laaws.crawler.impl.pluggable.command.ListStringCommandOption;
import org.lockss.laaws.crawler.impl.pluggable.command.StringCommandOption;
import org.lockss.log.L4JLogger;
import org.lockss.util.FileUtil;
import org.lockss.util.ListUtil;
import org.lockss.util.rest.crawler.CrawlDesc;

/**
 * The builder of a wget command line.
 */
public class WgetCommandLineBuilder implements CmdLineCrawler.CommandLineBuilder {

  private static final L4JLogger log = L4JLogger.getLogger();

  protected static final String WARC_FILE_NAME = "lockss-wget";
  public static final List<String> DEFAULT_CONFIG =
      ListUtil.fromCSV(DELETE_AFTER_KEY);
  private final WgetCmdLineCrawler wgetCrawler;

  WgetCommandLineBuilder() {
    super();
    wgetCrawler = null;
  }

  WgetCommandLineBuilder(WgetCmdLineCrawler crawler) {
    wgetCrawler = crawler;
  }

  /**
   * Builds the wget command line.
   *
   * @param crawlDesc A CrawlDesc with the description of the crawl.
   * @param tmpDir    A File with the temporary directory where to create files referenced by command
   *                  line option.
   * @return a List<String> with the wget command line.
   * @throws IOException if there are problems building the wget command line.
   */
  public List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir) throws IOException {
    log.debug2("crawlDesc = {}", crawlDesc);
    log.debug2("tmpDir = {}", tmpDir);
    FileUtil.ensureDirExists(tmpDir);
    List<String> command = new ArrayList<>();
    command.add("wget");
    command.add("--directory-prefix=./");
    if(crawlDesc.getCrawlKind().equals(CrawlDesc.CrawlKindEnum.NEWCONTENT)){
      command.add("-r");
    }
    command.add(DELETE_AFTER_KEY);
    // add parameters from config
    if(wgetCrawler != null)
      command.addAll(wgetCrawler.getConfigOptions());

    Integer crawlDepth = crawlDesc.getCrawlDepth();
    log.trace("crawlDepth = {}", crawlDepth);

    if (crawlDepth != null && crawlDepth > 0) {
      StringCommandOption.process(LEVEL_KEY, crawlDepth.toString(), command);
    }

    // fixed output information
    //File warc = new File(tmpDir, WARC_FILE_NAME);
    command.add(WARC_FILE_KEY + "=" + WARC_FILE_NAME);
    command.add(WARC_TEMPDIR_KEY + "=" + tmpDir.getAbsolutePath());
    // add parameters from request.
    Map<String, Object> extraCrawlerDataMap = crawlDesc.getExtraCrawlerData();
    if (extraCrawlerDataMap != null) {
      log.trace("extraCrawlerDataMap = {}", extraCrawlerDataMap);
      for (String optionKey : ALL_KEYS) {
        log.trace("optionKey = {}", optionKey);

        Object extraCrawlerOptionData = extraCrawlerDataMap.get(optionKey.substring(2));
        log.trace("extraCrawlerOptionData = {}", extraCrawlerOptionData);

        switch (optionKey) {
          case DEBUG_KEY:
          case QUIET_KEY:
          case VERBOSE_KEY:
          case NO_PARENT_KEY:
          case DELETE_AFTER_KEY:
          case NO_DIRECTORIES_KEY:
          case PAGE_REQUISITES_KEY:
          case RECURSIVE_KEY:
          case SPAN_HOSTS_KEY:
          case SPIDER_KEY:
          case WARC_CDX_KEY:
          case NO_WARC_COMPRESSION_KEY:
          case MIRROR_KEY:
            BooleanCommandOption.process(optionKey, extraCrawlerOptionData, command);
            break;
          case DOMAINS_KEY:
          case EXCLUDE_DIRECTORIES_KEY:
          case INCLUDE_DIRECTORIES_KEY:
            ListStringCommandOption.process(optionKey, extraCrawlerOptionData, command);
            break;
          case ACCEPT_REGEX_KEY:
          case LEVEL_KEY:
          case REJECT_REGEX_KEY:
          case WAIT_KEY:
          case WARC_FILE_KEY:
          case WARC_MAX_SIZE_KEY:
          case TRIES_KEY:
          case TIMEOUT_KEY:
          case DNS_TIMEOUT_KEY:
          case CONNECT_TIMEOUT_KEY:
          case READ_TIMEOUT_KEY:
            StringCommandOption.process(optionKey, extraCrawlerOptionData, command);
            break;
          case USER_AGENT_KEY:
            StringCommandOption userAgentOption =
              StringCommandOption.process(optionKey, extraCrawlerOptionData, command);
            if (userAgentOption.getValue() == null) {
              String userAgent = LockssDaemon.getUserAgent();
              log.trace("userAgent = {}", userAgent);
              command.add(USER_AGENT_KEY+"=\""+userAgent+"\"");
            }
            break;
          case HEADER_KEY:
          case WARC_HEADER_KEY:
            Object jsonObject = extraCrawlerDataMap.get(optionKey.substring(2));
            log.trace("jsonObject = {}", jsonObject);

            if (jsonObject != null) {
              List<String> headers = (List<String>) jsonObject;
              log.trace("headers = {}", headers);

              if (!headers.isEmpty()) {
                for (String header : headers) {
                  log.trace("header = {}", header);

                  StringCommandOption.process(optionKey, header, command);
                }
              }
            }
            break;
          case INPUT_FILE_KEY:
          case WARC_DEDUP_KEY:
          case APPEND_LOG_KEY:
            FileCommandOption.process(optionKey, extraCrawlerOptionData, tmpDir, command);
            break;
          default:
            log.warn("Ignored unexpected option '{}'", optionKey);
        }
      }
    }
    // input information
    List<String> crawlList = crawlDesc.getCrawlList();
    log.trace("crawlList = {}", crawlList);

    if (crawlList != null && !crawlList.isEmpty()) {
      for (String crawlUrl : crawlList) {
        log.trace("crawlUrl = {}", crawlUrl);
        command.add(crawlUrl);
      }
    }
    else {
      if (!command.contains(INPUT_FILE_KEY)) {
        String message = "No URLs to crawl with wget were specified";
        log.error(message);
        throw new IllegalArgumentException(message);
      }
    }

    log.debug2("command = {}", command);
    return command;
  }
}
