*** Settings ***

Documentation   Default filter regression test
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        ../26_alueelliset_organisaatiot/filter_resource.robot
Resource        assignments_common.robot

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Dos
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo
  Submit application
  [Teardown]  Logout

Sonja logs in saves default filter
  Sonja logs in
  Scroll and click test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Select from autocomplete by test id  areas-filter-component  Keskusta
  Select from autocomplete by test id  operations-filter-component  Asuinkerrostalon tai rivitalon rakentaminen
  Save advanced filter  myfilter
  Scroll and click test id  set-myfilter-as-default-filter

Sonja reloads page and sees that myfilter is the default filter
  Reload page and kill dev-box
  Show all applications
  Scroll and click test id  toggle-advanced-filters
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "myfilter")]
  Filter should contain tag  areas  Keskusta
  Filter should contain tag  operations  Asuinkerrostalon tai rivitalon rakentaminen
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  operations  1

Sonja selects assignments tab and sees empty filter settings
  Open assignments search
  Scroll and click test id  toggle-advanced-filters
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  operations  0
  [Teardown]  Logout
