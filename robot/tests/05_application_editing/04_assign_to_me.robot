*** Settings ***

Documentation  Sonja can assign application to herself
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  Create application the fast way  assign-to-me  753  75341600250030
  Add comment  hojo-hojo
  Logout
  
Application is not assigned
  Sonja logs in
  Open application  assign-to-me
  Application is not assigned

Sonja assign application to herself
  Select From List  xpath=//select[@data-test-id='application-assigneed-authority']  Sonja Sibbo
  
Assignee has changed
  Wait Until  Application is assigned to  Sonja Sibbo
  
*** Keywords ***

Application is assigned to
  [Arguments]  ${to}
  Wait until  Element should be visible  xpath=//select[@data-test-id='application-assigneed-authority']
  ${assignee} =  Get selected list label  xpath=//select[@data-test-id='application-assigneed-authority']
  Should be equal  ${assignee}  ${to}

Application is not assigned
  Application is assigned to  Valitse..
  
