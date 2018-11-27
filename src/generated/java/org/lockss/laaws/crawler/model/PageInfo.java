package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * The information related to pagination of content
 */
@ApiModel(description = "The information related to pagination of content")
@Validated

public class PageInfo   {
  @JsonProperty("totalCount")
  private Integer totalCount = null;

  @JsonProperty("resultsPerPage")
  private Integer resultsPerPage = null;

  @JsonProperty("continuationToken")
  private String continuationToken = null;

  @JsonProperty("curLink")
  private String curLink = null;

  @JsonProperty("nextLink")
  private String nextLink = null;

  public PageInfo totalCount(Integer totalCount) {
    this.totalCount = totalCount;
    return this;
  }

  /**
   * The total number of elements to be paginated
   * @return totalCount
  **/
  @ApiModelProperty(required = true, value = "The total number of elements to be paginated")
  @NotNull


  public Integer getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Integer totalCount) {
    this.totalCount = totalCount;
  }

  public PageInfo resultsPerPage(Integer resultsPerPage) {
    this.resultsPerPage = resultsPerPage;
    return this;
  }

  /**
   * The number of results per page
   * @return resultsPerPage
  **/
  @ApiModelProperty(required = true, value = "The number of results per page")
  @NotNull


  public Integer getResultsPerPage() {
    return resultsPerPage;
  }

  public void setResultsPerPage(Integer resultsPerPage) {
    this.resultsPerPage = resultsPerPage;
  }

  public PageInfo continuationToken(String continuationToken) {
    this.continuationToken = continuationToken;
    return this;
  }

  /**
   * The continuation token
   * @return continuationToken
  **/
  @ApiModelProperty(required = true, value = "The continuation token")
  @NotNull


  public String getContinuationToken() {
    return continuationToken;
  }

  public void setContinuationToken(String continuationToken) {
    this.continuationToken = continuationToken;
  }

  public PageInfo curLink(String curLink) {
    this.curLink = curLink;
    return this;
  }

  /**
   * The link to the current page
   * @return curLink
  **/
  @ApiModelProperty(required = true, value = "The link to the current page")
  @NotNull


  public String getCurLink() {
    return curLink;
  }

  public void setCurLink(String curLink) {
    this.curLink = curLink;
  }

  public PageInfo nextLink(String nextLink) {
    this.nextLink = nextLink;
    return this;
  }

  /**
   * The link to the next page
   * @return nextLink
  **/
  @ApiModelProperty(value = "The link to the next page")


  public String getNextLink() {
    return nextLink;
  }

  public void setNextLink(String nextLink) {
    this.nextLink = nextLink;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PageInfo pageInfo = (PageInfo) o;
    return Objects.equals(this.totalCount, pageInfo.totalCount) &&
        Objects.equals(this.resultsPerPage, pageInfo.resultsPerPage) &&
        Objects.equals(this.continuationToken, pageInfo.continuationToken) &&
        Objects.equals(this.curLink, pageInfo.curLink) &&
        Objects.equals(this.nextLink, pageInfo.nextLink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalCount, resultsPerPage, continuationToken, curLink, nextLink);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PageInfo {\n");
    
    sb.append("    totalCount: ").append(toIndentedString(totalCount)).append("\n");
    sb.append("    resultsPerPage: ").append(toIndentedString(resultsPerPage)).append("\n");
    sb.append("    continuationToken: ").append(toIndentedString(continuationToken)).append("\n");
    sb.append("    curLink: ").append(toIndentedString(curLink)).append("\n");
    sb.append("    nextLink: ").append(toIndentedString(nextLink)).append("\n");
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

