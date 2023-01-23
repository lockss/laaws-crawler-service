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
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.app.LockssApp;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.crawler.CrawlManager;
import org.lockss.crawler.CrawlManagerImpl;
import org.lockss.crawler.FuncNewContentCrawler.MySimulatedArchivalUnit;
import org.lockss.crawler.FuncNewContentCrawler.MySimulatedPlugin;
import org.lockss.laaws.crawler.model.*;
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
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** Test class for org.lockss.laaws.crawler.impl.CrawlsApiServiceImpl. */

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class TestCrawlsApiServiceImpl extends SpringLockssTestCase4 {
  private static final L4JLogger log = L4JLogger.getLogger();

  private static final String UI_PORT_CONFIGURATION_TEMPLATE = "UiPortConfigTemplate.txt";
  private static final String UI_PORT_CONFIGURATION_FILE = "UiPort.txt";

  private static final String EMPTY_STRING = "";
  private static final String BOGUS_ID = "bogus";

  // Credentials.
  private final Credentials USER_ADMIN = this.new Credentials("lockss-u", "lockss-p");
  private final Credentials CONTENT_ADMIN =
      this.new Credentials("content-admin", "I'mContentAdmin");
  private final Credentials ACCESS_CONTENT =
      this.new Credentials("access-content", "I'mAccessContent");
  private final Credentials ANYBODY = this.new Credentials("someUser", "somePassword");

  // The port that Tomcat is using during this test.
  @LocalServerPort private int port;

  // The application Context used to specify the command line arguments to be
  // used for the tests.
  @Autowired private ApplicationContext appCtx;
  private CrawlManagerImpl cmi;

  private MySimulatedArchivalUnit sau;

  /** Set up code to be run before all tests. */
  @BeforeClass
  public static void setUpBeforeAllTests() {}

  /**
   * Set up code to be run before each test.
   *
   * @throws Exception if there are problems.
   */
  @Before
  public void setUpBeforeEachTest() throws Exception {
    log.debug2("port = {}", port);

    // Set up the temporary directory where the test data will reside.
    setUpTempDirectory(TestCrawlsApiServiceImpl.class.getCanonicalName());

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

  @After
  public void cleanupAfterEachTest() {
    if(cmi != null) {
      cmi.deleteAllCrawls();
      cmi.stopService();
    }
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

    Configuration config = ConfigManager.getCurrentConfig();
    assertTrue(config.getBoolean(CrawlManagerImpl.PARAM_CRAWLER_ENABLED));
    cmi = (CrawlManagerImpl) LockssApp.getManagerByTypeStatic(CrawlManager.class);
    assertTrue(cmi.isCrawlerEnabled());

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getCrawlsUnAuthenticatedTest();
    doCrawlUnAuthenticatedTest();
    getCrawlByIdUnAuthenticatedTest();
    crawlPaginationUnAuthenticatedTest();
    urlPaginationUnAuthenticatedTest();
    deleteCrawlByIdUnAuthenticatedTest();
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
    Configuration config = ConfigManager.getCurrentConfig();
    assertTrue(config.getBoolean(CrawlManagerImpl.PARAM_CRAWLER_ENABLED));
   cmi = (CrawlManagerImpl) LockssApp.getManagerByTypeStatic(CrawlManager.class);

    assertTrue(cmi.isCrawlerEnabled());

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedAuthenticatedTest();
    getCrawlsAuthenticatedTest();
    doCrawlAuthenticatedTest();
    getCrawlByIdAuthenticatedTest();
    crawlPaginationAuthenticatedTest();
    urlPaginationAuthenticatedTest();
    deleteCrawlByIdAuthenticatedTest();
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
    runner.run(cmdLineArgs.toArray(new String[cmdLineArgs.size()]));
    Configuration config = ConfigManager.getCurrentConfig();
    assertFalse(config.getBoolean(CrawlManagerImpl.PARAM_CRAWLER_ENABLED));
    cmi = ApiUtils.getLockssCrawlManager();
    assertFalse(cmi.isCrawlerEnabled());

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getCrawlsDisabledTest();

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

  /**
   * Provides the standard command line arguments to start the server.
   *
   * @return a List<String> with the command line arguments.
   * @throws IOException if there are problems.
   */
  private List<String> getCommandLineArguments() {
    log.debug2("Invoked");

    List<String> cmdLineArgs = new ArrayList<>();
    cmdLineArgs.add("-p");
    cmdLineArgs.add(getPlatformDiskSpaceConfigPath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("config/common.xml");

    File folder = new File(new File(new File(getTempDirPath()), "tdbxml"), "prod");
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

  /** Runs the invalid method-related un-authenticated-specific tests. */
  private void runMethodsNotAllowedUnAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(null, null, HttpMethod.PUT, HttpStatus.NOT_FOUND);

    // Empty job ID.
    runTestMethodNotAllowed(EMPTY_STRING, ANYBODY, HttpMethod.PATCH, HttpStatus.NOT_FOUND);

    // Unknown job ID.
    runTestMethodNotAllowed(BOGUS_ID, ANYBODY, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(BOGUS_ID, null, HttpMethod.PATCH, HttpStatus.METHOD_NOT_ALLOWED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /** Runs the invalid method-related authenticated-specific tests. */
  private void runMethodsNotAllowedAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(null, ANYBODY, HttpMethod.PUT, HttpStatus.UNAUTHORIZED);

    // Empty job ID.
    runTestMethodNotAllowed(EMPTY_STRING, null, HttpMethod.PATCH, HttpStatus.UNAUTHORIZED);

    // Unknown job ID.
    runTestMethodNotAllowed(BOGUS_ID, ANYBODY, HttpMethod.PUT, HttpStatus.UNAUTHORIZED);

    // No credentials.
    runTestMethodNotAllowed(BOGUS_ID, null, HttpMethod.PATCH, HttpStatus.UNAUTHORIZED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /** Runs the invalid method-related authentication-independent tests. */
  private void runMethodsNotAllowedCommonTest() {
    log.debug2("Invoked");

    // Missing job ID.
    runTestMethodNotAllowed(null, USER_ADMIN, HttpMethod.PUT, HttpStatus.NOT_FOUND);

    // Empty job ID.
    runTestMethodNotAllowed(EMPTY_STRING, CONTENT_ADMIN, HttpMethod.PATCH, HttpStatus.NOT_FOUND);

    // Unknown job ID.
    runTestMethodNotAllowed(
        BOGUS_ID, ACCESS_CONTENT, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(BOGUS_ID, USER_ADMIN, HttpMethod.PUT, HttpStatus.METHOD_NOT_ALLOWED);

    log.debug2("Done");
  }

  /**
   * Performs an operation using a method that is not allowed.
   *
   * @param jobId A String with the identifier of the job.
   * @param credentials A Credentials with the request credentials.
   * @param method An HttpMethod with the request method.
   * @param expectedStatus An HttpStatus with the HTTP status of the result.
   */
  private void runTestMethodNotAllowed(
      String jobId, Credentials credentials, HttpMethod method, HttpStatus expectedStatus) {
    log.debug2("jobId = {}", jobId);
    log.debug2("credentials = {}", credentials);
    log.debug2("method = {}", method);
    log.debug2("expectedStatus = {}", expectedStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawls/{jobId}");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template)
            .build()
            .expand(Collections.singletonMap("jobId", jobId));

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
        new TestRestTemplate(restTemplate).exchange(uri, method, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertFalse(RestUtil.isSuccess(statusCode));
    assertEquals(expectedStatus, statusCode);
  }

  /**
   * Runs the getCrawls()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlsUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestGetCrawls(null, null, null, HttpStatus.OK);
    runTestGetCrawls(ANYBODY, null, null, HttpStatus.OK);

    getCrawlsCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawls()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlsAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestGetCrawls(null, null, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawls(ANYBODY, null, null, HttpStatus.UNAUTHORIZED);

    getCrawlsCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawls()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlsCommonTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 1);

    crawlPager = runTestGetCrawls(CONTENT_ADMIN, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 1);

    log.debug2("Done");
  }

  /**
   * Runs the getCrawls()-related tests with crawling disabled.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlsDisabledTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(null, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 0);

    crawlPager = runTestGetCrawls(ANYBODY, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 0);

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for the crawls of the system.
   *
   * @param credentials A Credentials with the request credentials.
   * @param limit An Integer with the maximum number of crawls to be returned.
   * @param continuationToken A String with the continuation token of the next page of crawls to be
   *     returned.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlPager with the crawls.
   * @throws Exception if there are problems.
   */
  private CrawlPager runTestGetCrawls(
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
    String template = getTestUrlTemplate("/crawls");

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
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
        new TestRestTemplate(restTemplate)
            .exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlPager result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), CrawlPager.class);
    }

    log.debug2("result = {}", result);
    return result;
  }

  /**
   * Validates a list of crawls.
   *
   * @param crawlPager A CrawlPager with the list of crawls to be validated.
   * @param limit An Integer with the limit used for the query, or null if no limit was specified,
   * @param expectedCount An int with the expected count of crawls in the list, or a negative number
   *     to avoid validating the count of crawls.
   * @return an int with the count of crawls in the list.
   */
  private int validateGetCrawlsResult(CrawlPager crawlPager, Integer limit, int expectedCount) {
    log.debug2("crawlPager = {}", crawlPager);
    log.debug2("limit = {}", limit);
    log.debug2("expectedCount = {}", expectedCount);

    int actualLimit = limit == null ? 50 : limit;

    // Validate the pagination information.
    PageInfo pageInfo = crawlPager.getPageInfo();
    assertEquals(actualLimit, pageInfo.getResultsPerPage().intValue());

    if (expectedCount >= 0) {
      assertEquals(expectedCount, pageInfo.getTotalCount().intValue());

      if (expectedCount < actualLimit) {
        assertNull(pageInfo.getContinuationToken());
        assertNull(pageInfo.getNextLink());
      }
    }

    // Get the list of crawls.
    List<CrawlStatus> crawls = crawlPager.getCrawls();
    int crawlCount = crawls.size();

    if (expectedCount >= 0) {
      if (limit == null) {
        assertEquals(expectedCount, crawlCount);
      } else {
        assertTrue(expectedCount >= crawlCount);
      }
    }

    // Loop through all the crawls.
    for (int index = 0; index < crawlCount; index++) {
      // Validate this crawl.
      CrawlStatus crawlStatus = crawls.get(index);
      assertEquals(sau.getAuId(), crawlStatus.getAuId());
      JobStatus status = crawlStatus.getJobStatus();
      StatusCodeEnum code = status.getStatusCode();

      // Only the last crawl may be in an Active status, as they are all for the
      // same Archival Unit..
      if (index < crawlCount - 1) {
        assertTrue(code == StatusCodeEnum.SUCCESSFUL || code == StatusCodeEnum.ABORTED);
        if (code == StatusCodeEnum.SUCCESSFUL) {
          assertTrue(
              "Successful".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        } else {
          assertTrue(
              "Aborted".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        }
      } else {
        assertTrue(
            code == StatusCodeEnum.QUEUED
                || code == StatusCodeEnum.ACTIVE
                || code == StatusCodeEnum.SUCCESSFUL);
        if (code == StatusCodeEnum.QUEUED) {
          assertEquals("Pending", status.getMsg());
        } else if (code == StatusCodeEnum.ACTIVE) {
          assertEquals("Active", status.getMsg());
        } else {
          assertTrue(
              "Successful".equals(status.getMsg())
                  || "Crawl aborted before start".equals(status.getMsg()));
        }
      }
    }

    return crawlCount;
  }

  /**
   * Runs the doCrawl()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void doCrawlUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestDoCrawl(null, null, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc(), ANYBODY, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc().auId(sau.getAuId()), null, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc(), ANYBODY, HttpStatus.BAD_REQUEST);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId()).crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);

    runTestDoCrawl(crawlDesc, null, HttpStatus.BAD_REQUEST);
    crawlDesc.forceCrawl(true);
    CrawlJob crawlJob = runTestDoCrawl(crawlDesc, null, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    CrawlPager crawlPager = runTestGetCrawls(null, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 2);

    crawlJob = runTestDoCrawl(crawlDesc, ANYBODY, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    crawlPager = runTestGetCrawls(ANYBODY, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, 3);

    doCrawlCommonTest("lockss");
  //  doCrawlCommonTest(("wget"));

    log.debug2("Done");
  }

  /**
   * Runs the doCrawl()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void doCrawlAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestDoCrawl(null, null, HttpStatus.UNAUTHORIZED);
    runTestDoCrawl(new CrawlDesc(), ANYBODY, HttpStatus.UNAUTHORIZED);
    runTestDoCrawl(new CrawlDesc().auId(sau.getAuId()), null, HttpStatus.UNAUTHORIZED);
    runTestDoCrawl(new CrawlDesc(), ANYBODY, HttpStatus.UNAUTHORIZED);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId());

    runTestDoCrawl(crawlDesc, null, HttpStatus.UNAUTHORIZED);

    doCrawlCommonTest("lockss");
   // doCrawlCommonTest(("wget"));

    log.debug2("Done");
  }

  /**
   * Runs the doCrawl()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void doCrawlCommonTest(String crawlerId) throws Exception {
    log.debug2("Invoked");

    runTestDoCrawl(null, USER_ADMIN, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc(), CONTENT_ADMIN, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc().auId(sau.getAuId()), USER_ADMIN, HttpStatus.BAD_REQUEST);
    runTestDoCrawl(new CrawlDesc(), CONTENT_ADMIN, HttpStatus.BAD_REQUEST);

    CrawlPager crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    int jobCount = validateGetCrawlsResult(crawlPager, null, -1);

    CrawlDesc crawlDesc = new CrawlDesc()
      .auId(sau.getAuId())
      .crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT)
      .crawlerId(crawlerId);

    runTestDoCrawl(crawlDesc, USER_ADMIN, HttpStatus.BAD_REQUEST);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestDoCrawl(crawlDesc, CONTENT_ADMIN, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    crawlPager = runTestGetCrawls(CONTENT_ADMIN, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, ++jobCount);

    crawlJob = runTestDoCrawl(crawlDesc, USER_ADMIN, HttpStatus.ACCEPTED);
    assertEquals(sau.getAuId(), crawlJob.getCrawlDesc().getAuId());

    crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, null, ++jobCount);

    log.debug2("Done");
  }


  /**
   * Performs a POST operation to perform a crawl.
   *
   * @param crawlDesc A CrawlDesc with the description of the crawl to be performed.
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlJob for the crawl.
   * @throws Exception if there are problems.
   */
  private CrawlJob runTestDoCrawl(
      CrawlDesc crawlDesc, Credentials credentials, HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("crawlDesc = {}", crawlDesc);
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    ResponseEntity<String> response = runTestDoCrawlWithWait(crawlDesc, credentials);

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
   * @return a CrawlJob or error msg as string entity.
   * @throws Exception if there are problems.
   */
  private ResponseEntity<String> runTestDoCrawlWithWait(
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
    RestTemplate restTemplate = RestUtil.getRestTemplate();

    HttpEntity<CrawlDesc> requestEntity = null;

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
    } else {
      // No: Create the request entity.
      requestEntity = new HttpEntity<>(crawlDesc);
    }

    ResponseEntity<String> response = null;
    boolean done = false;

    // Loop while the reported error is that the Archival Unit is being crawled.
    while (!done) {
      // Make the request and get the response.
      response =
          new TestRestTemplate(restTemplate)
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
      } catch (Exception e) {
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
        } catch (InterruptedException ie) {
        }
        continue;
      }

      // No: No need to try again.
      done = true;
    }

    log.debug2("response = {}", response);
    return response;
  }

  /**
   * Runs the getCrawlById()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlByIdUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(null, null, null, HttpStatus.OK);
    int jobCount = validateGetCrawlsResult(crawlPager, null, -1);

    for (int index = 0; index < jobCount; index++) {
      Credentials credentials = null;

      if (index % 2 == 1) {
        credentials = ANYBODY;
      }

      String jobId = crawlPager.getCrawls().get(index).getJobId();

      CrawlStatus crawlStatus = runTestGetCrawlById(jobId, credentials, HttpStatus.OK);

      validateGetCrawlByIdResult(crawlStatus);
    }

    getCrawlByIdCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlById()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlByIdAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestGetCrawlById(null, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlById(EMPTY_STRING, ANYBODY, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlById("1", null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlById("12345", ANYBODY, HttpStatus.UNAUTHORIZED);

    getCrawlByIdCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlById()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void getCrawlByIdCommonTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    int crawlCount = validateGetCrawlsResult(crawlPager, null, -1);

    for (int index = 0; index < crawlCount; index++) {
      Credentials credentials = USER_ADMIN;

      if (index % 2 == 1) {
        credentials = CONTENT_ADMIN;
      }

      String jobId = crawlPager.getCrawls().get(index).getJobId();

      CrawlStatus crawlStatus = runTestGetCrawlById(jobId, credentials, HttpStatus.OK);

      validateGetCrawlByIdResult(crawlStatus);
    }

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for a crawl given its identifier.
   *
   * @param jobId A String with the identifier of the crawl.
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlStatus with the crawl data.
   * @throws Exception if there are problems.
   */
  private CrawlStatus runTestGetCrawlById(
      String jobId, Credentials credentials, HttpStatus expectedHttpStatus) throws Exception {
    log.debug2("jobId = {}", jobId);
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawls/{jobId}");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template)
            .build()
            .expand(Collections.singletonMap("jobId", jobId));

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
        new TestRestTemplate(restTemplate)
            .exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlStatus result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), CrawlStatus.class);
    }

    log.debug2("result = {}", result);
    return result;
  }

  /**
   * Validates the status of a crawl.
   *
   * @param crawlStatus A CrawlStatus with the status of the crawl.
   */
  private void validateGetCrawlByIdResult(CrawlStatus crawlStatus) {
    log.info("crawlStatus = {}", crawlStatus);
    assertEquals(sau.getAuId(), crawlStatus.getAuId());

    if (crawlStatus.getJobStatus().getStatusCode() == StatusCodeEnum.SUCCESSFUL) {
      if (crawlStatus.getPriority() == 0) {
        assertEquals(25118, crawlStatus.getBytesFetched().longValue());
        assertEquals(12, crawlStatus.getFetchedItems().getCount().intValue());
      } else {
        assertEquals(0, crawlStatus.getBytesFetched().longValue());
        assertEquals(1, crawlStatus.getFetchedItems().getCount().intValue());
      }

      assertEquals(1, crawlStatus.getExcludedItems().getCount().intValue());
      assertEquals(1, crawlStatus.getNotModifiedItems().getCount().intValue());
      assertEquals(4, crawlStatus.getParsedItems().getCount().intValue());
    }

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void crawlPaginationUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(null, null, null, HttpStatus.OK);
    int crawlCount = validateGetCrawlsResult(crawlPager, null, -1);
    assertEquals(5, crawlCount);

    List<String> jobIds = new ArrayList<>(crawlCount);

    for (int index = 0; index < crawlCount; index++) {
      jobIds.add(crawlPager.getCrawls().get(index).getJobId());
    }

    int pageSize = 2;
    int remainingCrawlCount = crawlCount;

    crawlPager = runTestGetCrawls(ANYBODY, pageSize, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

    assertEquals(jobIds.get(0), crawlPager.getCrawls().get(0).getJobId());
    assertEquals(jobIds.get(1), crawlPager.getCrawls().get(1).getJobId());

    String continuationToken = crawlPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingCrawlCount = remainingCrawlCount - pageSize;

    crawlPager = runTestGetCrawls(null, pageSize, continuationToken, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

    assertEquals(jobIds.get(2), crawlPager.getCrawls().get(0).getJobId());
    assertEquals(jobIds.get(3), crawlPager.getCrawls().get(1).getJobId());

    continuationToken = crawlPager.getPageInfo().getContinuationToken();
    remainingCrawlCount = remainingCrawlCount - pageSize;

    if (remainingCrawlCount == 0) {
      assertNull(continuationToken);
    } else {
      assertNotNull(continuationToken);

      crawlPager = runTestGetCrawls(ANYBODY, pageSize, continuationToken, HttpStatus.OK);
      validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

      assertEquals(jobIds.get(4), crawlPager.getCrawls().get(0).getJobId());

      continuationToken = crawlPager.getPageInfo().getContinuationToken();
      assertNull(continuationToken);
    }

    crawlPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void crawlPaginationAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    crawlPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs job pagination-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void crawlPaginationCommonTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    int crawlCount = validateGetCrawlsResult(crawlPager, null, -1);

    List<String> jobIds = new ArrayList<>(crawlCount);

    for (int index = 0; index < crawlCount; index++) {
      jobIds.add(crawlPager.getCrawls().get(index).getJobId());
    }

    int pageSize = 2;
    int remainingCrawlCount = crawlCount;

    crawlPager = runTestGetCrawls(CONTENT_ADMIN, pageSize, null, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

    assertEquals(jobIds.get(0), crawlPager.getCrawls().get(0).getJobId());
    assertEquals(jobIds.get(1), crawlPager.getCrawls().get(1).getJobId());

    String continuationToken = crawlPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingCrawlCount = remainingCrawlCount - pageSize;

    crawlPager = runTestGetCrawls(USER_ADMIN, pageSize, continuationToken, HttpStatus.OK);
    validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

    assertEquals(jobIds.get(2), crawlPager.getCrawls().get(0).getJobId());

    if (remainingCrawlCount == 1) {
      continuationToken = crawlPager.getPageInfo().getContinuationToken();
      assertNull(continuationToken);
    } else {
      assertEquals(jobIds.get(3), crawlPager.getCrawls().get(1).getJobId());

      continuationToken = crawlPager.getPageInfo().getContinuationToken();
      remainingCrawlCount = remainingCrawlCount - pageSize;

      if (remainingCrawlCount == 0) {
        assertNull(continuationToken);
      } else {
        assertNotNull(continuationToken);

        crawlPager = runTestGetCrawls(CONTENT_ADMIN, pageSize, continuationToken, HttpStatus.OK);
        validateGetCrawlsResult(crawlPager, pageSize, crawlCount);

        assertEquals(jobIds.get(4), crawlPager.getCrawls().get(0).getJobId());

        continuationToken = crawlPager.getPageInfo().getContinuationToken();
        assertNull(continuationToken);
      }
    }

    log.debug2("Done");
  }

  /**
   * Runs URL pagination-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void urlPaginationUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(null, null, null, HttpStatus.OK);
    String jobId = crawlPager.getCrawls().get(0).getJobId();

    UrlPager urlPager = runTestGetCrawlUrlKind(jobId, "parsed", null, null, null, HttpStatus.OK);

    int urlCount = urlPager.getUrls().size();
    assertEquals(4, urlCount);

    List<String> urls = new ArrayList<>(urlCount);

    for (int index = 0; index < urlCount; index++) {
      urls.add(urlPager.getUrls().get(index).getUrl());
    }

    int pageSize = 2;
    int remainingUrlCount = urlCount;

    urlPager = runTestGetCrawlUrlKind(jobId, "parsed", ANYBODY, pageSize, null, HttpStatus.OK);

    assertEquals(urls.get(0), urlPager.getUrls().get(0).getUrl());
    assertEquals(urls.get(1), urlPager.getUrls().get(1).getUrl());

    String continuationToken = urlPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingUrlCount = remainingUrlCount - pageSize;

    urlPager =
        runTestGetCrawlUrlKind(
            jobId, "parsed", ANYBODY, pageSize, continuationToken, HttpStatus.OK);

    assertEquals(urls.get(2), urlPager.getUrls().get(0).getUrl());
    assertEquals(urls.get(3), urlPager.getUrls().get(1).getUrl());

    continuationToken = crawlPager.getPageInfo().getContinuationToken();
    assertNull(continuationToken);

    remainingUrlCount = remainingUrlCount - pageSize;
    assertEquals(0, remainingUrlCount);

    urlPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs URL pagination-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void urlPaginationAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    urlPaginationCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs URL pagination-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void urlPaginationCommonTest() throws Exception {
    log.debug2("Invoked");

    CrawlPager crawlPager = runTestGetCrawls(USER_ADMIN, null, null, HttpStatus.OK);
    String jobId = crawlPager.getCrawls().get(0).getJobId();

    UrlPager urlPager =
        runTestGetCrawlUrlKind(jobId, "parsed", USER_ADMIN, null, null, HttpStatus.OK);

    int urlCount = urlPager.getUrls().size();
    assertEquals(4, urlCount);

    List<String> urls = new ArrayList<>(urlCount);

    for (int index = 0; index < urlCount; index++) {
      urls.add(urlPager.getUrls().get(index).getUrl());
    }

    int pageSize = 2;
    int remainingUrlCount = urlCount;

    urlPager =
        runTestGetCrawlUrlKind(jobId, "parsed", CONTENT_ADMIN, pageSize, null, HttpStatus.OK);

    assertEquals(urls.get(0), urlPager.getUrls().get(0).getUrl());
    assertEquals(urls.get(1), urlPager.getUrls().get(1).getUrl());

    String continuationToken = urlPager.getPageInfo().getContinuationToken();
    assertNotNull(continuationToken);

    remainingUrlCount = remainingUrlCount - pageSize;

    urlPager =
        runTestGetCrawlUrlKind(
            jobId, "parsed", USER_ADMIN, pageSize, continuationToken, HttpStatus.OK);

    assertEquals(urls.get(2), urlPager.getUrls().get(0).getUrl());
    assertEquals(urls.get(3), urlPager.getUrls().get(1).getUrl());

    continuationToken = crawlPager.getPageInfo().getContinuationToken();
    assertNull(continuationToken);

    remainingUrlCount = remainingUrlCount - pageSize;
    assertEquals(0, remainingUrlCount);

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for the URLs of a crawl.
   *
   * @param jobId A String with the identifier of the crawl.
   * @param urlKind A String with the kind of URLs to get.
   * @param credentials A Credentials with the request credentials.
   * @param limit An Integer with the maximum number of URLs to be returned.
   * @param continuationToken A String with the continuation token of the next page of URLs to be
   *     returned.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a UrlPager with the URLs.
   * @throws Exception if there are problems.
   */
  private UrlPager runTestGetCrawlUrlKind(
      String jobId,
      String urlKind,
      Credentials credentials,
      Integer limit,
      String continuationToken,
      HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("jobId = {}", jobId);
    log.debug2("urlKind = {}", urlKind);
    log.debug2("credentials = {}", credentials);
    log.debug2("limit = {}", limit);
    log.debug2("continuationToken = {}", continuationToken);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawls/{jobId}/{urlKind}");

    Map<String, String> urlVars = new HashMap<>();
    urlVars.put("jobId", jobId);
    urlVars.put("urlKind", urlKind);

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template).build().expand(urlVars);

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
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
        new TestRestTemplate(restTemplate)
            .exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    UrlPager result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), UrlPager.class);
    }

    log.debug2("result = {}", result);
    return result;
  }

  /**
   * Runs the deleteCrawlById()-related un-authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteCrawlByIdUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestDeleteCrawlById(null, null, HttpStatus.NOT_FOUND);
    runTestDeleteCrawlById(EMPTY_STRING, ANYBODY, HttpStatus.NOT_FOUND);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId()).crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestDoCrawl(crawlDesc, null, HttpStatus.ACCEPTED);
    String jobId = crawlJob.getJobId();

    CrawlStatus crawlStatus = runTestDeleteCrawlById(jobId, null, HttpStatus.OK);
    assertEquals(jobId, crawlStatus.getJobId());

    crawlJob = runTestDoCrawl(crawlDesc, ANYBODY, HttpStatus.ACCEPTED);
    jobId = crawlJob.getJobId();

    crawlStatus = runTestDeleteCrawlById(jobId, ANYBODY, HttpStatus.OK);
    assertEquals(jobId, crawlStatus.getJobId());

    deleteCrawlByIdCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the deleteCrawlById()-related authenticated-specific tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteCrawlByIdAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestDeleteCrawlById(null, null, HttpStatus.UNAUTHORIZED);
    runTestDeleteCrawlById(EMPTY_STRING, ANYBODY, HttpStatus.UNAUTHORIZED);
    runTestDeleteCrawlById("1", null, HttpStatus.UNAUTHORIZED);

    deleteCrawlByIdCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the deleteCrawlById()-related authentication-independent tests.
   *
   * @throws Exception if there are problems.
   */
  private void deleteCrawlByIdCommonTest() throws Exception {
    log.debug2("Invoked");

    runTestDeleteCrawlById(null, USER_ADMIN, HttpStatus.NOT_FOUND);
    runTestDeleteCrawlById(EMPTY_STRING, CONTENT_ADMIN, HttpStatus.NOT_FOUND);

    CrawlDesc crawlDesc = new CrawlDesc().auId(sau.getAuId()).crawlKind(CrawlDesc.CrawlKindEnum.NEWCONTENT);
    crawlDesc.forceCrawl(true);

    CrawlJob crawlJob = runTestDoCrawl(crawlDesc, USER_ADMIN, HttpStatus.ACCEPTED);
    String jobId = crawlJob.getJobId();

    CrawlStatus crawlStatus = runTestDeleteCrawlById(jobId, USER_ADMIN, HttpStatus.OK);
    assertEquals(jobId, crawlStatus.getJobId());

    crawlJob = runTestDoCrawl(crawlDesc, CONTENT_ADMIN, HttpStatus.ACCEPTED);
    jobId = crawlJob.getJobId();

    crawlStatus = runTestDeleteCrawlById(jobId, CONTENT_ADMIN, HttpStatus.OK);
    assertEquals(jobId, crawlStatus.getJobId());

    log.debug2("Done");
  }

  /**
   * Performs a DELETE operation on a crawl.
   *
   * @param jobId A String with the identifier of the crawl.
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlStatus with the status of the crawl.
   * @throws Exception if there are problems.
   */
  private CrawlStatus runTestDeleteCrawlById(
      String jobId, Credentials credentials, HttpStatus expectedHttpStatus) throws Exception {
    log.debug2("jobId = {}", jobId);
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawls/{jobId}");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
        UriComponentsBuilder.fromUriString(template)
            .build()
            .expand(Collections.singletonMap("jobId", jobId));

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
        new TestRestTemplate(restTemplate)
            .exchange(uri, HttpMethod.DELETE, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlStatus result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(), CrawlStatus.class);
    }

    log.debug2("result = {}", result);
    return result;
  }


  /**
   * Performs a DELETE operation on all crawls.
   *
   * @param credentials A Credentials with the request credentials.
   * @param expectedHttpStatus An HttpStatus with the expected HTTP status of the result.
   * @throws Exception if there are problems.
   */
  private void deleteAllCrawls(Credentials credentials, HttpStatus expectedHttpStatus)
      throws Exception {
    log.debug2("credentials = {}", credentials);
    log.debug2("expectedHttpStatus = {}", expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/jobs");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(template).build();

    URI uri =
        UriComponentsBuilder.newInstance().uriComponents(uriComponents).build().encode().toUri();
    log.trace("uri = {}", uri);

    // Initialize the request to the REST service.
    RestTemplate restTemplate = RestUtil.getRestTemplate();

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
    ResponseEntity<Void> response =
        new TestRestTemplate(restTemplate)
            .exchange(uri, HttpMethod.DELETE, requestEntity, Void.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    log.debug2("Done");
  }

  /**
   * Provides the URL template to be tested.
   *
   * @param pathAndQueryParams A String with the path and query parameters of the URL template to be
   *     tested.
   * @return a String with the URL template to be tested.
   */
  private String getTestUrlTemplate(String pathAndQueryParams) {
    return "http://localhost:" + port + pathAndQueryParams;
  }
}
