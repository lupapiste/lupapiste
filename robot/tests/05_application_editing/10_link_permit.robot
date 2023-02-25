*** Settings ***

Documentation   Adding and removing of link permits
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../../common_keywords/approve_helpers.robot

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
  Create application the fast way  ${baseApp}  ${propertyid}  raktyo-aloit-loppuunsaat

Sonja adds a link permit (lupapistetunnus type) via the link permit dialog
  Open link permit card
  # Empty selection is the first item
  Link permit select item count is  2
  Fill and submit link permit card

Go back to link permit dialog to verify that the just selected permit has disappeared from dropdown selection
  Open link permit card
  Link permit select item count is  1

Enforcing property id constraint removes outside application from the list
  Click label by test id  link-permit-same-property-label
  Link permit select item count is  2
  Click label by test id  link-permit-same-property-label
  Click by test id  add-link-permit-back

Sonja removes link permit
  Remove link permit

Sonja adds link permit using the required link permit button
  Open required link permit card
  Fill and submit link permit card

Sonja removes link permit again
  Remove link permit

Sonja adds the link permit (kuntalupatunnus type) in the dialog
  Open link permit card
  Link permit select item count is  2
  Element should be visible  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]

  Input text  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]  123-456-abc-def
  Test id disabled  link-permit-same-property-input
  Element Should Be Visible  xpath=//div[contains(@class, 'autocomplete-selection-wrapper') and contains(@class, 'disabled')]

  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Element text should be  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]  123-456-abc-def

*** Keywords ***

Open link permit card
  Click enabled by test id  application-add-link-permit-btn
  Wait until  test id visible  add-link-permit-card
  Wait test id visible  link-permit-select

Open required link permit card
  Click enabled by test id  add-required-link-permit
  Wait until  test id visible  add-link-permit-card
  Wait test id visible  link-permit-select

Remove link permit
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Click by test id  test-remove-link-permit
  Confirm yes no dialog
  Wait until  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Link permit select item count is
  [Arguments]  ${count}
  # items are not rendered in DOM when autocomplete is not active
  ${autocomplete_open}=  Get Element Count  xpath=//div[@data-test-id="link-permit-select"]//ul
  Run Keyword Unless  ${autocomplete_open}  Click element  xpath=//div[@data-test-id="link-permit-select"]
  Xpath Should Match X Times  //div[@data-test-id="link-permit-select"]//ul//li  ${count}

Fill and submit link permit card
  Select from autocomplete by test id  link-permit-select  ${appname}, ${linkPermitAppId}
  Autocomplete selection by test id is  link-permit-select  ${appname}, ${linkPermitAppId}
  Test id disabled  application-kuntalupatunnus-for-link-permit
  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]
  No such test id  add-required-link-permit
