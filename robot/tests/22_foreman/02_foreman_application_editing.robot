*** Settings ***

Resource        ../../common_resource.robot
Resource        ../common_keywords/approve_helpers.robot
Resource        keywords.robot
Suite Setup     Initialize
Suite Teardown  Logout

*** Keywords ***
Sonja creates an application and invites foreman
  Create project application
  Sonja invites foreman to application

Open linking dialog
  Click enabled by test id  application-add-link-permit-btn
  Wait until  Element should be visible  dialog-add-link-permit
  Wait Until  Element should be visible  xpath=//select[@data-test-id="choose-linkPermit-select"]

*** Test Cases ***
Sonja inits applications
  Sonja logs in
  Sonja creates an application and invites foreman
  Sonja creates an application and invites foreman

Submit the base apps
  Submit foreman base app  0
  Submit foreman base app  1
  [Teardown]  Logout

Foreman fills personal information and submits the first foreman application 
  Foreman logs in
  Foreman applies personal information to the foreman application  0
  
  Select From List By Value  permitSubtypeSelect  tyonjohtaja-hakemus
  Positive indicator should be visible
  Submit application

Foreman cannot see related projects
  Foreman applies personal information to the foreman application  1
  No such test id  'muutHankkeet.1.luvanNumero'
  
Foreman gets error message when trying to submit foreman notice before link permit has verdict
  Select from list by value  permitSubtypeSelect  tyonjohtaja-ilmoitus
  Open tab  requiredFieldSummary
  Element should be disabled  xpath=//button[@data-test-id='application-submit-btn']
  Submit application error should be  error.foreman.notice-not-submittable
  Wait Until  Application state should be  open

Foreman application can be submitted
  Select From List By Value  permitSubtypeSelect  tyonjohtaja-hakemus
  Positive indicator should be visible
  Submit application
  [Teardown]  Logout

Sonja logs in and gets verdict for the first foreman application
  Sonja logs in
  Verdict for foreman application  0
  [Teardown]  Logout

Foreman logs in and checks related projects on the second foreman application
  Foreman logs in
  Open foreman application  1
  Open tab  parties
  Open foreman accordions
  Scroll and click test id  fill-info-button
  Wait for jQuery
  Check related project  0  

Can not link base app to foreman application
  Open project application
  Confirm yes no dialog

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
  Logout

Authority opens the submitted foreman application
  Sonja logs in
  Open foreman application  1
  Wait until  Application state should be  submitted

Authority decides that a verdict is not required
  Select From List By Value  permitSubtypeSelect  tyonjohtaja-ilmoitus
  Positive indicator should be visible

Authority tries to send application to backend
  Click enabled by test id  approve-application

Can not be send before base app
  Confirm  integration-error-dialog

Approve base app
  Go back to project application
  Approve application

Fetch verdict to base app
  Open tab  verdict
  Fetch verdict

Approve foreman app
  Open foreman application  1
  Click enabled by test id  approve-application
  Wait Until  Application state should be  acknowledged

Link foreman approval to base app
  Go back to project application
  Wait Until  Application state should be  verdictGiven
  Open tab  tasks

  Required foreman state is  Vastaava työnjohtaja  missing

  ${foremanAppId} =  Get From List  ${foremanApps}  1
  Focus  xpath=//select[@data-test-id="foreman-selection-0"]
  Select From List By Value  xpath=//tr[@data-test-name="Vastaava työnjohtaja"]//select[@data-test-id="foreman-selection-0"]  ${foremanAppId}

  Wait Until  Required foreman state is  Vastaava työnjohtaja  ok

On second thought, complement is needed
  Open foreman application  1
  Click enabled by test id  request-for-complement
  Wait Until  Application state should be  complementNeeded

Verdict can't be given
  Open tab  verdict
  Element should not be visible  //div[@id="application-verdict-tab"]//button[@data-test-id="give-verdict"]

Foreman state has reset on base app
  Go back to project application
  Wait Until  Application state should be  verdictGiven
  Open tab  tasks
  Wait Until  Required foreman state is  Vastaava työnjohtaja  new

Change subtype back to foreman application
  Open foreman application  1
  Wait until  Select from list by value  permitSubtypeSelect  tyonjohtaja-hakemus
  Positive indicator should be visible

Verdict could be given
  Open tab  verdict
  Wait Until  Element should be visible  //div[@id="application-verdict-tab"]//button[@data-test-id="give-verdict"]

Re-send and give verdict
  Click enabled by test id  approve-application
  Wait until  Application state should be  sent
  Submit empty verdict  foremanVerdictGiven
  Application state should be  foremanVerdictGiven

Foreman state has changed on base app
  Go back to project application
  Open tab  tasks
  Wait Until  Required foreman state is  Vastaava työnjohtaja  ok

Deleting the verdict sets application back to previous state
  Open foreman application  1
  Open tab  verdict

  Wait Until  Element should be visible  //div[@id="application-verdict-tab"]//*[@data-test-id="delete-verdict-from-listing"]
  Click element  xpath=//div[@id="application-verdict-tab"]//*[@data-test-id="delete-verdict-from-listing"]
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should not be visible  //div[@id="application-verdict-tab"]//*[@data-test-id="delete-verdict-from-listing"]

  Application state should be  sent

Foreman state has reset again on base app
  Go back to project application
  Open tab  tasks
  Wait Until  Required foreman state is  Vastaava työnjohtaja  new
