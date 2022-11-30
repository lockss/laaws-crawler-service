package org.lockss.laaws.crawler.utils;

import org.jetbrains.annotations.NotNull;
import org.lockss.config.CurrentConfig;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.log.L4JLogger;
import org.lockss.util.Constants;
import org.lockss.util.StringUtil;
import java.util.List;
import java.util.concurrent.*;

public class ExecutorUtils {
  public static final long DEFAULT_THREAD_TIMEOUT = 30 * Constants.SECOND;
  static final String PREFIX = PluggableCrawlManager.PREFIX;
  /**
   * Executor thread timeout
   */
  public static final String PARAM_THREAD_TIMEOUT = PREFIX + "thread.timeout";
  private static final L4JLogger log = L4JLogger.getLogger();

  public static ThreadPoolExecutor createOrReConfigureExecutor(ThreadPoolExecutor executor,
                                                               ExecSpec curSpec,
                                                               PriorityBlockingQueue<Runnable> priorityQueue) {
    long threadTimeout =
      CurrentConfig.getCurrentConfig().getTimeInterval(PARAM_THREAD_TIMEOUT, DEFAULT_THREAD_TIMEOUT);
    if (executor == null) {
      return makePriorityExecutor(threadTimeout, curSpec.coreThreads, curSpec.maxThreads, priorityQueue);
    }
    else {
      executor.setCorePoolSize(curSpec.coreThreads);
      executor.setMaximumPoolSize(curSpec.maxThreads);
      executor.setKeepAliveTime(threadTimeout, TimeUnit.MILLISECONDS);
      return executor;
    }
  }

  public static ThreadPoolExecutor makePriorityExecutor(long threadTimeout, int coreThreads,
                                                        int maxThreads, BlockingQueue<Runnable> priorityQueue) {
    ThreadPoolExecutor exec = new ThreadPoolExecutor(coreThreads, maxThreads, threadTimeout,
      TimeUnit.MILLISECONDS, priorityQueue) {
      @Override
      protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ComparableFutureTask<>(runnable, value);
      }

      @Override
      protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ComparableFutureTask<>(callable);
      }

    };
    exec.allowCoreThreadTimeOut(true);
    exec.setRejectedExecutionHandler((r, executor) -> {
      try {
        // block until there's room
        executor.getQueue().put(r);
        // check afterwards and throw if pool shutdown
        if (executor.isShutdown()) {
          throw new RejectedExecutionException("Crawl request " + r +
            " rejected from because shutdown");
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RejectedExecutionException("Producer interrupted", e);
      }
    });
    return exec;
  }

  public static ExecSpec getCurrentSpec(String reqSpec, ExecSpec defSpec) {
    // Set default for each field
    ExecSpec curSpec = ExecSpec.copyOf(defSpec);

    // Override from param
    parsePoolSpecInto(reqSpec, curSpec);
    // If illegal, use default
    if (curSpec.coreThreads > curSpec.maxThreads) {
      log.warn("coreThreads ({}) must be less than maxThreads ( {})", curSpec.coreThreads, curSpec.maxThreads);
      curSpec = defSpec;
    }
    return curSpec;
  }

  public static ExecSpec parsePoolSpecInto(String spec, ExecSpec eSpec) {
    List<String> specList = StringUtil.breakAt(spec, ";", 3, false, true);
    switch (specList.size()) {
      case 3:
        eSpec.maxThreads = Integer.parseInt(specList.get(2));
      case 2:
        eSpec.coreThreads = Integer.parseInt(specList.get(1));
      case 1:
        eSpec.queueSize = Integer.parseInt(specList.get(0));
    }
    // if no explicit maxThreads, make it same as coreThreads
    if (specList.size() == 2) {
      eSpec.maxThreads = eSpec.coreThreads;
    }
    return eSpec;
  }

  public static class ExecSpec {
    public int queueSize;
    public int coreThreads;
    public int maxThreads;

    public static  ExecSpec copyOf(ExecSpec spec) {
      ExecSpec copySpec = new ExecSpec();
      copySpec.coreThreads = spec.coreThreads;
      copySpec.maxThreads = spec.maxThreads;
      copySpec.queueSize = spec.queueSize;
      return copySpec;
    }
  }

  public static class ComparableFutureTask<T> extends FutureTask<T>
    implements Comparable<Object> {

    private final Comparable<Object> comparableJob;

    @SuppressWarnings("unchecked")
    public ComparableFutureTask(Runnable runnable, T value) {
      super(runnable, value);
      this.comparableJob = (Comparable<Object>) runnable;
    }

    @SuppressWarnings("unchecked")
    public ComparableFutureTask(Callable<T> callable) {
      super(callable);
      this.comparableJob = (Comparable<Object>) callable;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return this.comparableJob
        .compareTo(((ComparableFutureTask<?>) o).comparableJob);
    }
  }

}
