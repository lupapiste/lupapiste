*** Settings ***

Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Keywords ***
Mikko creates an application and invites foreman
  Create project application
  Mikko invites foreman to application

Open linking dialog
  Click enabled by test id  application-add-link-permit-btn
  Wait until  Element should be visible  dialog-add-link-permit
  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]

*** Test Cases ***
Mikko inits applications
  Mikko logs in
  Mikko creates an application and invites foreman
  Mikko creates an application and invites foreman

Submit the base app
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Primary operation is  kerrostalo-rivitalo
  Submit application

Foreman sees his other foreman jobs
  Foreman logs in
  Foreman applies personal information to the foreman application  0
  Foreman applies personal information to the foreman application  1
  Foreman can see the first related construction info on the second foreman application

Foreman gets error message when trying to submit foreman notice before link permit has verdict
  Select from list by value  permitSubtypeSelect  tyonjohtaja-ilmoitus
  Open tab  requiredFieldSummary
  Click enabled by test id  application-submit-btn
  Wait Until  Click element  xpath=//div[@id='dynamic-yes-no-confirm-dialog']//button[@data-test-id='confirm-yes']
  Wait Until  Element should be visible  xpath=//div[@id='modal-dialog-content']
  Element should contain  xpath=//div[@id='modal-dialog-content']/div[@class='header']/span[@class="title"]  Työnjohtajan ilmoitus
  Confirm notification dialog
  Wait Until  Application state should be  draft

Can not link base app to foreman application
  Open project application
  Click by test id  accept-invite-button

  Open linking dialog

  ${app} =   Get From List  ${applications}  1
  ${linkPermitAppId} =   Get From List  ${foremanApps}  1

  Wait Until  Select From List  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${app}, ${linkPermitAppId}
  List Selection Should Be  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${app}, ${linkPermitAppId}
  Click enabled by test id  button-link-permit-dialog-add
  Wait Until  Element should be visible  xpath=//div[@id="dialog-add-link-permit"]//div[@data-bind="ltext: errorMessage"]
  Element Text Should Be  xpath=//div[@id="dialog-add-link-permit"]//div[@data-bind="ltext: errorMessage"]  Kohdehakemukseen ei voi lisätä enempää viitteitä

Create blank foreman application
  Create application the fast way  ${appname}  753-416-25-22  tyonjohtajan-nimeaminen-v2
  ${blankForemanAppId} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${blankForemanAppId}

Link the new foreman app from base app
  Open project application
  Open linking dialog

  Wait Until  Select From List  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${appname}, ${blankForemanAppId}
  List Selection Should Be  xpath=//select[@data-test-id="choose-linkPermit-select"]  ${appname}, ${blankForemanAppId}
  Click enabled by test id  button-link-permit-dialog-add
  Wait until  Element should not be visible  dialog-add-link-permit

Foreman app is linking to base app
  # ...even though we tried to create the link the other way
  Wait until  Page Should Contain Element  xpath=//section[@id="application"]//a[@data-test-app-linking-to-us="${blankForemanAppId}"]
