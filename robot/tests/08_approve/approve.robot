*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko can't approve application
  Mikko logs in
  Open the application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout

Sonja logs in for approval
  Sonja logs in
  Open the application

Sonja approves application
  Click enabled by test id  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout
