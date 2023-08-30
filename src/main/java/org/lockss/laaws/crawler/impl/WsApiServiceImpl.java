package org.lockss.laaws.crawler.impl;

import org.josql.Query;
import org.josql.QueryExecutionException;
import org.josql.QueryParseException;
import org.josql.QueryResults;
import org.lockss.laaws.crawler.api.WsApiDelegate;
import org.lockss.spring.base.BaseSpringApiServiceImpl;
import org.lockss.util.josql.JosqlUtil;
import org.lockss.ws.entities.CrawlWsResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WsApiServiceImpl extends BaseSpringApiServiceImpl implements WsApiDelegate {
  public ResponseEntity getWsCrawls(String crawlQuery) {
    log.debug("crawlQuery = {}", crawlQuery);

    CrawlHelper crawlHelper = new CrawlHelper();
    List<CrawlWsResult> results = null;

    try {
      // Create the full query.
      String fullQuery =
        JosqlUtil.createFullQuery(
          crawlQuery,
          CrawlHelper.SOURCE_FQCN,
          CrawlHelper.PROPERTY_NAMES,
          CrawlHelper.RESULT_FQCN);
      log.trace("fullQuery = {}", fullQuery);

      // Create a new JoSQL query.
      Query q = new Query();

      try {
        // Parse the SQL-like query.
        q.parse(fullQuery);

        // Execute the query.
        QueryResults qr = q.execute(crawlHelper.createUniverse());

        // Get the query results.
        results = (List<CrawlWsResult>) qr.getResults();
        log.trace("results.size() = {}", results.size());
        log.trace("results = {}", crawlHelper.nonDefaultToString(results));
        return new ResponseEntity<List<CrawlWsResult>>(results, HttpStatus.OK);
      }
      catch (QueryExecutionException | QueryParseException qex) {
        String message = "Cannot getWsCrawls() for crawlQuery = '" + crawlQuery + "'";
        log.error(message, qex);
        return new ResponseEntity<String>(message, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
    catch (Exception ex) {
      String message = "Cannot getWsCrawls() for crawlQuery = '" + crawlQuery + "'";
      log.error(message, ex);
      return new ResponseEntity<String>(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
