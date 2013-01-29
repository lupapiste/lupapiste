*** Settings ***

Documentation   Mikko creates a new inforequest and then an application from it.
Suite setup     Mikko logs in
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new inforequest
  Create inforequest  Latokuja 10, Sipoo  753  75341600250030  Jiihaa
  Log  jiihaa
  
There are no attachments at this stage
  Log  jiihaa

Mikko creates new application from inforequest
  Click by test id  inforequest-convert-to-application
  Wait until  Element should be visible  application
  Wait until  Element should contain  xpath=//span[@data-test-id='application-title']  Latokuja 10, Sipoo
  
Proper attachment templates and documents are present
  Log  jiihaa

Mikko closes application
  Click by test id  application-cancel-btn
  Wait Until  Element should be visible  applications
