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
*
*/

package org.lockss.laaws.crawler.wget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawler;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.util.Constants;
import org.lockss.util.NumberUtil;
import org.lockss.util.StringUtil;

import static org.lockss.laaws.crawler.wget.WgetCommandOptions.NO_WARC_COMPRESSION_KEY;

/**
 * The type Wget cmd line crawler.
 */
public class WgetCmdLineCrawler extends CmdLineCrawler {
  /**
   * The constant ATTR_SUCCESS_CODE.
   */
public static final String ATTR_SUCCESS_CODE = "successCode";
  /**
   * The constant ATTR_OUTPUT_LEVEL.
   */
public static final String ATTR_OUTPUT_LEVEL ="outputLevel";
  /**
   * The Success codes.
   */
List<Integer>  successCodes;
  /** The Output level. */
  String outputLevel;

  List<String> configOptions;

  @Override
  public void updateCrawlerConfig(CrawlerConfig crawlerConfig) {
    super.updateCrawlerConfig(crawlerConfig);
    Map<String, String> attrs= crawlerConfig.getAttributes();
    setCmdLineBuilder(new WgetCommandLineBuilder(this));
    // setup the wget defaults from the attributes
    configOptions = new ArrayList<>();
    String level = attrs.get(ATTR_OUTPUT_LEVEL);
    if (level != null) {
      setOutputLevel(level);
      configOptions.add(getOutputLevel());
    }
    setSuccessCodes(attrs.get(ATTR_SUCCESS_CODE));
    if(!compressWarc) {
      configOptions.add(NO_WARC_COMPRESSION_KEY);
    }
    // the remainder of the wget parameters are --foo
    for (String attr : attrs.keySet()) {
      if(attr.startsWith("opt.")) {
        String opt = attr.replace("opt.","--");
        String val = attrs.get(attr);
        configOptions.add(opt + "=" + val);
     }
    }
  }

  public long getMaxRetries() {
    return pcManager.getMaxRetries();
  }

  public double getRetryDelay() {
    return NumberUtil.roundToNDecimals(((double) pcManager.getRetryDelay()) / Constants.SECOND,2);
  }

  public double getConnectTimeout() {
    return NumberUtil.roundToNDecimals( ((double)pcManager.getConnectTimeout())/Constants.SECOND,2);
  }

  public double getReadTimeout() {
    return NumberUtil.roundToNDecimals( ((double)pcManager.getReadTimeout())/Constants.SECOND,2);
  }

  public double getFetchDelay() {
    return NumberUtil.roundToNDecimals(((double)pcManager.getFetchDelay())/Constants.SECOND,2);
  }


  @Override
  protected boolean didCrawlSucceed(int exitCode) {
    return successCodes.contains(exitCode);
  }

  /**
   * Gets output level.
   *
   * @return the output level
   */
  String getOutputLevel() {
    return outputLevel;
  }

  /**
   * Sets output level.
   *
   * @param level the level
   */
  void setOutputLevel(String level) {

    if (level != null) {
      switch (level) {
        case "debug":
          outputLevel = "--debug";
          break;
        case "no-verbose":
          outputLevel = "--no-verbose";
          break;
        case "quiet":
          outputLevel = "--quiet";
          break;
        default:
          outputLevel = "--verbose";
      }
    }
  }

  /**
   * Return a list exitCodes used to indicate crawl success.
   *
   * @return the success codes
   */
  List<Integer> getSuccessCodes() {
    return successCodes;
  }

  /**
   * Set the list of exitCodes used to indicate crawl success.
   *
   * @param str the '; delimited list of successful exit codes'
   */
  void setSuccessCodes(String str) {
    if(StringUtil.isNullString(str)) {
      successCodes = new ArrayList<>();
      successCodes.add(0);
    }
    else {
      successCodes = Arrays.stream(str.split(";")).map(Integer::valueOf)
        .collect(Collectors.toList());
    }
  }

  public List<String> getConfigOptions() {
    return configOptions;
  }
}