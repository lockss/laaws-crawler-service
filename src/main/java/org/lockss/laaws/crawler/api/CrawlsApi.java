package org.lockss.laaws.crawler.api;

import org.lockss.laaws.crawler.model.Crawl;
import org.lockss.laaws.crawler.model.Error;
import org.lockss.laaws.crawler.model.InlineResponse200;
import org.lockss.laaws.crawler.model.InlineResponse2001;

import io.swagger.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import javax.validation.constraints.*;

@Api(value = "crawls", description = "the crawls API")
public interface CrawlsApi {

    @ApiOperation(value = "Remove or stop a crawl", notes = "", response = Void.class, authorizations = {
        @Authorization(value = "HTTP_BASIC")
    }, tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Crawl successfully stopped", response = Void.class),
        @ApiResponse(code = 401, message = "Unauthorized request", response = Void.class),
        @ApiResponse(code = 404, message = "No crawl found with that id", response = Void.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = Void.class) })
    @RequestMapping(value = "/crawls/{crawlid}",
        produces = { "application/json" }, 
        consumes = { "application/json" },
        method = RequestMethod.DELETE)
    default ResponseEntity<Void> crawlsCrawlidDelete(@ApiParam(value = "",required=true ) @PathVariable("crawlid") String crawlid,
         @NotNull @ApiParam(value = "The crawl id", required = true) @RequestParam(value = "id", required = true) String id) {
        // do some magic!
        return new ResponseEntity<Void>(HttpStatus.OK);
    }


    @ApiOperation(value = "Get the crawl info for this job", notes = "Get the job represented by this job id", response = InlineResponse2001.class, authorizations = {
        @Authorization(value = "HTTP_BASIC")
    }, tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = InlineResponse2001.class),
        @ApiResponse(code = 400, message = "Status 400", response = InlineResponse2001.class),
        @ApiResponse(code = 404, message = "No such job", response = InlineResponse2001.class) })
    @RequestMapping(value = "/crawls/{crawlid}",
        produces = { "application/json" }, 
        consumes = { "application/json" },
        method = RequestMethod.GET)
    default ResponseEntity<InlineResponse2001> crawlsCrawlidGet(@ApiParam(value = "",required=true ) @PathVariable("crawlid") String crawlid) {
        // do some magic!
        return new ResponseEntity<InlineResponse2001>(HttpStatus.OK);
    }


    @ApiOperation(value = "stop and remove all crawl", notes = "", response = Void.class, authorizations = {
        @Authorization(value = "HTTP_BASIC")
    }, tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Crawl successfully stopped", response = Void.class),
        @ApiResponse(code = 401, message = "Unauthorized request", response = Void.class),
        @ApiResponse(code = 404, message = "No crawl found with that id", response = Void.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = Void.class) })
    @RequestMapping(value = "/crawls/",
        produces = { "application/json" }, 
        consumes = { "application/json" },
        method = RequestMethod.DELETE)
    default ResponseEntity<Void> crawlsDelete( @NotNull @ApiParam(value = "The crawl id", required = true) @RequestParam(value = "id", required = true) String id) {
        // do some magic!
        return new ResponseEntity<Void>(HttpStatus.OK);
    }


    @ApiOperation(value = "Get the list of recent crawls", notes = "Get a list of recent crawls performed by the crawler if size and page are passed in use those arguments to limit return.", response = InlineResponse200.class, authorizations = {
        @Authorization(value = "HTTP_BASIC")
    }, tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = InlineResponse200.class),
        @ApiResponse(code = 400, message = "Status 400", response = InlineResponse200.class) })
    @RequestMapping(value = "/crawls/",
        produces = { "application/json" }, 
        consumes = { "application/json" },
        method = RequestMethod.GET)
    default ResponseEntity<InlineResponse200> crawlsGet( @ApiParam(value = "Size of the page to retrieve.") @RequestParam(value = "size", required = false) Integer size,
         @ApiParam(value = "Number of the page to retrieve.") @RequestParam(value = "page", required = false) Integer page) {
        // do some magic!
        return new ResponseEntity<InlineResponse200>(HttpStatus.OK);
    }


    @ApiOperation(value = "Request a crawl using a descriptor", notes = "Use the information found in the descriptor object to initiate a crawl.", response = Crawl.class, authorizations = {
        @Authorization(value = "HTTP_BASIC")
    }, tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 202, message = "The Crawl request has been accepted", response = Crawl.class),
        @ApiResponse(code = 401, message = "The Request is unauthorized", response = Crawl.class),
        @ApiResponse(code = 404, message = "The au cannot be found.", response = Crawl.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = Crawl.class) })
    @RequestMapping(value = "/crawls/",
        produces = { "application/json" }, 
        method = RequestMethod.POST)
    default ResponseEntity<Crawl> crawlsPost(@ApiParam(value = "" ,required=true ) @RequestBody Object body) {
        // do some magic!
        return new ResponseEntity<Crawl>(HttpStatus.OK);
    }

}
