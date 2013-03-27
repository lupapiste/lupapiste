*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753  75341600250030
  Input Text  kuvaus  Hieno hanke...
  Input Text  poikkeamat  poikkeuksetta!
  Focus  kuvaus
  Wait for jQuery

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko decides to submit application
  Submit application

Mikko still can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko remembers that it is his turn to pick the kids from day care
  Logout

Sonja logs in and sees that some completion is needed
  Sonja logs in
  Open application  ${appname}  75341600250030
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='request-for-complement']
  Click by test id  request-for-complement
  Logout

Mikko comes back, fills in missing parts and makes a resubmit
  Mikko logs in
  Open application  ${appname}  75341600250030
  Submit application
  Logout

Sonja logs in for approval
  Sonja logs in
  Open application  ${appname}  75341600250030

Sonja approves application
  Click by test id  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  Logout
