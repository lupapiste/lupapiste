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
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  operation=Rakennuksen sisätilojen muutos (käyttötarkoitus ja/tai muu merkittävä sisämuutos)
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Mikko decides to submit application
  Submit application

Mikko still can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  [Teardown]  Kill session

Ronja tries to approve application without permission
  Ronja logs in
  Open application  ${appname}  753-416-25-30
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  Kill session

Sonja logs in for approval
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja rejects hankkeen-kuvaus
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

Sonja approves application
  Approve application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Party tab indicators have been reset
  Wait Until  Element should not be visible  applicationPartyDocumentIndicator

Sonja sees that attachment has transferred indicator icon
  Open tab  attachments
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="transfered-muut.muu"]

Building selector keeps its value
  Open tab  info
  Open accordions  info
  Element should be visible  //section[@data-doc-type='rakennuksen-muuttaminen']//select[@name='buildingId']
  List Selection Should Be  //section[@data-doc-type='rakennuksen-muuttaminen']//select[@name='buildingId']  199887766E

Sonja sees that some completion is needed
  Click enabled by test id  request-for-complement
  Wait Until  Application state should be  complementNeeded
  Kill session

Mikko comes back, fills in missing parts and no submit button enabled
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  Kill session

*** Keywords ***

Accordion approved
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] div.sticky button.positive i.approved
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')
  # Every group is approved or neutral
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] i.rejected


Approve button visible
  [Arguments]  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] button[data-test-id=approve-doc-${name}]

Reject button visible
  [Arguments]  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] button[data-test-id=reject-doc-${name}]

Approve button not visible
  [Arguments]  ${name}
  Element should not be visible  jquery=section[data-doc-type=${name}] button[data-test-id=approve-doc-${name}]

Reject button not visible
  [Arguments]  ${name}
  Element should not be visible  jquery=section[data-doc-type=${name}] button[data-test-id=reject-doc-${name}]

Sonja accordion approved
  [Arguments]  ${name}
  Accordion approved  ${name}
  Approve button not visible  ${name}
  Reject button visible  ${name}
  # Every group is approved
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] .accordion_content i.lupicon-check


Accordion rejected
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.rejected
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')

Sonja accordion rejected
  [Arguments]  ${name}
  Accordion rejected  ${name}
  Approve button visible  ${name}
  Reject button not visible  ${name}

# If a subgroup is rejected, the approved accordion is negated: it is no longer positive, has reject icon
# but both buttons are visible.
Accordion negated
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']


Sonja accordion negated
  [Arguments]  ${name}
  Accordion negated  ${name}
  Approve button visible  ${name}
  Reject button visible  ${name}
  # Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.approved
  # Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.rejected

Group neutral
  [Arguments]  ${name}
  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group approved
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] i.approved
  Wait Until  Element should not be visible  jquery=div.form-group[id*='${name}'] i.rejected
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('OK')
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] span:contains('Sibbo Sonja')


Sonja group approved
  [Arguments]  ${name}
  Group approved  ${name}
  Wait Until  Element should not be visible  jquery=button[data-test-id=approve-doc-${name}]
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group rejected
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] i.rejected
  Wait Until  Element should not be visible  jquery=div.form-group[id*=${name}] i.approved
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Tarkennettavaa')
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Sibbo Sonja')

Sonja group rejected
  [Arguments]  ${name}
  Group rejected  ${name}
  Wait Until  Element should not be visible  jquery=button[data-test-id=reject-doc-${name}]
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]

# Clickers

Click reject
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]
  Scroll and click test id  reject-doc-${name}

Click approve
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Scroll and click test id  approve-doc-${name}

Approve accordion
  [Arguments]  ${name}
  Click approve  ${name}
  Sonja accordion approved  ${name}

Reject accordion
  [Arguments]  ${name}
  Click reject  ${name}
  Sonja accordion rejected  ${name}

Approve group
  [Arguments]  ${name}
  Click approve  ${name}
  Sonja group approved  ${name}

Reject group
  [Arguments]  ${name}
  Click reject  ${name}
  Sonja group rejected  ${name}

# Quick'n'dirty: wipe out current session and go to
# login page
Kill session
  Delete Cookie  ring-session
  Go To  ${LOGIN URL}
