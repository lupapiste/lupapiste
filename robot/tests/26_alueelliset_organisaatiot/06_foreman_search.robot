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
  Go to page  applications
  Active search tab is  all
  Log out

Sonja logs in and sees some only foreman applications on foreman search tab
  Sonja logs in
  Go to page  applications
  Active search tab is  application
  Scroll and click test id  search-tab-all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Switch to foremen  
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  3

Sonja can see foreman application on verdict tab
  Click by test id  search-tab-verdict
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1

Sonja sees different saved filters on foreman search tab
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-saved-filters
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-Barfoo"]
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-Foobar"]

  Switch to applications
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-saved-filters
  Wait Until  Element should be visible  //div[@data-test-id="saved-filter-row-Barfoo"]
  Wait Until  Element should be visible  //div[@data-test-id="saved-filter-row-Foobar"]
  Click by test id  toggle-advanced-filters

Sonja switches between applications and foremen view
  Scroll and click test id  search-tab-application
  Switch to foremen
  Active search tab is  foremanApplication
  Scroll and click test id  search-tab-foremanNotice
  Switch to applications
  Active search tab is  application
    

*** Keywords ***

Switch to applications
  Click element  //label[@for="searchTypeApplications"]

Switch to foremen
  Click element  //label[@for="searchTypeForeman"]
