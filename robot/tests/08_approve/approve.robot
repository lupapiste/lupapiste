*** Settings ***

Documentation   Mikko can't approve application
Test setup      Wait Until  Ajax calls have finished
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko can't approve application
  Mikko logs in
  Click element    test-application-link
  Wait until  Element should be disabled  test-approve-application
  Logout

Sonja logs in for approval
  Sonja logs in
  Click element  test-application-link

Sonja could approve application
  Element should be enabled  test-approve-application

Sonja approves application
  Click element  test-approve-application

Sonja cant re-approve application
  Element should be disabled  test-approve-application
  Logout
