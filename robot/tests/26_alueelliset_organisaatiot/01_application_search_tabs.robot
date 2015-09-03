*** Settings ***

Documentation  Applicant and authority uses application tabs
Suite teardown  Logout
Resource       ../../common_resource.robot
Suite setup  Apply minimal fixture now

*** Test Cases ***

Teppo logs in and creates some applications
  Teppo logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${info}  info
  Set Suite Variable  ${draft}  draft
  Set Suite Variable  ${open}  open
  Set Suite Variable  ${answered}  answered
  Set Suite Variable  ${submitted}  submitted
  Set Suite Variable  ${sent}  sent
  Set Suite Variable  ${complement-needed}  complement-needed
  Set Suite Variable  ${verdictGiven}  verdictGiven
  Set Suite Variable  ${constructionStarted}  constructionStarted
  Create inforequest the fast way  create-info  360603.153  6734222.95  753-423-2-40  kerrostalo-rivitalo  init-comment
  Create application the fast way  notice-1-${secs}  753-423-2-41  kerrostalo-rivitalo  ${draft}
  Create application the fast way  notice-2-${secs}  753-423-2-42  kerrostalo-rivitalo  ${open}
  Create application the fast way  notice-3-${secs}  753-423-2-43  kerrostalo-rivitalo  ${answered}
  Create application the fast way  notice-4-${secs}  753-423-2-44  kerrostalo-rivitalo  ${submitted}
  Create application the fast way  notice-5-${secs}  753-423-2-45  kerrostalo-rivitalo  ${sent}
  Create application the fast way  notice-6-${secs}  753-423-2-46  kerrostalo-rivitalo  ${complement-needed}
  Create application the fast way  notice-7-${secs}  753-423-2-47  kerrostalo-rivitalo  ${verdictGiven}
  Create application the fast way  notice-8-${secs}  753-423-2-48  kerrostalo-rivitalo  ${constructionStarted}

Teppo navigates to application page
  Go to page  applications
  Wait until  Click by test id  search-tab-all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  9

Teppo selects application tab
  Click by test id  search-tab-application
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  6
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${submitted}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${sent}"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${complement-needed}"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${draft}"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${open}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${answered}"]

Teppo selects construction tab
  Click by test id  search-tab-construction
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  2
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${verdictGiven}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${constructionStarted}"]

Teppo selects inforequest tab
  Click by test id  search-tab-inforequest
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${info}"]

Teppo selects cancelled tab
  Click by test id  search-tab-canceled
  Wait Until  Element should not be visible  //table[@id="applications-list"]

Teppo Logs out
  Log out

Sonja logs in and navigates to application page
  Sonja logs in
  Go to page  applications

Sonja selects all applications tab
  Wait until  Click by test id  search-tab-all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  8

Sonja selects application tab
  Click by test id  search-tab-application
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  3
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${submitted}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${sent}"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${complement-needed}"]

Sonja selects construction tab
  Click by test id  search-tab-construction
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  2
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${verdictGiven}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${constructionStarted}"]

Sonja selects inforequest tab
  Click by test id  search-tab-inforequest
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  3
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${open}"]
  Wait until  Element should be visible  //table[@id="applications-list"]//tbody/tr[@class="application-row"]/td[@data-test-row-state="${answered}"]
  Wait until  Element should be visible  //table[@id="applications-list"]/tbody/tr[@class="application-row"]/td[@data-test-row-state="${info}"]

Sonja selects cancelled tab
  Click by test id  search-tab-canceled
  Wait Until  Element should not be visible  //table[@id="applications-list"]
