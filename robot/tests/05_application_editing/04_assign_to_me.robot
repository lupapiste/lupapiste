*** Settings ***

Documentation  Sonja can assign application to herself
Suite setup    Sonja logs in
Test setup     Wait Until  Ajax calls have finished
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja can assign a non-assigned application
  Application is not assigned
  Click element  test-assign-to-me
  
Assignee has changed
  Application is assigned  Sonja Sibbo

*** Keywords ***

Application is not assigned
  Element should be visible  test-assign-to-me
  
Application is assigned
  [Arguments]  ${whom}
  Wait until page contains element  test-assigned-to-me
  Element should contain  test-assigned-to-me  Sonja Sibbo