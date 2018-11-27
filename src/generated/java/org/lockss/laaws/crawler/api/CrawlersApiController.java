package org.lockss.laaws.crawler.api;

import org.springframework.stereotype.Controller;

@Controller
public class CrawlersApiController implements CrawlersApi {

    private final CrawlersApiDelegate delegate;

    @org.springframework.beans.factory.annotation.Autowired
    public CrawlersApiController(CrawlersApiDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public CrawlersApiDelegate getDelegate() {
        return delegate;
    }
}
