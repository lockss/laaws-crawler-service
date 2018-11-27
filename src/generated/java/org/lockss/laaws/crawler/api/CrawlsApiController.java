package org.lockss.laaws.crawler.api;

import org.springframework.stereotype.Controller;

@Controller
public class CrawlsApiController implements CrawlsApi {

    private final CrawlsApiDelegate delegate;

    @org.springframework.beans.factory.annotation.Autowired
    public CrawlsApiController(CrawlsApiDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public CrawlsApiDelegate getDelegate() {
        return delegate;
    }
}
