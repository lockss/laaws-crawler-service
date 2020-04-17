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

package org.lockss.laaws.crawler.utils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.lockss.log.L4JLogger;

public class ContinuationToken {

  private static final String SEPARATOR = ".";
  private static final L4JLogger log = L4JLogger.getLogger();
  private Long timestamp = null;
  private Long lastElement = null;

  public ContinuationToken(String requestedToken)
      throws IllegalArgumentException {
    log.debug2("requestedToken = {}", requestedToken);

    String errMsg = "Invalid continuation token '" + requestedToken + "'";

    if (requestedToken != null && !requestedToken.trim().isEmpty()) {
      try {
        List<Long> tokenItems = splitToken(requestedToken.trim());
        log.trace("tokenItems = {}", tokenItems);

        timestamp = tokenItems.get(0);
        lastElement = tokenItems.get(1);
        log.trace("this = {}", this.toString());
      } catch (Exception ex) {
        log.warn(errMsg, ex);
        throw new IllegalArgumentException(errMsg, ex);
      }
    }

    validateMembers();
  }

  public ContinuationToken(Long timestamp, Long lastElement) {
    this.timestamp = timestamp;
    this.lastElement = lastElement;
    this.validateMembers();
  }

  public List<Long> splitToken(String str) {
    String[] tokenArray = str.split("\\" + SEPARATOR);
    log.trace("tokenArray = {}", tokenArray);

    return Stream.of(tokenArray).map(num -> Long.parseLong(num.trim()))
        .collect(Collectors.toList());
  }

  public Long getTimestamp() {
    return this.timestamp;
  }

  public Long getLastElement() {
    return this.lastElement;
  }

  public String toToken() {
    return this.timestamp != null && this.lastElement != null ? this.timestamp + SEPARATOR
        + this.lastElement : null;
  }

  public String toString() {
    return "[ContinuationToken timestamp=" + this.timestamp + ", lastElement=" + this.lastElement
        + "]";
  }

  private void validateMembers() {
    String errMsg;
    if ((this.timestamp != null || this.lastElement == null) && (this.timestamp == null
        || this.lastElement != null)) {
      if (this.timestamp != null && this.timestamp < 0L) {
        errMsg = "Invalid member: timestamp = '" + this.timestamp + "'";
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }
      else if (this.lastElement != null && this.lastElement < 0L) {
        errMsg = "Invalid member: lastElement = '" + this.lastElement + "'";
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }
    }
    else {
      errMsg = "Invalid member combination: timestamp = '" + this.timestamp + "', lastElement = '"
          + this.lastElement + "'";
      log.warn(errMsg);
      throw new IllegalArgumentException(errMsg);
    }
  }
  /**
   * Return a valid list of token elements.
   */
  /*
  List<Long> getTokens(String tokenString) throws IllegalArgumentException {
    List<Long> tokens = Collections.EMPTY_LIST;
    String errMsg;
    if (tokenString != null) {
      try {
        tokens = splitToken(tokenString, SEPARATOR);
      }
      catch (Exception ex) {
        errMsg = "Invalid token string: " + tokenString;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }
      if (tokens.size() < 2) {
        errMsg = "Invalid token elements: " + tokenString;
        log.warn(errMsg);
        throw new IllegalArgumentException(errMsg);
      }
    }
    return tokens;
*/
}