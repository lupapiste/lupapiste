*** Settings ***

Documentation   Inforequest state handling
Test setup      Wait Until  Ajax calls have finished
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority assigns an inforequest to herself and then back to no-one
  Sonja logs in
  Click link  test-from-applications-to-inforequests-tab
  Wait until  No inforequest is assigned to  Sonja
  Open any inforequest
  Wait until page contains element  inforequest-assignee-select
  Select From List  inforequest-assignee-select  777777777777777777000023
  Click link  xpath=//a[@data-test-id="test-inforequests"]
  Wait until  Number of assigned inforequests  Sonja  1
  Logout
  
Applicant marks inforequest answered
  Mikko logs in
  Wait and click  test-from-applications-to-inforequests-tab
  Wait until  Number of visible applications on page  inforequests  2
  Wait and click  test-inforequest-link
  Wait until  Inforequest state is  Avoin
  Wait and click  test-mark-inforequest-answered
  Wait until  Inforequest state is  Vastattu
  Logout

*** Keywords ***

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element should contain  test-inforequest-state  ${state}

Nth inforequest is assigned to
  [Arguments]  ${Index}  ${Assignee name}
  Element should contain  xpath=//tr[@data-test-class="inforequest-row"][${Index}]/td[@data-test-class="inforequest-assignee"]  ${Assignee name}

No inforequest is assigned to
  [Arguments]  ${Assignee name}
  Number of assigned inforequests  ${Assignee name}  0
  
Number of assigned inforequests
  [Arguments]  ${Assignee name}  ${Count}
  Xpath Should Match X Times  //td[@data-test-class="inforequest-assignee" and contains(text(), "${Assignee name}")]  ${Count}
