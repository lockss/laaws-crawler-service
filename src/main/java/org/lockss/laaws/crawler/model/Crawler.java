package org.lockss.laaws.crawler.model;

import java.util.Objects;
import io.swagger.annotations.ApiModel;
import com.fasterxml.jackson.annotation.JsonValue;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The name of the crawler to use
 */
public enum Crawler {
  
  LOCKSS("lockss"),
  
  CRAWLJAX("crawljax");

  private String value;

  Crawler(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static Crawler fromValue(String text) {
    for (Crawler b : Crawler.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

