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
package org.lockss.laaws.crawler.wget.command;

import static org.lockss.laaws.crawler.wget.command.WgetCommandOption.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lockss.log.L4JLogger;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.status.ApiStatus;

/**
 * The builder of a wget command line.
 */
public class WgetCommandLineBuilder {
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Builds the wget command line.
   * 
   * @param crawlDesc A CrawlDesc with the description of the crawl.
   * @param tmpDir    A File with the temporary directory where to create files
   *                  referenced by command line option.
   * @return a List<String> with the wget command line.
   * @throws IOException if there are problems building the wget command line.
   */
  public List<String> buildCommandLine(CrawlDesc crawlDesc, File tmpDir)
      throws IOException {
    log.debug2("crawlDesc = {}", crawlDesc);
    log.debug2("tmpDir = {}", tmpDir);

    List<String> command = new ArrayList<>();
    command.add("wget");

    Integer crawlDepth = crawlDesc.getCrawlDepth();
    log.trace("crawlDepth = {}", crawlDepth);

    if (crawlDepth != null) {
      StringWgetCommandOption.process(LEVEL_KEY, crawlDepth.toString(),
	  command);
    }

    String extraCrawlerData = crawlDesc.getExtraCrawlerData();
    log.trace("extraCrawlerData = {}", extraCrawlerData);

    if (extraCrawlerData != null) {
      Map<String, Object> extraCrawlerDataMap =
	  new ObjectMapper().readValue(extraCrawlerData, HashMap.class);
      log.trace("extraCrawlerDataMap = {}", extraCrawlerDataMap);

      for (String optionKey : ALL_KEYS) {
	log.trace("optionKey = {}", optionKey);

	Object extraCrawlerOptionData =
	    extraCrawlerDataMap.get(optionKey.substring(2));
	log.trace("extraCrawlerOptionData = {}", extraCrawlerOptionData);

	switch (optionKey) {
	  case DELETE_AFTER_KEY:
	  case NO_DIRECTORIES_KEY:
	  case PAGE_REQUISITES_KEY:
	  case RECURSIVE_KEY:
	  case SPAN_HOSTS_KEY:
	  case SPIDER_KEY:
	  case WARC_CDX_KEY:
	    BooleanWgetCommandOption.process(optionKey, extraCrawlerOptionData,
		command);
	    break;
	  case DOMAINS_KEY:
	  case EXCLUDE_DIRECTORIES_KEY:
	  case INCLUDE_DIRECTORIES_KEY:
	    ListStringWgetCommandOption.process(optionKey,
		extraCrawlerOptionData, command);
	    break;
	  case ACCEPT_REGEX_KEY:
	  case LEVEL_KEY:
	  case REJECT_REGEX_KEY:
	  case WAIT_KEY:
	  case WARC_FILE_KEY:
	  case WARC_MAX_SIZE_KEY:
	    StringWgetCommandOption.process(optionKey, extraCrawlerOptionData,
		command);
	    break;
	  case USER_AGENT_KEY:
	    StringWgetCommandOption userAgentOption =
	    StringWgetCommandOption.process(optionKey, extraCrawlerOptionData,
		command);
	
	    if (userAgentOption.getValue() == null) {
	      ApiStatus apiStatus = new ApiStatus("swagger/swagger.yaml");
	      log.trace("apiStatus = {}", apiStatus);

	      String userAgent = apiStatus.getServiceName() + " "
		  + apiStatus.getComponentVersion();
	      log.trace("userAgent = {}", userAgent);

	      StringWgetCommandOption.process(optionKey, userAgent, command);
	    }

	    break;
	  case HEADER_KEY:
	  case WARC_HEADER_KEY:
	    Object jsonObject =
		extraCrawlerDataMap.get(optionKey.substring(2));
	    log.trace("jsonObject = {}", jsonObject);

	    if (jsonObject != null) {
	      List<String> headers = (List<String>)jsonObject;
	      log.trace("headers = {}", headers);

	      if (!headers.isEmpty()) {
		for (String header : headers) {
		  log.trace("header = {}", header);

		  StringWgetCommandOption.process(optionKey, header, command);
		}
	      }
	    }

	    break;
	  case WARC_DEDUP_KEY:
	    FileWgetCommandOption.process(optionKey, extraCrawlerOptionData,
		tmpDir, command);
	    break;
	  default:
	    log.warn("Ignored unexpected option '{}'", optionKey);
	}
      }
    }

    List<String> crawlList = crawlDesc.getCrawlList();
    log.trace("crawlList = {}", crawlList);

    if (crawlList != null) {
      for (String crawlUrl : crawlList) {
	log.trace("crawlUrl = {}", crawlUrl);
	command.add(crawlUrl);
      }
    }

    log.debug2("command = {}", command);
    return command;
  }
}
