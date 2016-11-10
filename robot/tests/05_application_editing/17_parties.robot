*** Settings ***

Documentation   Editing application parties
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Variables ***

${remove-button}  button[data-test-class='delete-schemas.hakija-r']
  
*** Test Cases ***

Pena logs in and creates application
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Party-on-${secs}
  Set Suite Variable  ${propertyId}  753-423-2-22
  Create application with state  ${appname}  ${propertyId}  aita  open

Pena executes test scenario
  Test scenario  
  [Teardown]  Logout
  
Sonja logs in
  Sonja logs in
  Open application  ${appname}  ${propertyId}

Sonja executes test scenario
  Test scenario
  [Teardown]  Logout

*** Keywords ***

Test scenario
  Open tab  parties
  No remove button
  Add hakija-r
  Two remove buttons
  Scroll and click  ${remove-button}:first
  Deny  dynamic-yes-no-confirm-dialog
  Scroll and click  ${remove-button}:last
  Confirm  dynamic-yes-no-confirm-dialog
  No remove button

No remove button
  Wait Until  Element should not be visible  jquery=${remove-button}

Add hakija-r
  Scroll and click test id  hakija-r_append_btn

Two remove buttons
  jQuery should match X times  ${remove-button}  2

