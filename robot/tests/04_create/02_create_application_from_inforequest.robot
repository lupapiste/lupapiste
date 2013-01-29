*** Settings ***

Documentation   Mikko creates a new inforequest and then an application from it.
Suite setup     Mikko logs in
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new inforequest
  Create inforequest  Latokuja 10, Sipoo  753  75341600250030  Jiihaa
  
There are no attachments at this stage
  Element should not be visible  xpath=//*[@data-test-id='inforequest-attachments-table']
  Element should be visible  xpath=//*[@data-test-id='inforequest-attachments-no-attachments']

Mikko creates new application from inforequest
  Click by test id  inforequest-convert-to-application
  Wait until  Element should be visible  application
  Wait until  Element should contain  xpath=//span[@data-test-id='application-title']  Latokuja 10, Sipoo
  
Proper attachment templates are present
  Click by test id  application-open-application-tab
  Wait until  Element should be visible  application-attachments-tab
  Element should be visible  xpath=//*[@data-test-id='application-attachments-table']
  Element should not be visible  xpath=//*[@data-test-id='application-attachments-no-attachments']

Mikko closes application
  Click by test id  application-cancel-btn
  Wait Until  Element should be visible  applications
