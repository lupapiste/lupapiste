*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko can't approve application
  Mikko logs in
  Wait and click by test class  application-link
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout

Sonja logs in for approval
  Sonja logs in
  Wait and click by test class  application-link

Sonja approves application
  Wait and click enabled button  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id="approve-application"]
  Logout
