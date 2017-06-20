package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.math.BigDecimal;
import javax.validation.constraints.*;
/**
 * The information needed to page in long list of data
 */
@ApiModel(description = "The information needed to page in long list of data")

public class PageInfo   {
  @JsonProperty("totalCount")
  private Integer totalCount = null;

  @JsonProperty("resultsPerPage")
  private Integer resultsPerPage = null;

  @JsonProperty("current Page")
  private BigDecimal currentPage = null;

  @JsonProperty("curLink")
  private String curLink = null;

  @JsonProperty("nextLink")
  private String nextLink = null;

  public PageInfo totalCount(Integer totalCount) {
    this.totalCount = totalCount;
    return this;
  }

   /**
   * The total number of objects found.
   * @return totalCount
  **/
  @ApiModelProperty(example = "150", required = true, value = "The total number of objects found.")
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
   * The number of results per page.
   * @return resultsPerPage
  **/
  @ApiModelProperty(example = "20", required = true, value = "The number of results per page.")
  @NotNull
  public Integer getResultsPerPage() {
    return resultsPerPage;
  }

  public void setResultsPerPage(Integer resultsPerPage) {
    this.resultsPerPage = resultsPerPage;
  }

  public PageInfo currentPage(BigDecimal currentPage) {
    this.currentPage = currentPage;
    return this;
  }

   /**
   * The current page number
   * @return currentPage
  **/
  @ApiModelProperty(example = "2.0", required = true, value = "The current page number")
  @NotNull
  public BigDecimal getCurrentPage() {
    return currentPage;
  }

  public void setCurrentPage(BigDecimal currentPage) {
    this.currentPage = currentPage;
  }

  public PageInfo curLink(String curLink) {
    this.curLink = curLink;
    return this;
  }

   /**
   * The link to the current page
   * @return curLink
  **/
  @ApiModelProperty(value = "The link to the current page")
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
        Objects.equals(this.currentPage, pageInfo.currentPage) &&
        Objects.equals(this.curLink, pageInfo.curLink) &&
        Objects.equals(this.nextLink, pageInfo.nextLink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalCount, resultsPerPage, currentPage, curLink, nextLink);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PageInfo {\n");
    
    sb.append("    totalCount: ").append(toIndentedString(totalCount)).append("\n");
    sb.append("    resultsPerPage: ").append(toIndentedString(resultsPerPage)).append("\n");
    sb.append("    currentPage: ").append(toIndentedString(currentPage)).append("\n");
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

