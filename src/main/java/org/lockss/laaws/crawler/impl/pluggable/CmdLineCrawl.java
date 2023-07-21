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
import org.lockss.laaws.crawler.impl.pluggable.CmdLineCrawler.RunnableCrawlJob;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUtil;
import org.lockss.state.AuState;
import org.lockss.util.MimeUtil;
import org.lockss.util.StringUtil;
import org.lockss.util.UrlUtil;
import org.lockss.util.io.FileUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
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

  List<String> stems = new ArrayList<>();
  List<String> reqUrls;
  CrawlerStatus crawlerStatus;
  AuState auState;
  boolean isRepairCrawl = false;
  RunnableCrawlJob runnableJob;
  private LockssRunnable lockssRunnable;

  /**
   * Instantiates a new Cmd line crawl.
   *
   * @param crawler  the crawler for this crawl
   * @param crawlJob the job for this crawl
   */
  public CmdLineCrawl(CmdLineCrawler crawler, ArchivalUnit au, CrawlJob crawlJob) {
    super(crawler.getCrawlerConfig(), au, crawlJob);
    this.crawler = crawler;
    String jobId = crawlJob.getJobId();
    threadName = crawlDesc.getCrawlKind() + ":"
      + crawlDesc.getCrawlerId() +
      ":" + jobId.substring(0, Integer.min(6, jobId.length() - 1));
    final Configuration currentConfig = ConfigManager.getCurrentConfig();
    outputLogLevel = crawler.getOutputLogLevel();
    errorLogLevel = crawler.getErrorLogLevel();
    isRepairCrawl = crawlJob.getCrawlDesc().getCrawlKind() == CrawlDesc.CrawlKindEnum.REPAIR;
    reqUrls = crawlDesc.getCrawlList();
  }


  @Override
  public CrawlerStatus startCrawl() {
    CrawlerStatus cs = getCrawlerStatus();
    JobStatus js = getJobStatus();
    try {
      js.setStatusCode(JobStatus.StatusCodeEnum.ACTIVE);
      js.setMsg("Active.");
      tmpDir = FileUtil.createTempDir(crawlDesc.getCrawlerId(), "");
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
    deleteTmpDir();
    if(lockssRunnable != null) {
      lockssRunnable.interruptThread();
      lockssRunnable = null;
    }
    else if(crawlerStatus != null) {
      crawlerStatus.setCrawlStatus(Crawler.STATUS_ABORTED, "Request removed from queue.");
      ApiUtils.getPluggableCrawlManager().handleCrawlComplete(crawlerStatus);
      auState.newCrawlFinished(crawlerStatus.getCrawlStatus(),null);
      crawlerStatus.signalCrawlEnded();
    }
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
  protected List<String> getReqUrls() {return reqUrls; }
  protected  List<String> getStems() { return stems;}

  public LockssRunnable getRunnable() {
    lockssRunnable = new LockssRunnable(threadName) {

      @Override
      public void lockssRun() {
        log.debug2("{} started", this);
        crawlerStatus = getCrawlerStatus();
        auState = AuUtil.getAuState(crawlerStatus.getAu());
        boolean joinOutputStreams = crawler.isJoinOutputStreams();
        try {
          auState.newCrawlStarted();
          nowRunning();
          crawlerStatus = startCrawl();
          ProcessBuilder builder = new ProcessBuilder();
          builder.directory(tmpDir);
          builder.command(command);
          if (joinOutputStreams) {
            builder.redirectErrorStream(true);
          }
          log.debug("Starting crawl process in {} with command {}...",
                    tmpDir, String.join(" ", command));
          Process process = builder.start();
          StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
          outputGobbler.start();

          if(!joinOutputStreams) {
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            errorGobbler.start();
          }
          crawlerStatus.signalCrawlStarted();
          int exitCode = process.waitFor();
          if (crawler.didCrawlSucceed(exitCode)) {
            log.info("Crawl process succeeded with exitCode {}", exitCode);
            Collection<File> warcFiles = getWarcFiles("*.warc.gz");
            log.info("Importing {} into repository.",
                     StringUtil.numberOfUnits(warcFiles.size(), "warcfile"));
            crawlerStatus.setCrawlStatus(Crawler.STATUS_ACTIVE, "Storing");
            for (File warc : warcFiles) {
              crawler.storeInRepository(crawlerStatus.getAuId(), warc, true);
            }
            crawler.updateAuConfig(getAu(), isRepairCrawl, getReqUrls(), getStems());
            crawlerStatus.setCrawlStatus(Crawler.STATUS_SUCCESSFUL);
            log.info("Content stored, crawl complete.");

          }
          else {
            log.info("Crawl process failed with exitCode {}", exitCode);
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
          crawlerStatus.setCrawlStatus(
              Crawler.STATUS_ABORTED, "Crawl Interrupted");
          // no action
        }
        finally {
          ApiUtils.getPluggableCrawlManager().handleCrawlComplete(crawlerStatus);
          auState.newCrawlFinished(crawlerStatus.getCrawlStatus(),null);
          crawlerStatus.signalCrawlEnded();
          setThreadName(threadName + ": idle");
          deleteTmpDir();
          log.debug2("{} terminating", this);
        }
      }
    };
    return lockssRunnable;
  }

  private void deleteTmpDir() {
    log.debug("Deleting tree at {}", tmpDir);
    boolean isDeleted=true;
    if(tmpDir!= null) {
      isDeleted = FileUtil.delTree(tmpDir);
    }
    log.trace("isDeleted = {}", isDeleted);
    if (!isDeleted) {
      log.warn("Temporary directory {} cannot be deleted after processing", tmpDir);
    }
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
          else if(type.equals("OUTPUT")) {
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
      try {
        String stem = UrlUtil.getUrlPrefix(url);
        if(!stems.contains(stem)) stems.add(stem);
      } catch (MalformedURLException e) {
        log.error("Found malformed url: " + url);
      }
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

