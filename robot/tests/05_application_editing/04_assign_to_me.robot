*** Settings ***

Documentation  Sonja can assign application to herself
Suite setup    Sonja logs in
Test teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja can assign a non-assigned application
  Wait until page contains element  test-assign-to-me
  Application is not assigned
  Click element  test-assign-to-me
  Wait until page contains element  applications-page-is-ready
  Application is assigned  Sonja Sibbo

*** Keywords ***

Application is not assigned
  Element should be visible  test-assign-to-me
  
Application is assigned
  [Arguments]  ${whom}
  Wait until page contains element  test-assigned-to-me
  Element should contain  test-assigned-to-me  Sonja Sibbo