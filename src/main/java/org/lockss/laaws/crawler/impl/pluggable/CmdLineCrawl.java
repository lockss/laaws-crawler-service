package org.lockss.laaws.crawler.impl.pluggable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssRunnable;
import org.lockss.laaws.crawler.impl.ApiUtils;
import org.lockss.log.L4JLogger;
import org.lockss.util.io.FileUtil;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;

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

  /**
   * Instantiates a new Cmd line crawl.
   *
   * @param crawler  the crawler for this crawl
   * @param crawlJob the job for this crawl
   */
  public CmdLineCrawl(CmdLineCrawler crawler, CrawlJob crawlJob) {
    super(crawler.getCrawlerConfig(), crawlJob);
    this.crawler = crawler;
    threadName = crawlDesc.getCrawlKind() + ":"
      + crawlDesc.getCrawlerId() +
      ":" + crawlJob.getJobId();

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

        try {
          nowRunning();
          startCrawl();
          ProcessBuilder builder = new ProcessBuilder();
          builder.command(command);
          builder.inheritIO();
          log.info("external crawl process started");
          Process process = builder.start();
          crawlerStatus.signalCrawlStarted();
          int exitCode = process.waitFor();
          log.debug("external crawl process finished: exitCode = {}", exitCode);
          if (exitCode == 0) {
            Collection<File> warcFiles = getWarcFiles("*.warc.gz");
            log.debug2("Found {} warcFiles after crawl.", warcFiles.size());
            for (File warc : warcFiles) {
              crawler.storeInRepository(crawlerStatus.getAuId(), warc, true);
            }
            crawlerStatus.setCrawlStatus(Crawler.STATUS_SUCCESSFUL);
          }
          else {
            log.error();
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
