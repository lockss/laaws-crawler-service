package org.lockss.laaws.crawler.api;

import org.lockss.laaws.crawler.model.CrawlerInfo;
import org.lockss.laaws.crawler.model.InlineResponse200;

import java.util.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CrawlersApiControllerIntegrationTest {

    @Autowired
    private CrawlersApi api;

    @Test
    public void getCrawlerByNameTest() throws Exception {
        String crawler = "crawler_example";
        ResponseEntity<CrawlerInfo> responseEntity = api.getCrawlerByName(crawler);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

    @Test
    public void getCrawlersTest() throws Exception {
        ResponseEntity<InlineResponse200> responseEntity = api.getCrawlers();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, responseEntity.getStatusCode());
    }

}
