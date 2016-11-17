*** Settings ***

Documentation  Applicant and authority uses application tabs
Suite Teardown  Logout
Resource       ../../common_resource.robot
Suite Setup  Apply minimal fixture now

*** Test Cases ***

Teppo logs in and creates some applications
  Teppo logs in
  ${secs} =  Get Time  epoch
  Create application the fast way  tabs-test-01  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-02  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-03  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-04  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-05  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-06  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-07  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-08  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-09  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-10  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-11  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-12  753-416-25-30  kerrostalo-rivitalo
  Create application the fast way  tabs-test-13  753-416-25-30  kerrostalo-rivitalo

Teppo navigates to application page
  Go to page  applications
  Active search tab is  all
  Wait until  Click by test id  search-tab-all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  13

Teppo selects 10 as the paging limit
  Click by test id  limit-10
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  10

Teppo browses the pages
  Click by test id  paging-next
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  3
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@data-test-address="tabs-test-01"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@data-test-address="tabs-test-02"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@data-test-address="tabs-test-03"]
  Click by test id  paging-previous
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  10

Teppo selects larger paging limits
  Click by test id  limit-25
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  13
  Click by test id  limit-50
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  13

Teppo briefly visits another tab and the limits remain unchanged
  Click by test id  limit-10
  Click by test id  search-tab-construction
  Click by test id  search-tab-all
  Wait until  Element should be visible  //button[@data-test-id="limit-10" and contains(@class, "active")]
