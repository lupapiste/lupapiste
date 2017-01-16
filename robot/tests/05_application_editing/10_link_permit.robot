*** Settings ***

Documentation   Adding and removing of link permits
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/approve_helpers.robot

*** Variables ***
  
${appname}            Link_permit_app
${baseApp}            App_to_have_link_permit
${propertyid}         753-423-5-10
${outsideapp}         Outside
${outsidepropertyid}  753-423-6-22
  
*** Test Cases ***

Sonja prepares the application that will later act as link permit
  Sonja logs in
  Create application the fast way  ${appname}  ${propertyid}  kerrostalo-rivitalo
  Submit application
  Approve application

  ${linkPermitAppId} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${linkPermitAppId}

Sonja creates application for the other property
  Create application the fast way  ${outsideapp}  ${outsidepropertyid}  pientalo

Sonja prepares the application to whom the link permit will be added
  Create application the fast way  ${baseApp}  ${propertyid}  kerrostalo-rivitalo

Sonja adds a link permit (lupapistetunnus type) via the link permit dialog
  Open link permit dialog

  # Empty selection is the first item
  jQuery should match X times  link-permit-autocomplete li  3

  Select from autocomplete by test id  link-permit-select  ${appname}, ${linkPermitAppId}
  Autocomplete selection by test id is  link-permit-select  ${appname}, ${linkPermitAppId}
  Test id disabled  application-kuntalupatunnus-for-link-permit
  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Go back to link permit dialog to verify that the just selected permit has disappeared from dropdown selection
  Open link permit dialog
  jQuery should match X times  link-permit-autocomplete li  2

Enforcing property id constraint removes outside application from the list
  Click label by test id  link-permit-same-property-label  
  jQuery should match X times  link-permit-autocomplete li  1
  Click label by test id  link-permit-same-property-label  
  Click by test id  button-link-permit-dialog-cancel

Sonja removes link permit
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Click by test id  test-remove-link-permit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Sonja adds the link permit (kuntalupatunnus type) in the dialog
  Open link permit dialog
  Xpath Should Match X Times  //div[@data-test-id="link-permit-select"]//li  3
  Element should be visible  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]

  Input text  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]  123-456-abc-def
  Test id disabled  link-permit-same-property-input
  jQuery should match X times  link-permit-autocomplete div.autocomplete-selection-wrapper.disabled  1  

  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Element text should be  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]  123-456-abc-def

*** Keywords ***

Open link permit dialog
  Click enabled by test id  application-add-link-permit-btn
  Wait until  Element should be visible  dialog-add-link-permit
  Wait test id visible  link-permit-select
