*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot
Resource        ../common_keywords/approve_helpers.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753-416-25-30  sisatila-muutos
  Execute Javascript  $("textarea[name='kuvaus']").val('Hieno hanke...').change();
  Execute Javascript  $("textarea[name='poikkeamat']").val('poikkeuksetta!').change();

Mikko selects building
  Open tab  info
  Open accordions  info
  Wait Until  Select From List  //section[@data-doc-type='rakennuksen-muuttaminen']//select[@name='buildingId']  199887766E
  Confirm  dynamic-yes-no-confirm-dialog

Got owner info from backed
  Wait Until  Textfield Value Should Be  //section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path="rakennuksenOmistajat.0.yritys.yritysnimi"]  Testiyritys 9242

Mikko sets himself the applicant
  Open tab  parties
  Open accordions  parties
  Select From List  //section[@data-doc-type="hakija-r"]//select[@name="henkilo.userId"]  Intonen Mikko
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko adds an attachment
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Muu liite  Muu  Rakennuksen sisätilojen muutos (käyttötarkoitus ja/tai muu merkittävä sisämuutos)

Mikko decides to submit application
  Submit application

Mikko still can't approve application
  Wait Until  Element should be disabled  jquery=[data-test-id='approve-application']
  [Teardown]  Kill session

Ronja tries to approve application without permission
  Ronja logs in
  Open application  ${appname}  753-416-25-30
  Wait Until  Element should be disabled  jquery=[data-test-id='approve-application']
  Kill session

Sonja logs in for approval
  Sonja logs in
  Open application  ${appname}  753-416-25-30

# LPK-1995
Sonja has the power to delete paasuunnittelija
  Open tab  parties
  Wait Until  Element should be visible  xpath=//section[@data-doc-type='paasuunnittelija']//button[@data-test-class='delete-schemas.paasuunnittelija']

Sonja rejects hankkeen-kuvaus
  Open tab  info
  Wait Until  Element should be visible  //div[@id="application-info-tab"]
  Open accordions  info
  Reject accordion  hankkeen-kuvaus-rakennuslupa

Sonja approves hankkeen-kuvaus
  Approve accordion  hankkeen-kuvaus-rakennuslupa

Sonja approves group kaytto
  Group neutral  kaytto
  Approve group  kaytto

Sonja approves group lammitys
  Group neutral  lammitys
  Approve group  lammitys

Sonja rejects group mitat
  Reject group  mitat
  Sonja accordion negated  rakennuksen-muuttaminen

Sonja approves accordion rakennuksen-muuttaminen
  Approve accordion  rakennuksen-muuttaminen

Sonja rejects group kaytto
  Reject group  kaytto
  Sonja accordion negated  rakennuksen-muuttaminen
  [Teardown]  Kill session

Mikko logs in and sees correct approval state
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Accordion approved  hankkeen-kuvaus-rakennuslupa
  Accordion negated  rakennuksen-muuttaminen
  Group rejected  mitat
  Group rejected  kaytto
  Group approved  lammitys
  [Teardown]  Kill session

Sonja logs in and approves group kaytto: rakennuksen-muuttaminen is now approved.
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Approve group  kaytto
  Sonja accordion approved  rakennuksen-muuttaminen

Party tab has indicators
  Wait Until  Element should be visible  applicationPartyDocumentIndicator

Integration message monotor button is not enabled
  Test id disabled  show-integration-messages

Sonja approves application
  Approve application

Integration message monitor button is enabled
  Test id enabled  show-integration-messages

Authority opens integration message monitor
  Click enabled by test id  show-integration-messages

At least one message is displayed in the monitor
  Wait Until  Page Should Contain Element  //div[@data-test-id="integration-message-monitor"]/ul/li
  Close message monitor

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Party tab indicators have been reset
  Wait Until  Element should not be visible  applicationPartyDocumentIndicator

Sonja sees that attachment has sent indicator icon
  Open tab  attachments
  Wait until  Attachment indicator icon should be visible  sent  muut.muu

Building selector keeps its value
  Open tab  info
  Open accordions  info
  Element should be visible  //section[@data-doc-type='rakennuksen-muuttaminen']//select[@name='buildingId']
  List Selection Should Be  //section[@data-doc-type='rakennuksen-muuttaminen']//select[@name='buildingId']  199887766E

Sonja sees that some completion is needed
  Click enabled by test id  request-for-complement
  Wait Until  Application state should be  complementNeeded

Mikko comes back, fills in missing parts and no submit button enabled
  Kill session
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  Kill session

*** Keywords ***


Close message monitor
  Click Element  jquery=div.integration-message-monitor span.close


# Quick'n'dirty: wipe out current session and go to
# login page
Kill session
  Delete Cookie  ring-session
  Go To  ${LOGIN URL}
