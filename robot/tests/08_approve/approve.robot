*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko can't approve application
  [Tags]  fail
  Mikko logs in
  Open some application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout

Sonja logs in for approval
  [Tags]  fail
  Sonja logs in
  Open some application

Sonja approves application
  [Tags]  fail
  Wait and click enabled button  approve-application

Sonja cant re-approve application
  [Tags]  fail
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout
