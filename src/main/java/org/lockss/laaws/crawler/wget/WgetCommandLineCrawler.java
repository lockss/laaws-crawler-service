package org.lockss.laaws.crawler.wget;

import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawler;

public class WgetCommandLineCrawler extends CmdLineCrawler {

  public WgetCommandLineCrawler() {
    super();
    setCmdLineBuilder(new WgetCommandLineBuilder());

  }
 }
