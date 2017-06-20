package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.lockss.laaws.crawler.model.Crawl;
import org.lockss.laaws.crawler.model.PageInfo;
import javax.validation.constraints.*;
/**
 * A list of jobs within the ranges given in the query and page info.
 */
@ApiModel(description = "A list of jobs within the ranges given in the query and page info.")

public class InlineResponse200   {
  @JsonProperty("crawls")
  private List<Crawl> crawls = new ArrayList<Crawl>();

  @JsonProperty("pageInfo")
  private PageInfo pageInfo = null;

  public InlineResponse200 crawls(List<Crawl> crawls) {
    this.crawls = crawls;
    return this;
  }

  public InlineResponse200 addCrawlsItem(Crawl crawlsItem) {
    this.crawls.add(crawlsItem);
    return this;
  }

   /**
   * An array of crawls
   * @return crawls
  **/
  @ApiModelProperty(value = "An array of crawls")
  public List<Crawl> getCrawls() {
    return crawls;
  }

  public void setCrawls(List<Crawl> crawls) {
    this.crawls = crawls;
  }

  public InlineResponse200 pageInfo(PageInfo pageInfo) {
    this.pageInfo = pageInfo;
    return this;
  }

   /**
   * Get pageInfo
   * @return pageInfo
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public PageInfo getPageInfo() {
    return pageInfo;
  }

  public void setPageInfo(PageInfo pageInfo) {
    this.pageInfo = pageInfo;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InlineResponse200 inlineResponse200 = (InlineResponse200) o;
    return Objects.equals(this.crawls, inlineResponse200.crawls) &&
        Objects.equals(this.pageInfo, inlineResponse200.pageInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(crawls, pageInfo);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InlineResponse200 {\n");
    
    sb.append("    crawls: ").append(toIndentedString(crawls)).append("\n");
    sb.append("    pageInfo: ").append(toIndentedString(pageInfo)).append("\n");
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

