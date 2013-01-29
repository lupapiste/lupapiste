*** Settings ***

Documentation   Inforequest state handling
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority assigns an inforequest to herself
  Sonja logs in
  Wait until  Number of visible inforequests  1
  Wait until  No inforequest is assigned to  Sonja
  Open the inforequest
  Wait until page contains element  inforequest-assignee-select
  Select From List  inforequest-assignee-select  777777777777777777000023
  Click by test id  inforequest-requests
  Wait until  Number of assigned inforequests  Sonja  1
  Logout

Applicant marks inforequest answered
  Mikko logs in
  Wait until  Number of visible inforequests  1
  Open the inforequest
  Wait until  Inforequest state is  Avoin
  Wait and click  test-mark-inforequest-answered
  Wait until  Inforequest state is  Vastattu
  Logout

*** Keywords ***

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element should contain  test-inforequest-state  ${state}

No inforequest is assigned to
  [Arguments]  ${Assignee name}
  Number of assigned inforequests  ${Assignee name}  0

Number of assigned inforequests
  [Arguments]  ${Assignee name}  ${Count}
  ## FIXME should match that td is actually in the assignee column
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'inforequest')]/td[contains(text(), '${Assignee name}')]  ${Count}
