*** Settings ***

Documentation  Authority admin search applications with event filter
Suite Teardown  Logout
Resource       ../../common_resource.robot
Suite Setup  Apply minimal fixture now

*** Test Cases ***

Olli-ya creates some applications
  Olli-ya logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname1}  Warranty_app_${secs}
  Set Suite Variable  ${appname2}  Worktime_app_1_${secs}
  Set Suite Variable  ${appname3}  Worktime_app_2_${secs}
  Set Suite Variable  ${appname4}  Fullfilling_app_1
  Set Suite Variable  ${appname5}  Fullfilling_app_2
  Set Suite Variable  ${appname6}  Fullfilling_app_3

  Create application the fast way   ${appname1}  564-423-2-162  ya-katulupa-vesi-ja-viemarityot
  Create application the fast way   ${appname2}  564-423-2-163  ya-katulupa-vesi-ja-viemarityot
  Create application the fast way   ${appname3}  564-423-2-164  ya-katulupa-vesi-ja-viemarityot
  Create application the fast way   ${appname4}  564-423-2-165  ya-katulupa-vesi-ja-viemarityot
  Create application the fast way   ${appname5}  564-423-2-166  ya-katulupa-vesi-ja-viemarityot
  Create application the fast way   ${appname6}  564-423-2-167  ya-katulupa-vesi-ja-viemarityot

Olli-ya prepares warranty application
  Go to page  applications
  Open application  ${appname1}  564-423-2-162
  Submit application
  Click enabled by test id  approve-application
  Confirm notification dialog
  Open tab  verdict
  Submit empty verdict  verdictGiven  1

  Open tab  tasks
  Sets date via modal datepicker dialog  application-inform-construction-started-btn  01.01.2018
  Wait until  Application state should be  constructionStarted

  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']

  Sets date via modal datepicker dialog  application-inform-construction-ready-btn  01.02.2018
  Wait until  Application state should be  closed

Olli-ya prepares worktime applications
  Go to page  applications
  Open application  ${appname2}  564-423-2-163
  Open accordions  info
  Fill tyoaika fields

  Go to page  applications
  Open application  ${appname3}  564-423-2-164
  Open accordions  info
  Fill tyoaika fields  01.12.2016  15.12.2016

Olli-ya see all applications without filters
  Go to page  applications
  Show all applications
  Element should not be visible  xpath=//div[@data-test-id="event-filter-item"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname1}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname4}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname5}"]

Olli-ya can see all filters
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="event-filter-item"]
  Wait until  Element should be visible  xpath=//div[@data-test-id="event-filter-component"]
  Click Element  xpath=//div[@data-test-id="event-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="event-filter-component"]//div[@class="autocomplete-dropdown"]
  Autocomplete option list should contain  event-filter-component  Takuun päättyminen  Lupa-ajan alkaminen  Lupa-ajan päättyminen  Lupa-aika alkanut, ei valmis  Lupa-aika päättynyt, ei valmis  Ilmoitettu valmistuneeksi, mutta tila ei valmistunut
  Click Element  xpath=//div[@data-test-id="event-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//div[@data-test-id="event-filter-component"]//div[@class="autocomplete-dropdown"]

Olli-ya selects warranty filter and see only warranty applications
  Select From Autocomplete  div[@data-test-id="event-filter-component"]  Takuun päättyminen
  Input text by test id  event-search-start-date  01.01.2020
  Input text by test id  event-search-end-date  01.03.2020
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname1}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname4}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname5}"]

Olli-ya selects worktime started filter and see only one worktime applications
  Select From Autocomplete  div[@data-test-id="event-filter-component"]  Lupa-ajan alkaminen
  Input text by test id  event-search-start-date  01.05.2014
  Input text by test id  event-search-end-date  02.05.2014
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]

Olli-ya selects worktime ended filter and see only one worktime applications
  Select From Autocomplete  div[@data-test-id="event-filter-component"]  Lupa-ajan päättyminen
  Input text by test id  event-search-start-date  10.12.2016
  Input text by test id  event-search-end-date  20.12.2016
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]

Olli-ya selects worktime ended, not ready and see only worktime application 1
  Click by test id  clear-saved-filters
  Select From Autocomplete  div[@data-test-id="event-filter-component"]  Lupa-aika päättynyt, ei valmis
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]

Olli-ya selects worktime started, not ready and see only worktime application 2
  Click by test id  clear-saved-filters
  Select From Autocomplete  div[@data-test-id="event-filter-component"]  Lupa-aika alkanut, ei valmis
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]


*** Keywords ***

Sets date via modal datepicker dialog
  [Arguments]  ${openDialogButtonId}  ${date}
  Click enabled by test id  ${openDialogButtonId}
  Wait until  element should be visible  modal-datepicker-date
  Element Should Be Enabled  modal-datepicker-date
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  modal-datepicker-date  ${date}
  Click enabled by test id  modal-datepicker-continue
  Confirm  dynamic-yes-no-confirm-dialog