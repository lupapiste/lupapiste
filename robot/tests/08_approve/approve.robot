*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.txt

*** Test Cases ***

Mikko can't approve application
  Mikko logs in
  Click element    test-application-link
  Wait until  Element should be disabled  test-approve-application
  Logout

Sonja could approve application
  Sonja logs in
  Click element  test-application-link
  Wait until page contains element  application-page-is-ready
  Element should be enabled  test-approve-application

Sonja approves application
  Click element  test-approve-application

Sonja cant re-approve application
  Wait until  page should contain element  application-page-is-ready
  Wait until  Element should be disabled  test-approve-application
  Logout
