package org.lockss.laaws.crawler.api;

import org.lockss.laaws.crawler.model.CrawlRequest;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.ErrorPager;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.UrlPager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A delegate to be called by the {@link CrawlsApiController}}.
 * Implement this interface with a {@link org.springframework.stereotype.Service} annotated class.
 */

public interface CrawlsApiDelegate {

    Logger log = LoggerFactory.getLogger(CrawlsApi.class);

    default Optional<ObjectMapper> getObjectMapper() {
        return Optional.empty();
    }

    default Optional<HttpServletRequest> getRequest() {
        return Optional.empty();
    }

    default Optional<String> getAcceptHeader() {
        return getRequest().map(r -> r.getHeader("Accept"));
    }

    /**
     * @see CrawlsApi#addCrawl
     */
    default ResponseEntity<CrawlRequest> addCrawl( CrawlRequest  body) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#deleteCrawlById
     */
    default ResponseEntity<CrawlRequest> deleteCrawlById( Integer  jobId) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#deleteCrawls
     */
    default ResponseEntity<Void> deleteCrawls( String  id) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlById
     */
    default ResponseEntity<CrawlStatus> getCrawlById( Integer  jobId) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlErrored
     */
    default ResponseEntity<ErrorPager> getCrawlErrored( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlExcluded
     */
    default ResponseEntity<UrlPager> getCrawlExcluded( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlFetched
     */
    default ResponseEntity<UrlPager> getCrawlFetched( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlNotModified
     */
    default ResponseEntity<UrlPager> getCrawlNotModified( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlParsed
     */
    default ResponseEntity<UrlPager> getCrawlParsed( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawlPending
     */
    default ResponseEntity<UrlPager> getCrawlPending( Integer  jobId,
         String  continuationToken,
         Integer  limit) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * @see CrawlsApi#getCrawls
     */
    default ResponseEntity<JobPager> getCrawls( Integer  limit,
         String  continuationToken) {
        if(getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default CrawlsApi interface so no example is generated");
        }
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }


    /**
     * @see CrawlsApi#getStatus
     */
    default ResponseEntity<org.lockss.laaws.status.model.ApiStatus> getStatus() {
      if (getObjectMapper().isPresent() && getAcceptHeader().isPresent()) {
        if (getAcceptHeader().get().contains("application/json")) {
          try {
            return new ResponseEntity<>(getObjectMapper().get()
                .readValue("{  \"ready\" : true,  \"version\" : \"version\"}", org.lockss.laaws.status.model.ApiStatus.class),
                HttpStatus.NOT_IMPLEMENTED);
          } catch (IOException e) {
            log.error("Couldn't serialize response for content type application/json", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
          }
        }
      } else {
        log.warn(
            "ObjectMapper or HttpServletRequest not configured in default StatusApi interface so no example is generated");
      }
      return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
