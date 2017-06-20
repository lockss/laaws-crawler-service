package org.lockss.laaws.crawler.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.math.BigDecimal;
import javax.validation.constraints.*;
/**
 * The status of a particular crawl.
 */
@ApiModel(description = "The status of a particular crawl.")

public class Status   {
  @JsonProperty("code")
  private BigDecimal code = null;

  @JsonProperty("msg")
  private String msg = null;

  public Status code(BigDecimal code) {
    this.code = code;
    return this;
  }

   /**
   * The numeric value of the current state
   * @return code
  **/
  @ApiModelProperty(required = true, value = "The numeric value of the current state")
  @NotNull
  public BigDecimal getCode() {
    return code;
  }

  public void setCode(BigDecimal code) {
    this.code = code;
  }

  public Status msg(String msg) {
    this.msg = msg;
    return this;
  }

   /**
   * The text description for the current state
   * @return msg
  **/
  @ApiModelProperty(required = true, value = "The text description for the current state")
  @NotNull
  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Status status = (Status) o;
    return Objects.equals(this.code, status.code) &&
        Objects.equals(this.msg, status.msg);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, msg);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Status {\n");
    
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    msg: ").append(toIndentedString(msg)).append("\n");
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

