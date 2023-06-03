/*
 * Copyright (c) 2018-2020 Board of Trustees of Leland Stanford Jr. University,
 * all rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Stanford University shall not
 * be used in advertising or otherwise to promote the sale, use or other dealings
 * in this Software without prior written authorization from Stanford University.
 */

package org.lockss.laaws.crawler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.FuncNewContentCrawler.MySimulatedArchivalUnit;
import org.lockss.crawler.FuncNewContentCrawler.MySimulatedPlugin;
import org.lockss.laaws.crawler.model.JobPager;
import org.lockss.laaws.crawler.model.PageInfo;
import org.lockss.log.L4JLogger;
import org.lockss.plugin.PluginTestUtil;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.spring.test.SpringLockssTestCase4;
import org.lockss.util.rest.RestUtil;
import org.lockss.util.rest.crawler.CrawlDesc;
import org.lockss.util.rest.crawler.CrawlJob;
import org.lockss.util.rest.crawler.JobStatus;
import org.lockss.util.rest.crawler.JobStatus.StatusCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** Test class for org.lockss.laaws.crawler.impl.JobsApiServiceImpl. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TestJobsApiServiceImpl extends SpringLockssTestCase4 {

  private static final L4JLogger log = L4JLogger.getLogger();

  private static final String UI_PORT_CONFIGURATION_TEMPLATE = "UiPortConfigTemplate.txt";
  private static final String UI_PORT_CONFIGURATION_FILE = "UiPort.txt";

  // Credentials.
  private final Credentials USER_ADMIN = this.new Credentials("lockss-u", "lockss-p");
  private final Credentials CONTENT_ADMIN =
      this.new Credentials("content-admin", "I'mContentAdmin");
  private final Credentials ACCESS_CONTENT =
      this.new Credentials("access-content", "I'mAccessContent");
  private final Credentials ANYBODY = this.new Credentials("someUser", "somePassword");
  // The application Context used to specify the command line arguments to be
  // used for the tests.
  @Autowired ApplicationContext appCtx;
  // The port that Tomcat is using during this test.
  @LocalServerPort private int port;
  private MySimulatedArchivalUnit sau;

  /** Set up code to be run before all tests. */
  @BeforeClass
  public static void setUpBeforeAllTests() {
  }

  /**
   * Set up code to be run before each test.
   *
   * @throws Exception if there are problems.
   */
  @Before
  public void setUpBeforeEachTest() throws Exception {
    log.debug2("port = {}", port);

    // Set up the temporary directory where the test data will reside.
    setUpTempDirectory(TestJobsApiServiceImpl.class.getCanonicalName());

    // Set up the UI port.
    setUpUiPort(UI_PORT_CONFIGURATION_TEMPLATE, UI_PORT_CONFIGURATION_FILE);

    sau =
        (MySimulatedArchivalUnit)
            PluginTestUtil.createAndStartSimAu(
                MySimulatedPlugin.class, simAuConfig(getTempDirPath()));

    log.trace("Generating tree of size 3x1x2 with 3000 byte files...");
    sau.generateContentTree();

    log.debug2("Done");
  }

  /**
   * Provides the configuration of a simulated Archival Unit.
   *
   * @param rootPath A String with the path where the simulated Archival Unit files will be stored.
   * @return a Configuration with the simulated Archival Unit configuration.
   */
  private Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("depth", "3");
    conf.put("branch", "1");
    conf.put("numFiles", "2");
    conf.put("fileTypes", "" + SimulatedContentGenerator.FILE_TYPE_BIN);
    conf.put("binFileSize", "" + 3000);
    return conf;
  }

  // Can't be part of setUpBeforeEachTest as daemon hasn't been started yet
  private void startAllAusIfNecessary() {
    startAuIfNecessary(sau.getAuId());
  }

  /**
   * Runs the tests with authentication turned off.
   *
   * @throws Exception if there are problems.
   */
  @Test
  public void runUnAuthenticatedTests() throws Exception {
    log.debug2("Invoked");

    // Specify the command line parameters to be used for the tests.
    List<String> cmdLineArgs = getCommandLineArguments();
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/testAuthOff.txt");

    CommandLineRunner runner = appCtx.getBean(CommandLineRunner.class);
    runner.run(cmdLineArgs.toArray(new String[0]));

    startAllAusIfNecessary();

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getJobsUnAuthenticatedTest();
    queueJobUnAuthenticatedTest();
    jobPaginationUnAuthenticatedTest();
    deleteJobsUnAuthenticatedTest();

    log.debug2("Done");
  }

  /**
   * Runs the tests with authentication turned on.
   *
   * @throws Exception if there are problems.
   */
  @Test
  public void runAuthenticatedTests() throws Exception {
    log.debug2("Invoked");

    // Specify the command line parameters to be used for the tests.
    List<String> cmdLineArgs = getCommandLineArguments();
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/testAuthOn.txt");

    CommandLineRunner runner = appCtx.getBean(CommandLineRunner.class);
    runner.run(cmdLineArgs.toArray(new String[0]));

    startAllAusIfNecessary();

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedAuthenticatedTest();
    getJobsAuthenticatedTest();
    queueJobAuthenticatedTest();
    jobPaginationAuthenticatedTest();
    deleteJobsAuthenticatedTest();
    log.debug2("Done");
  }

  /**
   * Runs the tests with crawling disabled.
   *
   * @throws Exception if there are problems.
   */
  @Test
  public void runDisabledTests() throws Exception {
    log.debug2("Invoked");

    // Specify the command line parameters to be used for the tests.
    List<String> cmdLineArgs = getCommandLineArguments();
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/testDisabled.txt");

    CommandLineRunner runner = appCtx.getBean(CommandLineRunner.class);
    runner.run(cmdLineArgs.toArray(new String[0]));

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getJobsDisabledTest();
    log.debug2("Done");
  }

  /**
   * Provides the standard command line arguments to start the server.
   *
   * @return a List<String> with the command line arguments.
   */
  private List<String> getCommandLineArguments() {
    log.debug2("Invoked");

    List<String> cmdLineArgs = new ArrayList<>();
    cmdLineArgs.add("-p");
    cmdLineArgs.add(getPlatformDiskSpaceConfigPath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("config/common.xml");

    File folder = new File(new File(new File(getTempDirPath()), "jobs"), "prod");
    log.trace("folder = {}", folder);

    cmdLineArgs.add("-x");
    cmdLineArgs.add(folder.getAbsolutePath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/lockss.txt");
    cmdLineArgs.add("-p");
    cmdLineArgs.add(getUiPortConfigFile().getAbsolutePath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/lockss.opt");

    log.debug2("cmdLineArgs = {}", cmdLineArgs);
    return cmdLineArgs;
  }

  /**
   * Provides the URL template to be tested.
   *
   * @param pathAndQueryParams A String with the path and query parameters of the URL template to be
   * tested.
   * @return a String with the URL template to be tested.
   */
  private String getTestUrlTemplate(String pathAndQueryParams) {
    return "http://localhost:" + port + pathAndQueryParams;
  }

  /** Runs the invalid method-related un-authenticated-specific tests. */
  private void runMethodsNotAllowedUnAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(null, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    // Empty job ID.
    runTestMethodNotAllowed(ANYBODY, HttpMethod.PATCH, HttpStatus.METHOD_NOT_ALLOWED);

    // Unknown job ID.
    runTestMethodNotAllowed(ANYBODY, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(null, HttpMethod.PATCH, HttpStatus.METHOD_NOT_ALLOWED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getJobs()-related tests with crawling disabled.
   *
   * @throws Exception if there are problems.
   */
  private void getJobsDisabledTest() throws Exception {
    log.debug2("Invoked");

    JobPager jobPager = runTestGetJobs(null, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 0);

    jobPager = runTestGetJobs(ANYBODY, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 0);

    log.debug2("Done");
  }

  /**
   * Performs an operation using a method that is not allowed.
   *
   * @param credentials A Credentials with the request credentials.
   * @param method An HttpMethod with the request method.
   * @param expectedStatus An HttpStatus with the HTTP status of the result.
   */
  private void runTestMethodNotAllowed(
      Credentials credentials, HttpMethod method, HttpStatus expectedStatus) {
    log.debug2("credentials = {}", credentials);
    log.debug2("method = {}", method);
    log.debug2("expectedStatus = {}", expectedStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/jobs");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template)
            .build();

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplateBuilder templateBuilder = RestUtil.getRestTemplateBuilder(0, 0);

    HttpEntity<String> requestEntity = null;

    // Get the individual credentials elements.
    String user = null;
    String password = null;

    if (credentials != null) {
      user = credentials.getUser();
      password = credentials.getPassword();
    }

    // Check whether there are any custom headers to be specified in the
    // request.
    if (user != null || password != null) {

      // Initialize the request headers.
      HttpHeaders headers = new HttpHeaders();

      // Set up the authentication credentials, if necessary.
      if (credentials != null) {
        credentials.setUpBasicAuthentication(headers);
      }

      log.trace("requestHeaders = {}", () -> headers.toSingleValueMap());

      // Create the request entity.
      requestEntity = new HttpEntity<>(null, headers);
    }

    // Make the request and get the response.
    ResponseEntity<String> response =
        new TestRestTemplate(templateBuilder).exchange(uri, method, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertFalse(RestUtil.isSuccess(statusCode));
    assertEquals(expectedStatus, statusCode);
  }

  /** Runs the invalid method-related authentication-independent tests. */
  private void runMethodsNotAllowedCommonTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(USER_ADMIN, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    // Empty job ID.
    runTestMethodNotAllowed(CONTENT_ADMIN, HttpMethod.PATCH, HttpStatus.METHOD_NOT_ALLOWED);

    // Unknown job ID.
    runTestMethodNotAllowed(
        ACCESS_CONTENT, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(USER_ADMIN, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for the jobs of the system.
   *
   * @param credentials A Credentials with the request credentials.
   * @param limit An Integer with the maximum number of jobs to be returned.
   * @param continuationToken A String with the continuation token of the next page of jobs to be
   * returned.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a JobPager with the jobs.
   * @throws Exception if there are problems.
   */
  private JobPager runTestGetJobs(
      Credentials credentials,
      Integer limit,
      String continuationToken,
      HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("credentials = {}", credentials);
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/jobs");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(template).build();

    UriComponentsBuilder ucb = UriComponentsBuilder.newInstance().uriComponents(uriComponents);

    if (limit != null) {
      ucb.queryParam("limit", limit);
    }

    if (continuationToken != null) {
      ucb.queryParam("continuationToken", continuationToken);
    }

    URI uri = ucb.build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplateBuilder templateBuilder = RestUtil.getRestTemplateBuilder(0, 0);

    HttpEntity<String> requestEntity = null;

    // Get the individual credentials elements.
    String user = null;
    String password = null;

    if (credentials != null) {
      user = credentials.getUser();
      password = credentials.getPassword();
    }

    // Check whether there are any custom headers to be specified in the
    // request.
    if (user != null || password != null) {

      // Initialize the request headers.
      HttpHeaders headers = new HttpHeaders();

      // Set up the authentication credentials, if necessary.
      if (credentials != null) {
        credentials.setUpBasicAuthentication(headers);
      }

      log.trace("requestHeaders = {}", () -> headers.toSingleValueMap());

      // Create the request entity.
      requestEntity = new HttpEntity<>(null, headers);
    }

    // Make the request and get the response.
    ResponseEntity<String> response =
        new TestRestTemplate(templateBuilder)
            .exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    JobPager result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), JobPager.class);
    }

    log.debug2("result = {}", result);
    return result;
  }

  /**
   * Validates a list of jobs.
   *
   * @param jobPager A JobPager with the list of jobs to be validated.
   * @param limit An Integer with the limit used for the query, or null if no limit was specified,
   * @param expectedCount An int with the expected count of jobs in the list, or a negative number
   * to avoid validating the count of jobs.
   * @return an int with the count of jobs in the list.
   */
  private int validateGetJobsResult(JobPager jobPager, Integer limit, int expectedCount) {
    log.debug2("jobPager = {}", jobPager);
    log.debug2("limit = {}", limit);
    log.debug2("expectedCount = {}", expectedCount);

    int actualLimit = limit == null ? 50 : limit;

    // Validate the pagination information.
    PageInfo pageInfo = jobPager.getPageInfo();
    assertEquals(actualLimit, pageInfo.getResultsPerPage().intValue());

    if (expectedCount >= 0) {
      assertEquals(expectedCount, pageInfo.getTotalCount().intValue());

      if (expectedCount < actualLimit) {
        assertNull(pageInfo.getContinuationToken());
        assertNull(pageInfo.getNextLink());
      }
    }

    // Get the list of jobs.
    List<CrawlJob> jobs = jobPager.getJobs();
    int jobCount = jobs.size();

    if (expectedCount >= 0) {
      if (limit == null) {
        assertEquals(expectedCount, jobCount);
      }
      else {
        assertTrue(expectedCount >= jobCount);
      }
    }

    // Loop through all the jobs.
    for (int index = 0; index < jobCount; index++) {
      // Validate this crawl.
      CrawlJob crawlJob = jobs.get(index);
      assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());
      JobStatus status = crawlJob.getJobStatus();
      StatusCodeEnum code = status.getStatusCode();

      // Only the last crawl may be in an Active status, as they are all for the
      // same Archival Unit..
      if (index < jobCount - 1) {
        assertTrue(code == StatusCodeEnum.SUCCESSFUL || code == StatusCodeEnum.ABORTED);
        if (code == StatusCodeEnum.SUCCESSFUL) {
          assertTrue(
              "Successful".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        }
        else {
          assertTrue(
              "Aborted".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        }
      }
      else {
        assertTrue(
            code == StatusCodeEnum.QUEUED
                || code == StatusCodeEnum.ACTIVE
                || code == StatusCodeEnum.SUCCESSFUL);
        if (code == StatusCodeEnum.QUEUED) {
          assertEquals("Pending", status.getMsg());
        }
        else if (code == StatusCodeEnum.ACTIVE) {
          assertEquals("Active", status.getMsg());
        }
        else {
          assertTrue(
              "Successful".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        }
      }
    }

    return jobCount;
  }

  /** Runs the invalid method-related authenticated-specific tests. */
  private void runMethodsNotAllowedAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(ANYBODY, HttpMethod.PUT, HttpStatus.UNAUTHORIZED);

    // Empty job ID.
    runTestMethodNotAllowed(null, HttpMethod.PATCH, HttpStatus.UNAUTHORIZED);

    // Unknown job ID.
    runTestMethodNotAllowed(ANYBODY, HttpMethod.PUT, HttpStatus.UNAUTHORIZED);

    // No credentials.
    runTestMethodNotAllowed(null, HttpMethod.PATCH, HttpStatus.UNAUTHORIZED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getJobs()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getJobsUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    JobPager jobPager = runTestGetJobs(null, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 1);

    jobPager = runTestGetJobs(ANYBODY, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 1);

    getJobsCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getJobs()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getJobsAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestGetJobs(null, null, null, HttpStatus.UNAUTHORIZED);
    runTestGetJobs(ANYBODY, null, null, HttpStatus.UNAUTHORIZED);

    getJobsCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getJobs()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void getJobsCommonTest() throws Exception {
    log.debug2("Invoked");

    JobPager jobPager = runTestGetJobs(USER_ADMIN, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 1);

    jobPager = runTestGetJobs(CONTENT_ADMIN, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 1);

    log.debug2("Done");
  }

  /**
   * Runs the queueJob()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void queueJobUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestQueueJob(null, null, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc(), ANYBODY, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc().auId(sau.getAuId()), null, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc(), ANYBODY, HttpStatus.BAD_REQUEST);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId())
        .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);

    runTestQueueJob(crawlDesc, null, HttpStatus.BAD_REQUEST);
    crawlDesc.forceCrawl(true);
    CrawlJob crawlJob = runTestQueueJob(crawlDesc, null, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    JobPager jobPager = runTestGetJobs(null, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 2);

    crawlJob = runTestQueueJob(crawlDesc, ANYBODY, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    jobPager = runTestGetJobs(ANYBODY, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, 3);

    queueJobCommonTest("classic");
    //  queueJobCommonTest(("wget"));

    log.debug2("Done");
  }

  /**
   * Runs the queueJob()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void queueJobAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestQueueJob(null, null, HttpStatus.UNAUTHORIZED);
    runTestQueueJob(new CrawlDesc(), ANYBODY, HttpStatus.UNAUTHORIZED);
    runTestQueueJob(new CrawlDesc().auId(sau.getAuId()), null, HttpStatus.UNAUTHORIZED);
    runTestQueueJob(new CrawlDesc(), ANYBODY, HttpStatus.UNAUTHORIZED);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId());

    runTestQueueJob(crawlDesc, null, HttpStatus.UNAUTHORIZED);

    queueJobCommonTest("classic");
//    queueJobCommonTest(("wget"));

    log.debug2("Done");
  }

  /**
   * Runs the queueJob()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void queueJobCommonTest(String crawlerId) throws Exception {
    log.debug2("Invoked");

    runTestQueueJob(null, USER_ADMIN, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc(), CONTENT_ADMIN, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc().auId(sau.getAuId()), USER_ADMIN, HttpStatus.BAD_REQUEST);
    runTestQueueJob(new CrawlDesc(), CONTENT_ADMIN, HttpStatus.BAD_REQUEST);

    JobPager jobPager = runTestGetJobs(USER_ADMIN, null, null, HttpStatus.OK);
    int jobCount = validateGetJobsResult(jobPager, null, -1);

    CrawlDesc crawlDesc = new CrawlDesc()
        .auId(sau.getAuId())
        .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT)
        .crawlerId(crawlerId);

    runTestQueueJob(crawlDesc, USER_ADMIN, HttpStatus.BAD_REQUEST);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestQueueJob(crawlDesc, CONTENT_ADMIN, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    jobPager = runTestGetJobs(CONTENT_ADMIN, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, ++jobCount);

    crawlJob = runTestQueueJob(crawlDesc, USER_ADMIN, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    jobPager = runTestGetJobs(USER_ADMIN, null, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, null, ++jobCount);

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void jobPaginationUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    JobPager jobPager = runTestGetJobs(null, null, null, HttpStatus.OK);
    int jobCount = validateGetJobsResult(jobPager, null, -1);
    assertEquals(5, jobCount);

    List<String> jobIds = new ArrayList<>(jobCount);

    for (int index = 0; index < jobCount; index++) {
      jobIds.add(jobPager.getJobs().get(index).getJobId());
    }

    int pageSize = 2;
    int remainingJobCount = jobCount;

    jobPager = runTestGetJobs(ANYBODY, pageSize, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, pageSize, jobCount);

    assertEquals(jobIds.get(0), jobPager.getJobs().get(0).getJobId());
    assertEquals(jobIds.get(1), jobPager.getJobs().get(1).getJobId());

    String continuationToken = jobPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingJobCount = remainingJobCount - pageSize;

    jobPager = runTestGetJobs(null, pageSize, continuationToken, HttpStatus.OK);
    validateGetJobsResult(jobPager, pageSize, jobCount);

    assertEquals(jobIds.get(2), jobPager.getJobs().get(0).getJobId());
    assertEquals(jobIds.get(3), jobPager.getJobs().get(1).getJobId());

    continuationToken = jobPager.getPageInfo().getContinuationToken();
    remainingJobCount = remainingJobCount - pageSize;

    if (remainingJobCount == 0) {
      assertNull(continuationToken);
    }
    else {
      assertNotNull(continuationToken);

      jobPager = runTestGetJobs(ANYBODY, pageSize, continuationToken, HttpStatus.OK);
      validateGetJobsResult(jobPager, pageSize, jobCount);

      assertEquals(jobIds.get(4), jobPager.getJobs().get(0).getJobId());

      continuationToken = jobPager.getPageInfo().getContinuationToken();
      assertNull(continuationToken);
    }

    jobPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void jobPaginationAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    jobPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void jobPaginationCommonTest() throws Exception {
    log.debug2("Invoked");

    JobPager jobPager = runTestGetJobs(USER_ADMIN, null, null, HttpStatus.OK);
    int jobCount = validateGetJobsResult(jobPager, null, -1);

    List<String> jobIds = new ArrayList<>(jobCount);

    for (int index = 0; index < jobCount; index++) {
      jobIds.add(jobPager.getJobs().get(index).getJobId());
    }

    int pageSize = 2;
    int remainingJobCount = jobCount;

    jobPager = runTestGetJobs(CONTENT_ADMIN, pageSize, null, HttpStatus.OK);
    validateGetJobsResult(jobPager, pageSize, jobCount);

    assertEquals(jobIds.get(0), jobPager.getJobs().get(0).getJobId());
    assertEquals(jobIds.get(1), jobPager.getJobs().get(1).getJobId());

    String continuationToken = jobPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingJobCount = remainingJobCount - pageSize;

    jobPager = runTestGetJobs(USER_ADMIN, pageSize, continuationToken, HttpStatus.OK);
    validateGetJobsResult(jobPager, pageSize, jobCount);

    assertEquals(jobIds.get(2), jobPager.getJobs().get(0).getJobId());

    if (remainingJobCount == 1) {
      continuationToken = jobPager.getPageInfo().getContinuationToken();
      assertNull(continuationToken);
    }
    else {
      assertEquals(jobIds.get(3), jobPager.getJobs().get(1).getJobId());

      continuationToken = jobPager.getPageInfo().getContinuationToken();
      remainingJobCount = remainingJobCount - pageSize;

      if (remainingJobCount == 0) {
        assertNull(continuationToken);
      }
      else {
        assertNotNull(continuationToken);

        jobPager = runTestGetJobs(CONTENT_ADMIN, pageSize, continuationToken, HttpStatus.OK);
        validateGetJobsResult(jobPager, pageSize, jobCount);

        assertEquals(jobIds.get(4), jobPager.getJobs().get(0).getJobId());

        continuationToken = jobPager.getPageInfo().getContinuationToken();
        assertNull(continuationToken);
      }
    }

    log.debug2("Done");
  }

  /**
   * Runs the deleteJobs()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteJobsUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");
    // we don't treat an empty jobs list as an error.
    //runTestDeleteJobs(null, HttpStatus.NOT_FOUND);
    //runTestDeleteJobs(ANYBODY, HttpStatus.NOT_FOUND);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId())
        .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestQueueJob(crawlDesc, null, HttpStatus.ACCEPTED);

    runTestDeleteJobs(null, HttpStatus.OK);

    crawlJob = runTestQueueJob(crawlDesc, ANYBODY, HttpStatus.ACCEPTED);
    runTestDeleteJobs(ANYBODY, HttpStatus.OK);

    deleteJobsCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the deleteJobs()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteJobsAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestDeleteJobs(null, HttpStatus.UNAUTHORIZED);
    runTestDeleteJobs(ANYBODY, HttpStatus.UNAUTHORIZED);
    runTestDeleteJobs(null, HttpStatus.UNAUTHORIZED);

    deleteJobsCommonTest();

    log.debug2("Done");
  }

  /**
   * Performs a DELETE operation for all crawll.
   *
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @throws Exception if there are problems.
   */
  private void runTestDeleteJobs(Credentials credentials, HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/jobs");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template)
            .build();

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplateBuilder templateBuilder = RestUtil.getRestTemplateBuilder(0, 0);

    HttpEntity<Void> requestEntity = null;

    // Get the individual credentials elements.
    String user = null;
    String password = null;

    if (credentials != null) {
      user = credentials.getUser();
      password = credentials.getPassword();
    }

    // Check whether there are any custom headers to be specified in the
    // request.
    if (user != null || password != null) {

      // Initialize the request headers.
      HttpHeaders headers = new HttpHeaders();

      // Set up the authentication credentials, if necessary.
      if (credentials != null) {
        credentials.setUpBasicAuthentication(headers);
      }

      log.trace("requestHeaders = {}", () -> headers.toSingleValueMap());

      // Create the request entity.
      requestEntity = new HttpEntity<>(null, headers);
    }

    // Make the request and get the response.
    ResponseEntity<Void> response =
        new TestRestTemplate(templateBuilder)
            .exchange(uri, HttpMethod.DELETE, requestEntity, Void.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);
//    Void result = null;
//
//    if (RestUtil.isSuccess(statusCode)) {
//      result = new ObjectMapper().readValue(response.getBody().toString(), Void.class);
//    }
  }

  /**
   * Runs the deleteJobs()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteJobsCommonTest() throws Exception {
    log.debug2("Invoked");

//    runTestDeleteJobs(USER_ADMIN, HttpStatus.NOT_FOUND);
//    runTestDeleteJobs(CONTENT_ADMIN, HttpStatus.NOT_FOUND);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId())
        .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestQueueJob(crawlDesc, USER_ADMIN, HttpStatus.ACCEPTED);
    runTestDeleteJobs(USER_ADMIN, HttpStatus.OK);

    crawlJob = runTestQueueJob(crawlDesc, CONTENT_ADMIN, HttpStatus.ACCEPTED);
    runTestDeleteJobs(CONTENT_ADMIN, HttpStatus.OK);

    log.debug2("Done");
  }

  /**
   * Performs a POST operation to perform a crawl.
   *
   * @param crawlDesc A CrawlDesc with the description of the crawl to be performed.
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a JobPager with the jobs.
   * @throws Exception if there are problems.
   */
  private CrawlJob runTestQueueJob(
      CrawlDesc crawlDesc, Credentials credentials, HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("crawlDesc = {}", crawlDesc);
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    ResponseEntity<String> response = runTestQueueJobWithWait(crawlDesc, credentials);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlJob result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), CrawlJob.class);
    }

    log.debug2("result = {}", result);
    return result;
  }

  /**
   * Performs a POST operation to perform a crawl, waiting if the involved Archival Unit is
   * currently being crawled.
   *
   * @param crawlDesc A CrawlDesc with the description of the crawl to be performed.
   * @param credentials A Credentials with the request credentials.
   * @return a JobPager with the jobs.
   * @throws Exception if there are problems.
   */
  private ResponseEntity<String> runTestQueueJobWithWait(
      CrawlDesc crawlDesc, Credentials credentials) throws Exception {
    log.debug("crawlDesc = {}", crawlDesc);
    log.debug("credentials = {}", credentials);

    // Get the test URL template.
    String template = getTestUrlTemplate("/jobs");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(template).build();

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplateBuilder templateBuilder = RestUtil.getRestTemplateBuilder(0, 0);

    HttpEntity<CrawlDesc> requestEntity;

    // Get the individual credentials elements.
    String user = null;
    String password = null;

    if (credentials != null) {
      user = credentials.getUser();
      password = credentials.getPassword();
    }

    // Check whether there are any custom headers to be specified in the
    // request.
    if (user != null || password != null) {
      // Yes: Initialize the request headers.
      HttpHeaders headers = new HttpHeaders();

      // Set up the authentication credentials, if necessary.
      if (credentials != null) {
        credentials.setUpBasicAuthentication(headers);
      }

      log.trace("requestHeaders = {}", () -> headers.toSingleValueMap());

      // Create the request entity.
      requestEntity = new HttpEntity<>(crawlDesc, headers);
    }
    else {
      // No: Create the request entity.
      requestEntity = new HttpEntity<>(crawlDesc);
    }

    ResponseEntity<String> response = null;
    boolean done = false;

    // Loop while the reported error is that the Archival Unit is being crawled.
    while (!done) {
      // Make the request and get the response.
      response =
          new TestRestTemplate(templateBuilder)
              .exchange(uri, HttpMethod.POST, requestEntity, String.class);

      // Get the response status.
      HttpStatus statusCode = response.getStatusCode();

      // Check whether the response status is not the one that corresponds to
      // the Archival Unit being crawled.
      if (statusCode != HttpStatus.BAD_REQUEST) {
        // Yes: No need to try again.
        break;
      }

      // Get the result.
      CrawlJob result;

      try {
        result = new ObjectMapper().readValue(response.getBody(), CrawlJob.class);
      }
      catch (Exception e) {
        // It is not the situation where the Archival Unit is being crawled: No
        // need to try again.
        break;
      }

      // Check whether the status message indicates that the Archival Unit is
      // being crawled.
      if (result != null
          && result.getJobStatus() != null
          && result.getJobStatus().getMsg().contains("AU is crawling now.")) {
        // Yes: Try again.
        try {
          Thread.sleep(500);
        }
        catch (InterruptedException ie) {
        }
        continue;
      }

      // No: No need to try again.
      done = true;
    }

    log.debug2("response = {}", response);
    return response;
  }
}