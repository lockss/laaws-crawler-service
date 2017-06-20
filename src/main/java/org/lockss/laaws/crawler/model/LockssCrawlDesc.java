package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.lockss.laaws.crawler.model.Au;
import org.lockss.laaws.crawler.model.CrawlKind;
import javax.validation.constraints.*;
/**
 * A descriptor for a LOCKSS crawl.
 */
@ApiModel(description = "A descriptor for a LOCKSS crawl.")

public class LockssCrawlDesc   {
  @JsonProperty("auId")
  private Au auId = null;

  @JsonProperty("crawlKind")
  private CrawlKind crawlKind = null;

  @JsonProperty("repairList")
  private List<String> repairList = new ArrayList<String>();

  public LockssCrawlDesc auId(Au auId) {
    this.auId = auId;
    return this;
  }

   /**
   * Get auId
   * @return auId
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public Au getAuId() {
    return auId;
  }

  public void setAuId(Au auId) {
    this.auId = auId;
  }

  public LockssCrawlDesc crawlKind(CrawlKind crawlKind) {
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

  public LockssCrawlDesc repairList(List<String> repairList) {
    this.repairList = repairList;
    return this;
  }

  public LockssCrawlDesc addRepairListItem(String repairListItem) {
    this.repairList.add(repairListItem);
    return this;
  }

   /**
   * The repair urls in a repair crawl
   * @return repairList
  **/
  @ApiModelProperty(value = "The repair urls in a repair crawl")
  public List<String> getRepairList() {
    return repairList;
  }

  public void setRepairList(List<String> repairList) {
    this.repairList = repairList;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LockssCrawlDesc lockssCrawlDesc = (LockssCrawlDesc) o;
    return Objects.equals(this.auId, lockssCrawlDesc.auId) &&
        Objects.equals(this.crawlKind, lockssCrawlDesc.crawlKind) &&
        Objects.equals(this.repairList, lockssCrawlDesc.repairList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(auId, crawlKind, repairList);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class LockssCrawlDesc {\n");
    
    sb.append("    auId: ").append(toIndentedString(auId)).append("\n");
    sb.append("    crawlKind: ").append(toIndentedString(crawlKind)).append("\n");
    sb.append("    repairList: ").append(toIndentedString(repairList)).append("\n");
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

