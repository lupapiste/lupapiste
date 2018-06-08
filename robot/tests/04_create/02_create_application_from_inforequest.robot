*** Settings ***

Documentation   Mikko creates a new inforequest and then an application from it.
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new inforequest
  Mikko logs in
  Create inforequest the fast way  create-app-from-info  360603.153  6734222.95  753-416-25-30  kerrostalo-rivitalo  Jiihaa

There are no attachments at this stage
  Element should not be visible  xpath=//*[@data-test-id='inforequest-attachments-table']
  Element should be visible  xpath=//*[@data-test-id='inforequest-attachments-no-attachments']

Mikko creates new application from inforequest
  Click by test id  inforequest-convert-to-application
  Wait until  Element should be visible  application
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  753-416-25-30
  Wait until  Element should be visible  xpath=//*[contains(text(),'Hankkeen kuvaus')]

Proper attachment templates are present
  Open tab  attachments
  Wait until  Element should be visible  xpath=//rollup[@data-test-level='accordion-level-0']

Mikko closes application
  Cancel current application
  Wait Until  Element should be visible  applications
