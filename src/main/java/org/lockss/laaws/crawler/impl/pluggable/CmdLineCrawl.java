package org.lockss.laaws.crawler.impl.pluggable;


import java.io.*;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.Level;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssRunnable;
import org.lockss.laaws.crawler.impl.ApiUtils;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUtil;
import org.lockss.state.AuState;
import org.lockss.util.MimeUtil;
import org.lockss.util.UrlUtil;
import org.lockss.util.io.FileUtil;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;

/**
 * A class to wrap a single CommandLineCrawl
 */
public class CmdLineCrawl extends PluggableCrawl {
  private static final L4JLogger log = L4JLogger.getLogger();
  protected CmdLineCrawler crawler;
  protected String threadName;
  /*
  The command line as a list to execute.
   */
  protected List<String> command = null;
  /**
   * The temp directory used to store any files.
   */
  protected File tmpDir = null;

  protected String outputLogLevel;
  protected String errorLogLevel;
  protected static Pattern successPattern = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}.*[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,3})? URL:.* .*[^]]*] -> .*[^\"]*", Pattern.CASE_INSENSITIVE);
  protected static Pattern errorPattern = Pattern.compile(".*\\bERROR\\b.*", Pattern.CASE_INSENSITIVE);
  protected static Pattern urlPattern = Pattern.compile("((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)", Pattern.CASE_INSENSITIVE);
  protected static Pattern bytesPattern= Pattern.compile("\\[[0-9]+/[0-9]+]", Pattern.CASE_INSENSITIVE);
  private static final String ERROR_STR = " ERROR ";

  /**
   * Instantiates a new Cmd line crawl.
   *
   * @param crawler  the crawler for this crawl
   * @param crawlJob the job for this crawl
   */
  public CmdLineCrawl(CmdLineCrawler crawler, ArchivalUnit au, CrawlJob crawlJob) {
    super(crawler.getCrawlerConfig(), au, crawlJob);
    this.crawler = crawler;
    threadName = crawlDesc.getCrawlKind() + ":"
      + crawlDesc.getCrawlerId() +
      ":" + crawlJob.getJobId();
    final Configuration currentConfig = ConfigManager.getCurrentConfig();
    outputLogLevel = crawler.getOutputLogLevel();
    errorLogLevel = crawler.getErrorLogLevel();

  }


  @Override
  public CrawlerStatus startCrawl() {
    CrawlerStatus cs = getCrawlerStatus();
    JobStatus js = getJobStatus();
    try {
      js.setStatusCode(JobStatus.StatusCodeEnum.ACTIVE);
      js.setMsg("Active.");
      tmpDir = FileUtil.createTempDir("laaws-pluggable-crawler", "");
      command = crawler.getCmdLineBuilder().buildCommandLine(getCrawlDesc(), tmpDir);
    }
    catch (IOException ioe) {
      log.error("Unable to create output directory for crawl:", ioe);

      js.setStatusCode(JobStatus.StatusCodeEnum.ERROR);
    }
    return cs;
  }

  @Override
  public CrawlerStatus stopCrawl() {
    JobStatus status = getJobStatus();
    status.setStatusCode(JobStatus.StatusCodeEnum.ABORTED);
    status.setMsg("Crawl Aborted.");
    return getCrawlerStatus();
  }

  /**
   * Gets tmp dir.
   *
   * @return the tmp dir
   */
  public File getTmpDir() {
    return tmpDir;
  }

  public Collection<File> getWarcFiles(String extension) {
    return FileUtils.listFiles(tmpDir, new WildcardFileFilter(extension), null);
  }

  public List<String>  getWarcFileNames(String extension) {
    try {
      return FileUtil.listDirFilesWithExtension(tmpDir,extension);
    }
    catch (IOException e) {
      log.warn("CmdLine crawl did not return any results.");
      return null;
    }
  }

  /**
   * Gets command.
   *
   * @return the command
   */
  public List<String> getCommand() {
    return command;
  }

  public LockssRunnable getRunnable() {
    return new LockssRunnable(threadName) {

      @Override
      public void lockssRun() {
        log.debug2("{} started", this);
        CrawlerStatus crawlerStatus = getCrawlerStatus();
        AuState aus = AuUtil.getAuState(crawlerStatus.getAu());
        boolean joinOutputStreams = crawler.isJoinOutputStreams();
        try {
          aus.newCrawlStarted();
          nowRunning();
          crawlerStatus = startCrawl();
          ProcessBuilder builder = new ProcessBuilder();
          builder.command(command);
          if (joinOutputStreams) {
            builder.redirectErrorStream(true);
          }
          log.debug("Starting crawl process with command {}...", String.join(" ", command));
          Process process = builder.start();
          StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
          outputGobbler.start();

          if(!joinOutputStreams) {
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            errorGobbler.start();
          }
          crawlerStatus.signalCrawlStarted();
          int exitCode = process.waitFor();
          log.debug("Completed crawl process: exitCode = {}", exitCode);
          if (crawler.didCrawlSucceed(exitCode)) {
            Collection<File> warcFiles = getWarcFiles("*.warc.gz");
            log.debug2("Found {} warcFiles after crawl.", warcFiles.size());
            for (File warc : warcFiles) {
              crawler.storeInRepository(crawlerStatus.getAuId(), warc, true);
            }
            crawlerStatus.setCrawlStatus(Crawler.STATUS_SUCCESSFUL);

          }
          else {
            crawlerStatus.setCrawlStatus(
              Crawler.STATUS_ERROR, "crawl exited with code: " + exitCode);
          }
        }
        catch (IOException ioe) {
          log.error("Exception caught running process", ioe);
          crawlerStatus.setCrawlStatus(
              Crawler.STATUS_ERROR, "Exception thrown: " + ioe.getMessage());
        }
        catch (InterruptedException ignore) {
          // no action
        }
        finally {
          ApiUtils.getPluggableCrawlManager().handleCrawlComplete(crawlerStatus);
          aus.newCrawlFinished(crawlerStatus.getCrawlStatus(),null);
          crawlerStatus.signalCrawlEnded();
          setThreadName(threadName + ": idle");
          log.info("Deleting tree at {}", tmpDir);
          boolean isDeleted=true;
          if(tmpDir!= null) {
            isDeleted = FileUtil.delTree(tmpDir);
          }
          log.trace("isDeleted = {}", isDeleted);
          if (!isDeleted) {
            log.warn("Temporary directory {} cannot be deleted after processing", tmpDir);
          }
          log.debug2("{} terminating", this);
        }
      }
    };
  }

  private class StreamGobbler extends Thread {
    InputStream is;
    String type;

    private StreamGobbler(InputStream is, String type) {
      this.is = is;
      this.type = type;
    }

    @Override
    public void run() {
      try {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;
        String pre = null;

        while ((line = br.readLine()) != null) {
          if (type.equals("ERROR")) {
            if(line.endsWith(":")) {
              pre=line;
            }
            else {
              parseLine(pre, line);
              pre = null;
            }
            log.log(Level.toLevel(errorLogLevel),line);
          }
          else if(type.equals("INPUT")) {
            if(line.endsWith(":")) {
              pre=line;
            }
            else {
              parseLine(pre, line);
              pre = null;
            }
            log.log(Level.toLevel(outputLogLevel), line);
          }
        }
      }
      catch (IOException ioe) {
        log.error("Exception thrown while reading stream output.", ioe);
      }
    }
  }

  public void parseLine(String pre, String line) {
    String msg_line = line;
    if(pre != null)
      msg_line = pre + " " +line;
    // extract any urls from the line (there s/b only one)
    List<String> urls = extractUrls(msg_line);
    // extract the url
    if(urls.isEmpty()) return;
    if(urls.size() > 1) {
      log.warn ("Found multiple urls in message line: "+ msg_line);
    }
    String url = urls.get(0);
    // extract the file extension
    String ext = null;
    try {
      ext = UrlUtil.getFileExtension(url);
    }
    catch (MalformedURLException e) {
      log.warn("Attempt to parse log line with malformed url.");
    }

    // check for success
    Matcher matcher = successPattern.matcher(msg_line);
    if(matcher.matches()) {
      long bytesFetched = extractBytes(msg_line);
      crawlerStatus.signalUrlFetched(url);
      crawlerStatus.addContentBytesFetched(bytesFetched);
      if (ext != null) {
        crawlerStatus.signalMimeTypeOfUrl(MimeUtil.getMimeTypeFromExtension(ext),url);
      }
    }
    else {
      //check for error
      matcher = errorPattern.matcher(msg_line);
      if(matcher.matches()) {
        int idx = msg_line.indexOf(ERROR_STR);
        String error = msg_line.substring(idx+ERROR_STR.length());
        crawlerStatus.signalErrorForUrl(url, error);
        if (ext != null) {
          crawlerStatus.signalMimeTypeOfUrl(MimeUtil.getMimeTypeFromExtension(ext),url);
        }
      }
      else {
        log.warn("Unknown pattern while parsing log line: " + line);
      }
    }
  }

  /**
   * Returns a list with all links contained in the input
   */
  public static List<String> extractUrls(String text)
  {
    List<String> containedUrls = new ArrayList<String>();
    Matcher m = urlPattern.matcher(text);

    while (m.find()) {
      containedUrls.add(text.substring(m.start(0), m.end(0)));
    }
    return containedUrls;
  }

  public static long extractBytes(String str) {
    long bytes = 0;
    Matcher m = bytesPattern.matcher(str);
    if(m.find()) {
      // [nnn/nnn]
      String found = str.substring(m.start(0), m.end(0));
      String bytestr = found.substring(1,found.indexOf("/"));
      bytes = Long.parseLong(bytestr);
    }
    return bytes;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("(I): ");
    sb.append(getAuId());
    sb.append(", pri: ");
    sb.append(getCrawlDesc().getPriority());
    if (getCrawlDesc().getRefetchDepth() >= 0) {
      sb.append(", depth: ");
      sb.append(getCrawlDesc().getRefetchDepth());
    }
    sb.append(", crawlDesc: ");
    sb.append(getCrawlDesc());
    sb.append(", tmpDir: ");
    sb.append(tmpDir);
    sb.append(", command: ");
    sb.append(command);
    sb.append(", crawlerStatus: ");
    sb.append(getCrawlerStatus());
    sb.append("]");
    return sb.toString();
  }
}

