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

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of wget command line options.
 */
public class WgetCommandOptions {
  /** Logging parameters */
  public static final String APPEND_LOG_KEY="--append-output";//=FILE  append messages to FILE.
  public static final String DEBUG_KEY= "--debug"; //print lots of debugging information.
  public static final String QUIET_KEY = "--quiet";//quiet (no output).
  public static final String VERBOSE_KEY="--verbose";//be verbose (this is the default).
  public static final String INPUT_FILE_KEY = "--input-file";

  /** Download parameters */
  public static final String TRIES_KEY= "--tries";//=NUMBER set number of retries to NUMBER (0 unlimits)."
  public static final String TIMEOUT_KEY = "--timeout";
  public static final String DNS_TIMEOUT_KEY = "--dns-timeout";//=SECS  set the DNS lookup timeout to SECS.
  public static final String CONNECT_TIMEOUT_KEY = "--connect-timeout";//=SECS set the connect timeout to SECS.
  public static final String READ_TIMEOUT_KEY = "--read-timeout"; //=SECS set the read timeout to SECS.
  public static final String SPIDER_KEY = "--spider";
  public static final String WAIT_KEY = "--wait";//wait SECONDS between retrievals.

  /** HTTP Options */
  public static final String HEADER_KEY = "--header";
  public static final String USER_AGENT_KEY = "--user-agent";

  /** Directories */
  public static final String NO_DIRECTORIES_KEY = "--no-directories";

  /** Recursive download parameters */
  public static final String RECURSIVE_KEY = "--recursive";
  public static final String LEVEL_KEY = "--level";
  public static final String DELETE_AFTER_KEY = "--delete-after";
  public static final String PAGE_REQUISITES_KEY = "--page-requisites";
  public static final String MIRROR_KEY = "--mirror";
  /** Recursive accept/reject options */
  public static final String ACCEPT_REGEX_KEY = "--accept-regex";
  public static final String REJECT_REGEX_KEY = "--reject-regex";
  public static final String DOMAINS_KEY = "--domains";
  public static final String SPAN_HOSTS_KEY = "--span-hosts";
  public static final String INCLUDE_DIRECTORIES_KEY = "--include-directories";//LIST
  public static final String EXCLUDE_DIRECTORIES_KEY = "--exclude-directories";//LIST
  public static final String NO_PARENT_KEY = "--no-parent";

  /** WARC parameter options */
  public static final String WARC_CDX_KEY = "--warc-cdx";
  public static final String WARC_DEDUP_KEY = "--warc-dedup";
  public static final String WARC_FILE_KEY = "--warc-file";
  public static final String WARC_HEADER_KEY = "--warc-header";
  public static final String WARC_MAX_SIZE_KEY = "--warc-max-size";
  public static final String WARC_TEMPDIR_KEY = "--warc-tempdir";
  public static final String NO_WARC_COMPRESSION_KEY = "--no-warc-compression";

  /**
   * The keys of all the supported wget command line options.
   */
  public static final List<String> ALL_KEYS =
    new ArrayList<String>() {
      {
        add(APPEND_LOG_KEY);
        add(DEBUG_KEY);
        add(QUIET_KEY);
        add(VERBOSE_KEY);
        add(TRIES_KEY);
        add(TIMEOUT_KEY);
        add(DNS_TIMEOUT_KEY);
        add(CONNECT_TIMEOUT_KEY);
        add(READ_TIMEOUT_KEY);
        add(ACCEPT_REGEX_KEY);
        add(DELETE_AFTER_KEY);
        add(DOMAINS_KEY);
        add(EXCLUDE_DIRECTORIES_KEY);
        add(HEADER_KEY);
        add(INCLUDE_DIRECTORIES_KEY);
        add(INPUT_FILE_KEY);
        add(LEVEL_KEY);
        add(NO_DIRECTORIES_KEY);
        add(NO_PARENT_KEY);
        add(PAGE_REQUISITES_KEY);
        add(RECURSIVE_KEY);
        add(MIRROR_KEY);
        add(REJECT_REGEX_KEY);
        add(SPAN_HOSTS_KEY);
        add(SPIDER_KEY);
        add(USER_AGENT_KEY);
        add(WAIT_KEY);
        add(WARC_CDX_KEY);
        add(WARC_DEDUP_KEY);
        add(WARC_FILE_KEY);
        add(WARC_HEADER_KEY);
        add(WARC_MAX_SIZE_KEY);
        add(NO_WARC_COMPRESSION_KEY);
      }
    };
}
