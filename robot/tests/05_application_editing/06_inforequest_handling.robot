*** Settings ***

Documentation   Inforequest state handling
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new inforequest
  Mikko logs in
  Create inforequest  Inforequest handling  753  75341600250030  Jiihaa
  Logout
  
Authority assigns an inforequest to herself
  Sonja logs in
  Wait until  Number of assigned inforequests  Sonja  0
  Open the inforequest  Inforequest handling
  Wait until page contains element  inforequest-assignee-select
  Select From List  inforequest-assignee-select  777777777777777777000023
  Click by test id  inforequest-requests
  Wait until  Number of assigned inforequests  Sonja  1
  Logout

Applicant marks inforequest answered
  Mikko logs in
  Open the inforequest  Inforequest handling
  Wait until  Inforequest state is  Avoin
  Wait and click  test-mark-inforequest-answered
  Wait until  Inforequest state is  Vastattu
  Logout

*** Keywords ***

Open the request
  [Arguments]  ${address}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Open the application
  [Arguments]  ${address}
  Open the request  ${address}
  Wait until  Element should be visible  application
  Wait until  Element should contain  xpath=//span[@data-test-id='application-title']  ${address}

Open the inforequest
  [Arguments]  ${address}
  Open the request  ${address}
  Wait Until  Element should be visible  inforequest
  Wait until  Element should contain  xpath=//span[@data-test-id='inforequest-title']  ${address}

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element should contain  test-inforequest-state  ${state}

Inforequest is assigned to
  [Arguments]  ${address}  ${name}
  Wait until  Element should contain  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}'/td[@data-test-col-name='authority']  ${name}
