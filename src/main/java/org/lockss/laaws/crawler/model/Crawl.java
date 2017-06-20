package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDate;
import org.lockss.laaws.crawler.model.Status;
import javax.validation.constraints.*;
/**
 * A description of a crawl being performed by this Crawler
 */
@ApiModel(description = "A description of a crawl being performed by this Crawler")

public class Crawl   {
  @JsonProperty("id")
  private String id = null;

  @JsonProperty("creationDate")
  private LocalDate creationDate = null;

  @JsonProperty("status")
  private Status status = null;

  @JsonProperty("crawlDescriptor")
  private Object crawlDescriptor = null;

  @JsonProperty("startDate")
  private LocalDate startDate = null;

  @JsonProperty("endDate")
  private LocalDate endDate = null;

  @JsonProperty("result")
  private String result = null;

  public Crawl id(String id) {
    this.id = id;
    return this;
  }

   /**
   * A randomly generated crawl id
   * @return id
  **/
  @ApiModelProperty(required = true, value = "A randomly generated crawl id")
  @NotNull
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Crawl creationDate(LocalDate creationDate) {
    this.creationDate = creationDate;
    return this;
  }

   /**
   * The time the crawl was requested in ISO-8601
   * @return creationDate
  **/
  @ApiModelProperty(example = "yyyy-MM-ddTHH:mm:ss.SSSZ", required = true, value = "The time the crawl was requested in ISO-8601")
  @NotNull
  public LocalDate getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(LocalDate creationDate) {
    this.creationDate = creationDate;
  }

  public Crawl status(Status status) {
    this.status = status;
    return this;
  }

   /**
   * Get status
   * @return status
  **/
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Crawl crawlDescriptor(Object crawlDescriptor) {
    this.crawlDescriptor = crawlDescriptor;
    return this;
  }

   /**
   * The crawl description from the request.
   * @return crawlDescriptor
  **/
  @ApiModelProperty(example = "&quot;genericCrawlDesc&quot;", required = true, value = "The crawl description from the request.")
  @NotNull
  public Object getCrawlDescriptor() {
    return crawlDescriptor;
  }

  public void setCrawlDescriptor(Object crawlDescriptor) {
    this.crawlDescriptor = crawlDescriptor;
  }

  public Crawl startDate(LocalDate startDate) {
    this.startDate = startDate;
    return this;
  }

   /**
   * The time the crawl began in ISO-8601
   * @return startDate
  **/
  @ApiModelProperty(example = "yyyy-MM-ddTHH:mm:ss.SSSZ", value = "The time the crawl began in ISO-8601")
  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public Crawl endDate(LocalDate endDate) {
    this.endDate = endDate;
    return this;
  }

   /**
   * The time the crawl ended in ISO-8601
   * @return endDate
  **/
  @ApiModelProperty(example = "yyyy-MM-ddTHH:mm:ss.SSSZ", value = "The time the crawl ended in ISO-8601")
  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public Crawl result(String result) {
    this.result = result;
    return this;
  }

   /**
   * A URI which can be used to retrieve the crawl data.
   * @return result
  **/
  @ApiModelProperty(value = "A URI which can be used to retrieve the crawl data.")
  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Crawl crawl = (Crawl) o;
    return Objects.equals(this.id, crawl.id) &&
        Objects.equals(this.creationDate, crawl.creationDate) &&
        Objects.equals(this.status, crawl.status) &&
        Objects.equals(this.crawlDescriptor, crawl.crawlDescriptor) &&
        Objects.equals(this.startDate, crawl.startDate) &&
        Objects.equals(this.endDate, crawl.endDate) &&
        Objects.equals(this.result, crawl.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, creationDate, status, crawlDescriptor, startDate, endDate, result);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Crawl {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    creationDate: ").append(toIndentedString(creationDate)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    crawlDescriptor: ").append(toIndentedString(crawlDescriptor)).append("\n");
    sb.append("    startDate: ").append(toIndentedString(startDate)).append("\n");
    sb.append("    endDate: ").append(toIndentedString(endDate)).append("\n");
    sb.append("    result: ").append(toIndentedString(result)).append("\n");
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

