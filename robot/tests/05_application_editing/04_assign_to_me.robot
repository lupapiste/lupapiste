*** Settings ***

Documentation  Sonja can assign application to herself
Suite setup    Sonja logs in
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja can assign a non-assigned application
  Wait Until  Application is not assigned
  Wait and click by test class  assign-to-me

Assignee has changed
  Wait Until  Application is assigned  Sonja Sibbo

*** Keywords ***

Application is not assigned
  Element should be visible by test class  assign-to-me
  
Application is assigned
  [Arguments]  ${whom}
  Wait until page contains element by test class  assigned-to-me
  Element should contain by test class  assigned-to-me  Sonja Sibbo
