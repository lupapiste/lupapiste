*** Settings ***

Documentation  Applicant and authority uses application tabs
Suite Teardown  Logout
Resource       ../../common_resource.robot
Suite Setup  Apply minimal fixture now

*** Test Cases ***

Teppo logs in and creates some applications
  Teppo logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${draft}  draft
  Set Suite Variable  ${open}  open
  Set Suite Variable  ${verdictGiven}  verdictGiven

  Create inforequest the fast way  create-info  360603.153  6734222.95  753-423-2-40  tyonjohtajan-nimeaminen-v2  rapu
  Create application with state  notice-2-${secs}  753-423-2-42  kerrostalo-rivitalo  ${open}
  Create application with state  notice-1-${secs}  753-423-2-41  tyonjohtajan-nimeaminen-v2  ${draft}
  Create application with state  notice-2-${secs}  753-423-2-42  tyonjohtajan-nimeaminen-v2  ${open}
  Create application with state  notice-7-${secs}  753-423-2-47  tyonjohtajan-nimeaminen-v2  ${verdictGiven}
  Log out

Sonja logs in and sees some only foreman applications on foreman search tab
  Sonja logs in
  Go to page  applications
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  4
  Click Element  //label[@for="searchTypeForeman"]
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  3

Sonja can see foreman application on verdict tab
  Click by test id  search-tab-verdict
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1

Sonja sees different saved filters on foreman search tab
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-saved-filters
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-Barfoo"]
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-Foobar"]

  Click Element  //label[@for="searchTypeApplications"]
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-saved-filters
  Wait Until  Element should be visible  //div[@data-test-id="saved-filter-row-Barfoo"]
  Wait Until  Element should be visible  //div[@data-test-id="saved-filter-row-Foobar"]
