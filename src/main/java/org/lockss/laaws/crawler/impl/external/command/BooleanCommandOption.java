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
package org.lockss.laaws.crawler.impl.external.command;

import java.util.List;
import org.lockss.log.L4JLogger;

/** Representation of a boolean command line option. */
public class BooleanCommandOption extends CommandOption {
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Constructor.
   *
   * @param longKey A String with the long key of the boolean command line option.
   */
  public BooleanCommandOption(String longKey) {
    super(longKey);
  }

  /**
   * Processes a boolean command line option.
   *
   * @param optionKey A String with the key of the option.
   * @param jsonObject An object with the JSON object that represents the value of the command line
   *     option.
   * @param command A List<String> where to add this command line option, if appropriate.
   * @return a BooleanCommandOption with this object.
   */
  public static BooleanCommandOption process(
      String optionKey, Object jsonObject, List<String> command) {
    log.debug2("optionKey = {}", optionKey);
    log.debug2("jsonObject = {}", jsonObject);
    log.debug2("command = {}", command);

    // Create the object to be returned.
    BooleanCommandOption option = new BooleanCommandOption(optionKey);

    Boolean interpretedValue = null;

    // Check whether this option was specified at all.
    if (jsonObject != null) {
      // Yes: Interpret it as a boolean.
      interpretedValue = (Boolean) jsonObject;
      log.trace("interpretedValue = {}", interpretedValue);

      String optionValue = option.setValue(interpretedValue.toString());
      log.trace("optionValue = {}", optionValue);

      // Check whether the option value is specified as true.
      if (interpretedValue) {
        // Yes: Add it to the command line.
        command.add(option.getLongKey());
      } else {
        // No: Add the opposite option to the command line.
        command.add(option.getOppositeLongKey());
      }

      log.trace("command = {}", command);
    }

    log.debug2("option = {}", option);
    return option;
  }

  /**
   * Provides the long key of the boolean opposite option to this option.
   *
   * @return a String with the long key of the boolean opposite option to this option.
   */
  private String getOppositeLongKey() {
    if (getLongKey().startsWith("--no-")) {
      return "-" + getLongKey().substring(3);
    } else {
      return "--no" + getLongKey().substring(1);
    }
  }
}
