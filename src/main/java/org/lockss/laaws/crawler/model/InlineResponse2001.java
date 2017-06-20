package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.lockss.laaws.crawler.model.Crawl;
import javax.validation.constraints.*;
/**
 * A list of jobs within the ranges given in the query and page info.
 */
@ApiModel(description = "A list of jobs within the ranges given in the query and page info.")

public class InlineResponse2001   {
  @JsonProperty("crawl")
  private Crawl crawl = null;

  public InlineResponse2001 crawl(Crawl crawl) {
    this.crawl = crawl;
    return this;
  }

   /**
   * Get crawl
   * @return crawl
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public Crawl getCrawl() {
    return crawl;
  }

  public void setCrawl(Crawl crawl) {
    this.crawl = crawl;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InlineResponse2001 inlineResponse2001 = (InlineResponse2001) o;
    return Objects.equals(this.crawl, inlineResponse2001.crawl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(crawl);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InlineResponse2001 {\n");
    
    sb.append("    crawl: ").append(toIndentedString(crawl)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

