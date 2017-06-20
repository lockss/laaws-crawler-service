package org.lockss.laaws.crawler.model;

import java.util.Objects;
import io.swagger.annotations.ApiModel;
import com.fasterxml.jackson.annotation.JsonValue;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The kind of crawl being performed.  For now this is either new content or repair.
 */
public enum CrawlKind {
  
  NEWCONTENT("newContent"),
  
  REPAIR("repair");

  private String value;

  CrawlKind(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static CrawlKind fromValue(String text) {
    for (CrawlKind b : CrawlKind.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

