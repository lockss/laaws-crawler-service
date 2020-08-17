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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lockss.laaws.crawler.model.CrawlerConfig;
import org.lockss.laaws.crawler.model.CrawlerStatus;
import org.lockss.laaws.crawler.model.CrawlerStatuses;
import org.lockss.log.L4JLogger;
import org.lockss.spring.test.SpringLockssTestCase4;
import org.lockss.util.rest.RestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Test class for org.lockss.laaws.crawler.impl.CrawlersApiServiceImpl.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestCrawlersApiServiceImpl extends SpringLockssTestCase4 {
  private static final L4JLogger log = L4JLogger.getLogger();

  private static final String UI_PORT_CONFIGURATION_TEMPLATE =
      "UiPortConfigTemplate.txt";
  private static final String UI_PORT_CONFIGURATION_FILE = "UiPort.txt";

  private static final String EMPTY_STRING = "";

  // The identifier of a crawler that does not exist in the test system.
  private static final String UNKNOWN_CRAWLER ="unknown_crawler";

  // The identifier of a crawler that does exist in the test system.
  private static final String LOCKSS_CRAWLER = "lockss";

  // Credentials.
  private final Credentials USER_ADMIN =
      this.new Credentials("lockss-u", "lockss-p");
  private final Credentials CONTENT_ADMIN =
      this.new Credentials("content-admin", "I'mContentAdmin");
  private final Credentials ACCESS_CONTENT =
      this.new Credentials("access-content", "I'mAccessContent");
  private final Credentials ANYBODY =
      this.new Credentials("someUser", "somePassword");

  // The port that Tomcat is using during this test.
  @LocalServerPort
  private int port;

  // The application Context used to specify the command line arguments to be
  // used for the tests.
  @Autowired
  private ApplicationContext appCtx;

  /**
   * Set up code to be run before all tests.
   */
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
    setUpTempDirectory(TestCrawlersApiServiceImpl.class.getCanonicalName());

    // Set up the UI port.
    setUpUiPort(UI_PORT_CONFIGURATION_TEMPLATE, UI_PORT_CONFIGURATION_FILE);

    log.debug2("Done");
  }

  /**
   * Runs the tests with authentication turned off.
   * 
   * @throws Exception
   *           if there are problems.
   */
  @Test
  public void runUnAuthenticatedTests() throws Exception {
    log.debug2("Invoked");

    // Specify the command line parameters to be used for the tests.
    List<String> cmdLineArgs = getCommandLineArguments();
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/testAuthOff.txt");

    CommandLineRunner runner = appCtx.getBean(CommandLineRunner.class);
    runner.run(cmdLineArgs.toArray(new String[cmdLineArgs.size()]));

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getCrawlersUnAuthenticatedTest();
    getCrawlerConfigUnAuthenticatedTest(true);

    log.debug2("Done");
  }

  /**
   * Runs the tests with authentication turned on.
   * 
   * @throws Exception
   *           if there are problems.
   */
  @Test
  public void runAuthenticatedTests() throws Exception {
    log.debug2("Invoked");

    // Specify the command line parameters to be used for the tests.
    List<String> cmdLineArgs = getCommandLineArguments();
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/testAuthOn.txt");

    CommandLineRunner runner = appCtx.getBean(CommandLineRunner.class);
    runner.run(cmdLineArgs.toArray(new String[cmdLineArgs.size()]));

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedAuthenticatedTest();
    getCrawlersAuthenticatedTest();
    getCrawlerConfigAuthenticatedTest();

    log.debug2("Done");
  }

  /**
   * Runs the tests with crawling disabled.
   * 
   * @throws Exception
   *           if there are problems.
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

    runGetSwaggerDocsTest(getTestUrlTemplate("/v2/api-docs"));
    runMethodsNotAllowedUnAuthenticatedTest();
    getCrawlersUnAuthenticatedTest();
    getCrawlerConfigUnAuthenticatedTest(false);

    log.debug2("Done");
  }

  /**
   * Provides the standard command line arguments to start the server.
   * 
   * @return a List<String> with the command line arguments.
   * @throws IOException
   *           if there are problems.
   */
  private List<String> getCommandLineArguments() throws IOException {
    log.debug2("Invoked");

    List<String> cmdLineArgs = new ArrayList<String>();
    cmdLineArgs.add("-p");
    cmdLineArgs.add(getPlatformDiskSpaceConfigPath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("config/common.xml");

    File folder =
	new File(new File(new File(getTempDirPath()), "tdbxml"), "prod");
    log.trace("folder = {}", () -> folder);

    cmdLineArgs.add("-x");
    cmdLineArgs.add(folder.getAbsolutePath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/lockss.txt");
    cmdLineArgs.add("-p");
    cmdLineArgs.add(getUiPortConfigFile().getAbsolutePath());
    cmdLineArgs.add("-p");
    cmdLineArgs.add("test/config/lockss.opt");

    log.debug2("cmdLineArgs = {}", () -> cmdLineArgs);
    return cmdLineArgs;
  }

  /**
   * Runs the invalid method-related un-authenticated-specific tests.
   */
  private void runMethodsNotAllowedUnAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestMethodNotAllowed(null, null, HttpMethod.PUT, HttpStatus.NOT_FOUND);

    // Empty crawler ID.
    runTestMethodNotAllowed(EMPTY_STRING, ANYBODY, HttpMethod.PATCH,
	HttpStatus.NOT_FOUND);

    // Unknown crawler ID.
    runTestMethodNotAllowed(UNKNOWN_CRAWLER, ANYBODY, HttpMethod.PUT,
	HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(UNKNOWN_CRAWLER, null, HttpMethod.PATCH,
	HttpStatus.METHOD_NOT_ALLOWED);

    // Good crawler ID.
    runTestMethodNotAllowed(LOCKSS_CRAWLER, null, HttpMethod.PATCH,
	HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(LOCKSS_CRAWLER, ANYBODY, HttpMethod.PUT,
	HttpStatus.METHOD_NOT_ALLOWED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the invalid method-related authenticated-specific tests.
   */
  private void runMethodsNotAllowedAuthenticatedTest() {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestMethodNotAllowed(null, ANYBODY, HttpMethod.PUT,
	HttpStatus.UNAUTHORIZED);

    // Empty crawler ID.
    runTestMethodNotAllowed(EMPTY_STRING, null, HttpMethod.PATCH,
	HttpStatus.UNAUTHORIZED);

    // Unknown crawler ID.
    runTestMethodNotAllowed(UNKNOWN_CRAWLER, ANYBODY, HttpMethod.PUT,
	HttpStatus.UNAUTHORIZED);

    // No credentials.
    runTestMethodNotAllowed(LOCKSS_CRAWLER, null, HttpMethod.PATCH,
	HttpStatus.UNAUTHORIZED);

    // Bad credentials.
    runTestMethodNotAllowed(LOCKSS_CRAWLER, ANYBODY, HttpMethod.PUT,
	HttpStatus.UNAUTHORIZED);

    runMethodsNotAllowedCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the invalid method-related authentication-independent tests.
   */
  private void runMethodsNotAllowedCommonTest() {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestMethodNotAllowed(null, USER_ADMIN, HttpMethod.PUT,
	HttpStatus.NOT_FOUND);

    // Empty crawler ID.
    runTestMethodNotAllowed(EMPTY_STRING, CONTENT_ADMIN, HttpMethod.PATCH,
	HttpStatus.NOT_FOUND);

    // Unknown crawler ID.
    runTestMethodNotAllowed(UNKNOWN_CRAWLER, ACCESS_CONTENT, HttpMethod.PUT,
	HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(LOCKSS_CRAWLER, USER_ADMIN, HttpMethod.PUT,
	HttpStatus.METHOD_NOT_ALLOWED);

    runTestMethodNotAllowed(LOCKSS_CRAWLER, CONTENT_ADMIN, HttpMethod.PATCH,
	HttpStatus.METHOD_NOT_ALLOWED);

    log.debug2("Done");
  }

  /**
   * Performs an operation using a method that is not allowed.
   * 
   * @param crawler
   *          A String with the identifier of the crawler.
   * @param credentials
   *          A Credentials with the request credentials.
   * @param method
   *          An HttpMethod with the request method.
   * @param expectedStatus
   *          An HttpStatus with the HTTP status of the result.
   */
  private void runTestMethodNotAllowed(String crawler, Credentials credentials,
      HttpMethod method, HttpStatus expectedStatus) {
    log.debug2("crawler = {}", crawler);
    log.debug2("credentials = {}", credentials);
    log.debug2("method = {}", method);
    log.debug2("expectedStatus = {}", expectedStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawlers/{crawler}");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(template)
	.build().expand(Collections.singletonMap("crawler", crawler));

    URI uri = UriComponentsBuilder.newInstance().uriComponents(uriComponents)
	.build().encode().toUri();
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
      requestEntity = new HttpEntity<String>(null, headers);
    }

    // Make the request and get the response. 
    ResponseEntity<String> response = new TestRestTemplate(restTemplate)
	.exchange(uri, method, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertFalse(RestUtil.isSuccess(statusCode));
    assertEquals(expectedStatus, statusCode);
  }

  /**
   * Runs the getCrawlers()-related un-authenticated-specific tests.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlersUnAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    CrawlerStatuses statuses = runTestGetCrawlers(null, HttpStatus.OK);
    Map<String, CrawlerStatus> crawlers = statuses.getCrawlerMap();
    assertEquals(1, crawlers.size());
    assertTrue(crawlers.containsKey(LOCKSS_CRAWLER));

    statuses = runTestGetCrawlers(ANYBODY, HttpStatus.OK);
    crawlers = statuses.getCrawlerMap();
    assertEquals(1, crawlers.size());
    assertTrue(crawlers.containsKey(LOCKSS_CRAWLER));

    getCrawlersCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlers()-related authenticated-specific tests.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlersAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    runTestGetCrawlers(null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlers(ANYBODY, HttpStatus.UNAUTHORIZED);

    getCrawlersCommonTest();

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlers()-related authentication-independent tests.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlersCommonTest() throws Exception {
    log.debug2("Invoked");

    CrawlerStatuses statuses = runTestGetCrawlers(USER_ADMIN, HttpStatus.OK);
    Map<String, CrawlerStatus> crawlers = statuses.getCrawlerMap();
    assertEquals(1, crawlers.size());
    assertTrue(crawlers.containsKey(LOCKSS_CRAWLER));

    statuses = runTestGetCrawlers(CONTENT_ADMIN, HttpStatus.OK);
    crawlers = statuses.getCrawlerMap();
    assertEquals(1, crawlers.size());
    assertTrue(crawlers.containsKey(LOCKSS_CRAWLER));

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for the crawlers of the system.
   * 
   * @param credentials
   *          A Credentials with the request credentials.
   * @param expectedHttpStatus
   *          An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlerStatuses with the crawlers statuses.
   * 
   * @throws Exception if there are problems.
   */
  private CrawlerStatuses runTestGetCrawlers(Credentials credentials,
      HttpStatus expectedHttpStatus) throws Exception {
    log.debug2("credentials = {}", () -> credentials);
    log.debug2("expectedHttpStatus = {}", () -> expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawlers");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents =
	UriComponentsBuilder.fromUriString(template).build();

    URI uri = UriComponentsBuilder.newInstance().uriComponents(uriComponents)
	.build().encode().toUri();
    log.trace("uri = {}", () -> uri);

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
      requestEntity = new HttpEntity<String>(null, headers);
    }

    // Make the request and get the response. 
    ResponseEntity<String> response = new TestRestTemplate(restTemplate)
	.exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlerStatuses result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result = new ObjectMapper().readValue(response.getBody(),
	  CrawlerStatuses.class);
    }

    if (log.isDebug2Enabled()) log.debug2("result = {}", result);
    return result;
  }

  /**
   * Runs the getCrawlerConfig()-related un-authenticated-specific tests.
   * 
   * @param enabled
   *          A boolean indicating whether crawling is enabled.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlerConfigUnAuthenticatedTest(boolean enabled)
      throws Exception {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestGetCrawlerConfig(null, null, HttpStatus.NOT_FOUND);
    runTestGetCrawlerConfig(null, ANYBODY, HttpStatus.NOT_FOUND);

    // Empty crawler ID.
    runTestGetCrawlerConfig(EMPTY_STRING, null, HttpStatus.NOT_FOUND);
    runTestGetCrawlerConfig(EMPTY_STRING, ANYBODY, HttpStatus.NOT_FOUND);

    // Unknown crawler ID.
    runTestGetCrawlerConfig(UNKNOWN_CRAWLER, null, HttpStatus.NOT_FOUND);
    runTestGetCrawlerConfig(UNKNOWN_CRAWLER, ANYBODY, HttpStatus.NOT_FOUND);

    CrawlerConfig crawlerConfig =
	runTestGetCrawlerConfig(LOCKSS_CRAWLER, null, HttpStatus.OK);
    log.trace("crawlerConfig = {}", crawlerConfig);
    assertNotNull(crawlerConfig);
    assertTrue(Boolean.valueOf(crawlerConfig.getConfigMap()
	.get("starterEnabled")));
    assertEquals(enabled, Boolean.valueOf(crawlerConfig.getConfigMap()
	.get("crawlerEnabled")));

    getCrawlerConfigCommonTest(enabled);

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlerConfig()-related authenticated-specific tests.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlerConfigAuthenticatedTest() throws Exception {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestGetCrawlerConfig(null, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlerConfig(null, ANYBODY, HttpStatus.UNAUTHORIZED);

    // Empty crawler ID.
    runTestGetCrawlerConfig(EMPTY_STRING, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlerConfig(EMPTY_STRING, ANYBODY, HttpStatus.UNAUTHORIZED);

    // Unknown crawler ID.
    runTestGetCrawlerConfig(UNKNOWN_CRAWLER, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlerConfig(UNKNOWN_CRAWLER, ANYBODY, HttpStatus.UNAUTHORIZED);

    runTestGetCrawlerConfig(LOCKSS_CRAWLER, null, HttpStatus.UNAUTHORIZED);
    runTestGetCrawlerConfig(LOCKSS_CRAWLER, ANYBODY, HttpStatus.UNAUTHORIZED);

    getCrawlerConfigCommonTest(true);

    log.debug2("Done");
  }

  /**
   * Runs the getCrawlerConfig()-related authentication-independent tests.
   * 
   * @throws Exception if there are problems.
   */
  private void getCrawlerConfigCommonTest(boolean enabled) throws Exception {
    log.debug2("Invoked");

    // Missing crawler ID.
    runTestGetCrawlerConfig(null, USER_ADMIN, HttpStatus.NOT_FOUND);

    // Empty crawler ID.
    runTestGetCrawlerConfig(EMPTY_STRING, CONTENT_ADMIN, HttpStatus.NOT_FOUND);

    // Unknown crawler ID.
    runTestGetCrawlerConfig(UNKNOWN_CRAWLER, ACCESS_CONTENT, HttpStatus.NOT_FOUND);

    CrawlerConfig crawlerConfig =
	runTestGetCrawlerConfig(LOCKSS_CRAWLER, USER_ADMIN, HttpStatus.OK);
    log.trace("crawlerConfig = {}", crawlerConfig);
    assertNotNull(crawlerConfig);
    assertTrue(Boolean.valueOf(crawlerConfig.getConfigMap()
	.get("starterEnabled")));
    assertEquals(enabled, Boolean.valueOf(crawlerConfig.getConfigMap()
	.get("crawlerEnabled")));

    log.debug2("Done");
  }

  /**
   * Performs a GET operation for the configuration of a crawler.
   * 
   * @param crawlerId
   *          A String with the identifier of the crawler.
   * @param credentials
   *          A Credentials with the request credentials.
   * @param expectedHttpStatus
   *          An HttpStatus with the expected HTTP status of the result.
   * @return a CrawlerStatuses with the crawlers statuses.
   * 
   * @throws Exception if there are problems.
   */
  private CrawlerConfig runTestGetCrawlerConfig(String crawlerId,
      Credentials credentials, HttpStatus expectedHttpStatus) throws Exception {
    log.debug2("crawlerId = {}", crawlerId);
    log.debug2("credentials = {}", () -> credentials);
    log.debug2("expectedHttpStatus = {}", () -> expectedHttpStatus);

    // Get the test URL template.
    String template = getTestUrlTemplate("/crawlers/{crawlerId}");

    // Create the URI of the request to the REST service.
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(template)
	.build().expand(Collections.singletonMap("crawlerId", crawlerId));

    URI uri = UriComponentsBuilder.newInstance().uriComponents(uriComponents)
	.build().encode().toUri();
    log.trace("uri = {}", () -> uri);

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
      requestEntity = new HttpEntity<String>(null, headers);
    }

    // Make the request and get the response. 
    ResponseEntity<String> response = new TestRestTemplate(restTemplate)
	.exchange(uri, HttpMethod.GET, requestEntity, String.class);

    // Get the response status.
    HttpStatus statusCode = response.getStatusCode();
    assertEquals(expectedHttpStatus, statusCode);

    CrawlerConfig result = null;

    if (RestUtil.isSuccess(statusCode)) {
      result =
	  new ObjectMapper().readValue(response.getBody(), CrawlerConfig.class);
    }

    if (log.isDebug2Enabled()) log.debug2("result = {}", result);
    return result;
  }

  /**
   * Provides the URL template to be tested.
   * 
   * @param pathAndQueryParams
   *          A String with the path and query parameters of the URL template to
   *          be tested.
   * @return a String with the URL template to be tested.
   */
  private String getTestUrlTemplate(String pathAndQueryParams) {
    return "http://localhost:" + port + pathAndQueryParams;
  }
}
