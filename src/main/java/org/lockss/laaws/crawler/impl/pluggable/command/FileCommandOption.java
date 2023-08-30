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
package org.lockss.laaws.crawler.impl.pluggable.command;

import org.lockss.log.L4JLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Representation of a file command line option.
 */
public class FileCommandOption extends CommandOption {
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Constructor.
   *
   * @param longKey A String with the long key of the file command line option.
   */
  public FileCommandOption(String longKey) {
    super(longKey);
  }

  /**
   * Processes a file command line option.
   *
   * @param optionKey  A String with the key of the option.
   * @param jsonObject An object with the JSON object that represents the value of the command line
   *                   option.
   * @param tmpDir     A File with the temporary directory where to create files referenced by this
   *                   command line option.
   * @param command    A List<String> where to add this command line option, if appropriate.
   * @throws IOException if there are problems processing this command line.
   */
  public static void process(String optionKey, Object jsonObject, File tmpDir, List<String> command)
    throws IOException {
    log.debug2("optionKey = {}", optionKey);
    log.debug2("jsonObject = {}", jsonObject);
    log.debug2("tmpDir = {}", tmpDir);
    log.debug2("command = {}", command);

    // Create the object to be returned.
    FileCommandOption option = new FileCommandOption(optionKey);

    List<String> interpretedValue = null;

    // Check whether this option was specified at all.
    if (jsonObject != null) {
      // Yes: Interpret it as a list of strings with the content of a file.
      interpretedValue = (List<String>) jsonObject;
      log.trace("interpretedValue = {}", interpretedValue);

      // Check whether it is not an empty list.
      if (!interpretedValue.isEmpty()) {
        // Yes: Create a file in the temporary directory to store the passed
        // Contents.
        File tmpFile = new File(tmpDir, optionKey.substring(2));
        log.trace("tmpFile = {}", tmpFile);

        // Populate the file.
        Files.write(tmpFile.toPath(), interpretedValue, StandardCharsets.UTF_8);

        // The absolute path of the file is the value of the option.
        String optionValue = option.setValue(tmpFile.getAbsolutePath());
        log.trace("optionValue = {}", optionValue);

        // Add it to the command line.
        if (optionValue != null && !optionValue.isEmpty()) {
          command.add(option.getLongKey() + "=" + optionValue);
          log.trace("command = {}", command);
        }
      }
    }
    log.debug2("option = {}", option);
  }
}
