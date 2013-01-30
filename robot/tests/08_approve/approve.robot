*** Settings ***

Documentation   Mikko can't approve application
Suite setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  Create application  approve-app  753  75341600250030

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  
Mikko decides to submit application
  Click by test id  application-submit-btn
  
Mikko still can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko remembers that it is his turn to pick the kids from day care
  Logout

Sonja logs in for approval
  Sonja logs in
  Open the application  approve-app

Sonja approves application
  Click by test id  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  Logout
