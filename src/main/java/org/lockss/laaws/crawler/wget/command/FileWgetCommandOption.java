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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.lockss.log.L4JLogger;

/**
 * Representation of a wget file command line option.
 */
public class FileWgetCommandOption extends WgetCommandOption {
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Constructor.
   * 
   * @param longKey A String with the long key of the wget boolean command line
   *                option.
   */
  public FileWgetCommandOption(String longKey) {
    super(longKey);
  }

  /**
   * Processes a wget file command line option.
   * 
   * @param optionKey  A String with the key of the option.
   * @param jsonObject An object with the JSON object that represents the value
   *                   of the command line option.
   * @param tmpDir     A File with the temporary directory where to create files
   *                   referenced by this command line option.
   * @param command    A List<String> where to add this command line option, if
   *                   appropriate.
   * @return a BooleanWgetCommandOption with this object.
   * @throws IOException if there are problems processing this wget command
   *                     line.
   */
  public static FileWgetCommandOption process(String optionKey,
      Object jsonObject, File tmpDir, List<String> command) throws IOException {
    log.debug2("optionKey = {}", optionKey);
    log.debug2("jsonObject = {}", jsonObject);
    log.debug2("tmpDir = {}", tmpDir);
    log.debug2("command = {}", command);

    FileWgetCommandOption option = new FileWgetCommandOption(optionKey);

    List<String> interpretedValue = null;

    if (jsonObject != null) {
      interpretedValue = (List<String>)jsonObject;
      log.trace("interpretedValue = {}", interpretedValue);

      if (!interpretedValue.isEmpty()) {
	File tmpFile = new File(tmpDir, optionKey.substring(2));
	log.trace("tmpFile = {}", tmpFile);

	Files.write(tmpFile.toPath(), interpretedValue, StandardCharsets.UTF_8);

	String optionValue = option.setValue(tmpFile.getAbsolutePath());
	log.trace("optionValue = {}", optionValue);

	if (optionValue != null && !optionValue.isEmpty()) {
	  command.add(option.getLongKey() + "=" + optionValue);
	  log.trace("command = {}", command);
	}
      }
    }

    log.debug2("option = {}", option);
    return option;
  }
}
