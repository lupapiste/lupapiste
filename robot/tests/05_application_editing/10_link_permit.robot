*** Settings ***

Documentation   Adding and removing of link permits
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application that will later act as link permit
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Link_permit_app_${secs}
  Set Suite Variable  ${propertyid}  753-416-17-15
  Create application the fast way  ${appname}  753  ${propertyid}  asuinrakennus
  Submit application
  Click enabled by test id  approve-application
  Throw in a verdict
  Wait until  Application state should be  verdictGiven

  ${linkPermitAppId} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${linkPermitAppId}

Sonja prepares the application to whom the link permit will be added
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  App_to_have_link_permit_${secs}
  Set Suite Variable  ${propertyid}  753-416-17-15
  Create application the fast way  ${appname}  753  ${propertyid}  asuinrakennus

Sonja adds a link permit (lupapistetunnus type) via the link permit dialog
  Open link permit dialog

  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]
  # "Valitse listasta" option is there by default, let's take it into account
  Xpath Should Match X Times  //select[@data-test-id="choose-linkPermit-select"]//option  2

  Select From List By Index  xpath=//select[@data-test-id="choose-linkPermit-select"]  1

  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]//option[contains(text(), ${linkPermitAppId})]
#  List Selection Should Be  xpath=//select[@data-test-id="choose-linkPermit-select"]  mika-tekstisisalto-tahan?
  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Go back to link permit dialog to verify that the just selected permit has disappeared from dropdown selection
  Open link permit dialog
  Xpath Should Match X Times  //select[@data-test-id="choose-linkPermit-select"]//option  1
  Click by test id  button-link-permit-dialog-cancel

Sonja removes link permit
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Click by test id  test-remove-link-permit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Sonja adds the link permit (kuntalupatunnus type) in the dialog
  Open link permit dialog
  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]
  Xpath Should Match X Times  //select[@data-test-id="choose-linkPermit-select"]//option  2
  Element should be visible  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]

  Input text  xpath=//input[@data-test-id="application-kuntalupatunnus-for-link-permit"]  123-456-abc-def

  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//a[@data-test-id="test-remove-link-permit"]
  Element text should be  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]  123-456-abc-def

*** Keywords ***

Open link permit dialog
  Click enabled by test id  application-add-link-permit-btn
  Wait until  Element should be visible  dialog-add-link-permit
