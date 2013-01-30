*** Settings ***

Documentation   Inforequest state handling
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new inforequest
  Mikko logs in
  Create inforequest  inforequest-handling  753  75341600250030  Jiihaa
  Logout
  
Authority assigns an inforequest to herself
  Sonja logs in
  Inforequest is not assigned  inforequest-handling
  Open inforequest  inforequest-handling
  Wait until  Element should be visible  inforequest-assignee-select
  Select From List  inforequest-assignee-select  777777777777777777000023

Now Sonja is marked as authority
  Go to page  applications
  Inforequest is assigned to  inforequest-handling  Sonja Sibbo
  Logout

Mikko sees Sonja as authority
  Mikko logs in
  Inforequest is assigned to  inforequest-handling  Sonja Sibbo

Applicant marks inforequest answered
  Open inforequest  inforequest-handling
  Wait until  Inforequest state is  Avoin
  Click by test id  inforequest-mark-answered
  Wait until  Inforequest state is  Vastattu
  Logout

*** Keywords ***

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element should contain  test-inforequest-state  ${state}

Inforequest is not assigned
  [Arguments]  ${address}
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td[@data-test-col-name='authority']  ${EMPTY}
  
Inforequest is assigned to
  [Arguments]  ${address}  ${name}
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td[@data-test-col-name='authority']  ${name}
