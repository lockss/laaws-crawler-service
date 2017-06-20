package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.lockss.laaws.crawler.model.CrawlKind;
import org.lockss.laaws.crawler.model.Crawler;
import javax.validation.constraints.*;
/**
 * The minimum needed to perform a crawl.  NB.  This will not follow links off-site nor will it allow a crawl where permissions are not available.
 */
@ApiModel(description = "The minimum needed to perform a crawl.  NB.  This will not follow links off-site nor will it allow a crawl where permissions are not available.")

public class GenericCrawlDesc   {
  @JsonProperty("crawlKind")
  private CrawlKind crawlKind = null;

  @JsonProperty("crawler")
  private Crawler crawler = null;

  @JsonProperty("crawlList")
  private List<String> crawlList = new ArrayList<String>();

  @JsonProperty("crawlDepth")
  private Integer crawlDepth = null;

  @JsonProperty("tags")
  private List<String> tags = new ArrayList<String>();

  @JsonProperty("includeRegex")
  private List<String> includeRegex = new ArrayList<String>();

  @JsonProperty("excludeRegex")
  private List<String> excludeRegex = new ArrayList<String>();

  public GenericCrawlDesc crawlKind(CrawlKind crawlKind) {
    this.crawlKind = crawlKind;
    return this;
  }

   /**
   * Get crawlKind
   * @return crawlKind
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public CrawlKind getCrawlKind() {
    return crawlKind;
  }

  public void setCrawlKind(CrawlKind crawlKind) {
    this.crawlKind = crawlKind;
  }

  public GenericCrawlDesc crawler(Crawler crawler) {
    this.crawler = crawler;
    return this;
  }

   /**
   * Get crawler
   * @return crawler
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public Crawler getCrawler() {
    return crawler;
  }

  public void setCrawler(Crawler crawler) {
    this.crawler = crawler;
  }

  public GenericCrawlDesc crawlList(List<String> crawlList) {
    this.crawlList = crawlList;
    return this;
  }

  public GenericCrawlDesc addCrawlListItem(String crawlListItem) {
    this.crawlList.add(crawlListItem);
    return this;
  }

   /**
   * The list of urls to crawl.
   * @return crawlList
  **/
  @ApiModelProperty(required = true, value = "The list of urls to crawl.")
  @NotNull
  public List<String> getCrawlList() {
    return crawlList;
  }

  public void setCrawlList(List<String> crawlList) {
    this.crawlList = crawlList;
  }

  public GenericCrawlDesc crawlDepth(Integer crawlDepth) {
    this.crawlDepth = crawlDepth;
    return this;
  }

   /**
   * The depth of the crawl
   * @return crawlDepth
  **/
  @ApiModelProperty(required = true, value = "The depth of the crawl")
  @NotNull
  public Integer getCrawlDepth() {
    return crawlDepth;
  }

  public void setCrawlDepth(Integer crawlDepth) {
    this.crawlDepth = crawlDepth;
  }

  public GenericCrawlDesc tags(List<String> tags) {
    this.tags = tags;
    return this;
  }

  public GenericCrawlDesc addTagsItem(String tagsItem) {
    this.tags.add(tagsItem);
    return this;
  }

   /**
   * The tags to include if different from default  anchor tags
   * @return tags
  **/
  @ApiModelProperty(value = "The tags to include if different from default  anchor tags")
  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public GenericCrawlDesc includeRegex(List<String> includeRegex) {
    this.includeRegex = includeRegex;
    return this;
  }

  public GenericCrawlDesc addIncludeRegexItem(String includeRegexItem) {
    this.includeRegex.add(includeRegexItem);
    return this;
  }

   /**
   * A list of regex to include if not in the exclude list.
   * @return includeRegex
  **/
  @ApiModelProperty(value = "A list of regex to include if not in the exclude list.")
  public List<String> getIncludeRegex() {
    return includeRegex;
  }

  public void setIncludeRegex(List<String> includeRegex) {
    this.includeRegex = includeRegex;
  }

  public GenericCrawlDesc excludeRegex(List<String> excludeRegex) {
    this.excludeRegex = excludeRegex;
    return this;
  }

  public GenericCrawlDesc addExcludeRegexItem(String excludeRegexItem) {
    this.excludeRegex.add(excludeRegexItem);
    return this;
  }

   /**
   * A list of regex to exclude
   * @return excludeRegex
  **/
  @ApiModelProperty(value = "A list of regex to exclude")
  public List<String> getExcludeRegex() {
    return excludeRegex;
  }

  public void setExcludeRegex(List<String> excludeRegex) {
    this.excludeRegex = excludeRegex;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenericCrawlDesc genericCrawlDesc = (GenericCrawlDesc) o;
    return Objects.equals(this.crawlKind, genericCrawlDesc.crawlKind) &&
        Objects.equals(this.crawler, genericCrawlDesc.crawler) &&
        Objects.equals(this.crawlList, genericCrawlDesc.crawlList) &&
        Objects.equals(this.crawlDepth, genericCrawlDesc.crawlDepth) &&
        Objects.equals(this.tags, genericCrawlDesc.tags) &&
        Objects.equals(this.includeRegex, genericCrawlDesc.includeRegex) &&
        Objects.equals(this.excludeRegex, genericCrawlDesc.excludeRegex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(crawlKind, crawler, crawlList, crawlDepth, tags, includeRegex, excludeRegex);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GenericCrawlDesc {\n");
    
    sb.append("    crawlKind: ").append(toIndentedString(crawlKind)).append("\n");
    sb.append("    crawler: ").append(toIndentedString(crawler)).append("\n");
    sb.append("    crawlList: ").append(toIndentedString(crawlList)).append("\n");
    sb.append("    crawlDepth: ").append(toIndentedString(crawlDepth)).append("\n");
    sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
    sb.append("    includeRegex: ").append(toIndentedString(includeRegex)).append("\n");
    sb.append("    excludeRegex: ").append(toIndentedString(excludeRegex)).append("\n");
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

