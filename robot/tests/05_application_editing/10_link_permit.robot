*** Settings ***

Documentation   Adding and removing of link permits
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application that will later act as link permit
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Link_permit_app_${secs}
  Set Suite Variable  ${baseApp}  App_to_have_link_permit_${secs}
  Set Suite Variable  ${propertyid}  753-423-5-10
  Create application the fast way  ${appname}  ${propertyid}  kerrostalo-rivitalo  open
  Submit application
  Click enabled by test id  approve-application

  ${linkPermitAppId} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${linkPermitAppId}

Sonja prepares the application to whom the link permit will be added
  Create application the fast way  ${baseApp}  ${propertyid}  kerrostalo-rivitalo  open

Sonja adds a link permit (lupapistetunnus type) via the link permit dialog
  Open link permit dialog

  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]
  # "Valitse listasta" option is there by default, let's take it into account
  ${permits} =  Get Matching Xpath Count  //select[@data-test-id="choose-linkPermit-select"]//option
  Should Be True  ${permits} >= 2
  Set Suite Variable  ${permits}

  Select From List  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${appname}, ${linkPermitAppId}
  List Selection Should Be  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${appname}, ${linkPermitAppId}
  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//a[@data-test-id="test-application-link-permit-lupapistetunnus"]
  Element should not be visible  xpath=//span[@data-test-id="test-application-link-permit-kuntalupatunnus"]

Go back to link permit dialog to verify that the just selected permit has disappeared from dropdown selection
  Open link permit dialog
  ${expectedCount} =  Evaluate  ${permits} - 1
  Xpath Should Match X Times  //select[@data-test-id="choose-linkPermit-select"]//option  ${expectedCount}

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
  Xpath Should Match X Times  //select[@data-test-id="choose-linkPermit-select"]//option  ${permits}
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
