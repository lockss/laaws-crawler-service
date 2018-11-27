package org.lockss.laaws.crawler.api;

import org.lockss.laaws.crawler.model.CrawlRequest;
import org.lockss.laaws.crawler.model.CrawlStatus;
import org.lockss.laaws.crawler.model.ErrorPager;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.UrlPager;

import java.util.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.laaws.status.model.ApiStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CrawlsApiControllerIntegrationTest {

    @Autowired
    private CrawlsApi api;

    @Test
    public void addCrawlTest() throws Exception {
        CrawlRequest body = new CrawlRequest();
        ResponseEntity<CrawlRequest> responseEntity = api.addCrawl(body);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void deleteCrawlByIdTest() throws Exception {
        Integer jobId = 56;
        ResponseEntity<CrawlRequest> responseEntity = api.deleteCrawlById(jobId);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void deleteCrawlsTest() throws Exception {
        String id = "id_example";
        ResponseEntity<Void> responseEntity = api.deleteCrawls(id);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlByIdTest() throws Exception {
        Integer jobId = 56;
        ResponseEntity<CrawlStatus> responseEntity = api.getCrawlById(jobId);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlErroredTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<ErrorPager> responseEntity = api.getCrawlErrored(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlExcludedTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<UrlPager> responseEntity = api.getCrawlExcluded(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlFetchedTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<UrlPager> responseEntity = api.getCrawlFetched(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlNotModifiedTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<UrlPager> responseEntity = api.getCrawlNotModified(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlParsedTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<UrlPager> responseEntity = api.getCrawlParsed(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlPendingTest() throws Exception {
        Integer jobId = 56;
        String continuationToken = "continuationToken_example";
        Integer limit = 56;
        ResponseEntity<UrlPager> responseEntity = api.getCrawlPending(jobId, continuationToken, limit);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlsTest() throws Exception {
        Integer limit = 56;
        String continuationToken = "continuationToken_example";
        ResponseEntity<JobPager> responseEntity = api.getCrawls(limit, continuationToken);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }
    
    @Test
    public void getStatusTest() throws Exception {
        ResponseEntity<ApiStatus> responseEntity = api.getStatus();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

}
