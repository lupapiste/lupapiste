*** Settings ***

Documentation   Basic usage of assignments search
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot


*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Dos
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo
  Submit application
  Logout

Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info

Sonja creates two assignments for herself
  Create assignment  Sonja Sibbo  documents  uusiRakennus  Tarkasta varusteet
  Create assignment  Sonja Sibbo  documents  rakennuspaikka  Tarkasta kiinteistö

Sonja creates assignment for Ronja about paasuunnittelija
  Open tab  parties
  Create assignment  Ronja Sibbo  parties  paasuunnittelija  Katoppa tää

Sonja has 'own assignments' filter as default
  Open assignments search
  Active search tab is  created
  Click by test id  toggle-advanced-filters
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni

Sonja sees two assignments
  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  2
  # Order is ascending
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]/td[@data-test-col-name='description']  Tarkasta varusteet
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[2]/td[@data-test-col-name='description']  Tarkasta kiinteistö

Complete assignment button should be visible
  Element should be visible  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]//button[@data-test-id='complete-assignment']

No assignments completed
  Click by test id  search-tab-completed
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  0

All assignments returns two
  Click by test id  search-tab-all
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  2

Complete first assignment from search results
  Click element  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]//button[@data-test-id='complete-assignment']
  Positive indicator should be visible
  # The 'all' tab
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  2
  Click by test id  search-tab-created
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Click by test id  search-tab-completed
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]/td[@data-test-col-name='description']  Tarkasta varusteet
  Logout

Ronja logs in and sees only her assignments
  As Ronja
  Open assignments search
  Active search tab is  created
  Click by test id  toggle-advanced-filters
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Click by test id  search-tab-completed
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  0

Ronja opens her assignments and ends up in parties tab
  Click by test id  search-tab-created
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Click element  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]
  Wait until  Tab should be visible  parties

Ronja goes back to search and selects filter for all authorities in organization
  Open assignments search
  Active search tab is  created
  Select from autocomplete by test id  recipient-filter-component  Kaikki

Sonja's completed assignment is visible for Ronja
  Click by test id  search-tab-completed
  Active search tab is  completed
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]/td[@data-test-col-name='description']  Tarkasta varusteet

Two assignments are open with 'all' filter
  Click by test id  search-tab-created
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  2

Ronja can complete both assignments
  Active search tab is  created
  Click element  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]//button[@data-test-id='complete-assignment']
  Positive indicator should be visible
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  1
  Click element  xpath=(//table[@id="assignments-list"]//tbody/tr[@class="assignment-row"])[1]//button[@data-test-id='complete-assignment']
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  0
  Click by test id  search-tab-completed
  Wait until  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[@class="assignment-row"]  3

Completed assignments are not visible in application
  Select Radio Button  searchType  searchTypeApplications
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info
  Wait until  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  0
  Open tab  parties
  Wait until  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  0

No frontend errors
  Logout
  There are no frontend errors
