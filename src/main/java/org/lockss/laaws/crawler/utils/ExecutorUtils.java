package org.lockss.laaws.crawler.utils;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.lockss.config.CurrentConfig;
import org.lockss.laaws.crawler.impl.PluggableCrawlManager;
import org.lockss.log.L4JLogger;
import org.lockss.util.Constants;
import org.lockss.util.StringUtil;
import java.util.List;
import java.util.concurrent.*;

public class ExecutorUtils {
  static final String PREFIX = PluggableCrawlManager.PREFIX;

  public static final String EXEC_PREFIX = PREFIX + "executor.";

  /**
   * Executor Spec:
   * <tt><i>queue-max</i>;<i>thread-max</i></tt> or
   * <tt><i>queue-max</i>;<i>core-threads</i>;<i>max-threads</i></tt>

   */
  public static final String PARAM_EXECUTOR_SPEC = EXEC_PREFIX + "<name>.spec";
  public static final String DEFAULT_EXECUTOR_SPEC = "100;2";

  /**
   * The default executor spec as an object
   */
  private static final ExecSpec DEF_EXEC_SPEC = ExecutorUtils.parsePoolSpec(DEFAULT_EXECUTOR_SPEC);

  /**
   * Executor thread timeout
   */
  public static final String PARAM_THREAD_TIMEOUT = PREFIX + "thread.timeout";
  public static final long DEFAULT_THREAD_TIMEOUT = 30 * Constants.SECOND;

  private static final L4JLogger log = L4JLogger.getLogger();

  public static ThreadPoolExecutor createOrReConfigureExecutor(ThreadPoolExecutor executer,
      String spec,
      String defaultSpec) {
    // Set default for each field
    ExecSpec eSpec = parsePoolSpec(defaultSpec);
    // Override from param
    eSpec = parsePoolSpecInto(spec, eSpec);
    // If illegal, use default
    if (eSpec.coreThreads > eSpec.maxThreads) {
      log.warn("coreThreads (" + eSpec.coreThreads +
          ") must be less than maxThreads (" + eSpec.maxThreads + ")");
      eSpec = parsePoolSpec(defaultSpec);
    }
    long threadTimeout =
        CurrentConfig.getCurrentConfig().getTimeInterval(PARAM_THREAD_TIMEOUT, DEFAULT_THREAD_TIMEOUT);
    if (executer == null) {
      return makePriorityExecutor(eSpec.queueSize, threadTimeout,
          eSpec.coreThreads, eSpec.maxThreads);
    } else {
      executer.setCorePoolSize(eSpec.coreThreads);
      executer.setMaximumPoolSize(eSpec.maxThreads);
      executer.setKeepAliveTime(threadTimeout, TimeUnit.MILLISECONDS);
      // Can't change queue capacity, would need to copy to new queue
      // which requires awkward locking/synchronzation?
      return executer;
    }
  }

  static public ThreadPoolExecutor makePriorityExecutor(int queueMax, long threadTimeout,
      int coreThreads, int maxThreads) {
    ThreadPoolExecutor exec = new ThreadPoolExecutor(coreThreads, maxThreads, threadTimeout,
        TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(queueMax)) {
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
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RejectedExecutionException("Producer interrupted", e);
      }
    });
    return exec;
  }

  public static ExecSpec parsePoolSpec(String spec) {
    return parsePoolSpecInto(spec, new ExecSpec());
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

    public ExecSpec() {}

    public ExecSpec queueSize(int queueSize) {
      this.queueSize = queueSize;
      return this;
    }

    public ExecSpec coreThreads(int coreThreads) {
      this.coreThreads = coreThreads;
      return this;
    }

    public ExecSpec maxThreads(int maxThreads) {
      this.maxThreads = maxThreads;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ExecSpec execSpec = (ExecSpec) o;
      return queueSize == execSpec.queueSize && coreThreads == execSpec.coreThreads
          && maxThreads == execSpec.maxThreads;
    }

    @Override
    public int hashCode() {
      return Objects.hash(queueSize, coreThreads, maxThreads);
    }

    @Override
    public String toString() {
      return "ExecSpec{" +
          "queueSize=" + queueSize +
          ", coreThreads=" + coreThreads +
          ", maxThreads=" + maxThreads +
          '}';
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
