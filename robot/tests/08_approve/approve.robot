*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Execute Javascript  $("textarea[name='kuvaus']").val('Hieno hanke...').change();
  Execute Javascript  $("textarea[name='poikkeamat']").val('poikkeuksetta!').change();

Mikko sets himself the applicant
  Open tab  parties
  Select From List  //section[@data-doc-type="hakija"]//select[@name="henkilo.userId"]  Mikko Intonen
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko decides to submit application
  Submit application

Mikko still can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  [Teardown]  logout

Sonja logs in for approval
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja rejects hankkeen-kuvaus
  Wait Until  Element should be visible  xpath=//button[@data-test-id='reject-doc-hankkeen-kuvaus']
  Wait Until  Element should be visible  xpath=//button[@data-test-id='approve-doc-hankkeen-kuvaus']
  Click enabled by test id  reject-doc-hankkeen-kuvaus
  Wait Until  Element should not be visible  xpath=//button[@data-test-id='reject-doc-hankkeen-kuvaus']

Sonja approves hankkeen-kuvaus
  Wait Until  Element should be visible  xpath=//button[@data-test-id='approve-doc-hankkeen-kuvaus']
  Click enabled by test id  approve-doc-hankkeen-kuvaus
  Wait Until  Element should not be visible  xpath=//button[@data-test-id='approve-doc-hankkeen-kuvaus']
  Wait Until  Element should be visible  xpath=//button[@data-test-id='reject-doc-hankkeen-kuvaus']

Party tab has indicators
  Wait Until  Element should be visible  applicationPartyDocumentIndicator

Sonja approves application
  Click enabled by test id  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Party tab indicators have been reset
  Element should not be visible  applicationPartyDocumentIndicator

Sonja sees that some completion is needed
  Click enabled by test id  request-for-complement
  Wait Until  Application state should be  complement-needed
  [Teardown]  logout

Mikko comes back, fills in missing parts and no submit button enabled
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  [Teardown]  logout
