*** Settings ***

Documentation   Mikko can't approve application
Resource        ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo
  Execute Javascript  $("textarea[name='kuvaus']").val('Hieno hanke...').change();
  Execute Javascript  $("textarea[name='poikkeamat']").val('poikkeuksetta!').change();

Mikko sets himself the applicant
  Open tab  parties
  Open accordions  parties
  Select From List  //section[@data-doc-type="hakija-r"]//select[@name="henkilo.userId"]  Intonen Mikko
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Mikko adds an attachment
  Open tab  attachments
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Mikko decides to submit application
  Submit application

Mikko still can't approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  [Teardown]  logout

Sonja logs in for approval
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja rejects hankkeen-kuvaus
  Wait Until  Element should be visible  //div[@id="application-info-tab"]
  Open accordions  info
  Reject accordion  hankkeen-kuvaus

Sonja approves hankkeen-kuvaus
  Approve accordion  hankkeen-kuvaus

Sonja approves group kaytto
  Group neutral  kaytto
  Approve group  kaytto

Sonja approves group lammitys
  Group neutral  lammitys
  Approve group  lammitys

Sonja rejects group mitat
  Reject group  mitat
  Sonja accordion negated  uusiRakennus

Sonja approves accordion uusiRakennus
  Approve accordion  uusiRakennus

Sonja rejects group kaytto
  Reject group  kaytto
  Sonja accordion negated  uusiRakennus
  [Teardown]  logout

Mikko logs in and sees correct approval state
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Accordion approved  hankkeen-kuvaus
  Accordion negated  uusiRakennus
  Group rejected  mitat
  Group rejected  kaytto
  Group approved  lammitys
  [Teardown]  logout

Sonja logs in and approves groups kaytto and mitat
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Approve group  kaytto
  Approve group  mitat
  Sonja accordion approved  uusiRakennus

Party tab has indicators
  Wait Until  Element should be visible  applicationPartyDocumentIndicator

Sonja approves application
  Open tab  requiredFieldSummary
  Element should be visible  xpath=//button[@data-test-id="approve-application-summaryTab"]
  Element should be visible  xpath=//button[@data-test-id="approve-application"]
  Click enabled by test id  approve-application

Sonja cant re-approve application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']

Party tab indicators have been reset
  Wait Until  Element should not be visible  applicationPartyDocumentIndicator

Sonja sees that attachment has transferred indicator icon
  Open tab  attachments
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="transfered-muut.muu"]

Sonja sees that some completion is needed
  Click enabled by test id  request-for-complement
  Wait Until  Application state should be  complement-needed
  [Teardown]  logout

Mikko comes back, fills in missing parts and no submit button enabled
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open accordions  info
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='approve-application']
  [Teardown]  logout


*** Keywords ***

Accordion approved
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] div.sticky button.positive.approved i.approved
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')
  # Every group is approved or neutral
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] i.rejected


Sonja accordion approved
  [Arguments]  ${name}
  Accordion approved  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.rejected
  Element should not be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.approved
  # Every group is approved or neutral
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] .accordion_content button.approved


Accordion rejected
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.rejected
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')

Sonja accordion rejected
  [Arguments]  ${name}
  Accordion rejected  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.approved
  Element should not be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.rejected

# If a subgroup is rejected, the approved accordion is negated: it is no longer positive, has reject icon
# but both buttons are visible.
Accordion negated
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']


Sonja accordion negated
  [Arguments]  ${name}
  Accordion negated  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.approved
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.rejected

Group neutral
  [Arguments]  ${name}
  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group approved
  [Arguments]  ${name}
  Element should be visible  jquery=div.form-group[id*='${name}'] i.approved
  Element should not be visible  jquery=div.form-group[id*='${name}'] i.rejected
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('OK')
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] span:contains('Sibbo Sonja')


Sonja group approved
  [Arguments]  ${name}
  Group approved  ${name}
  Element should not be visible  jquery=button[data-test-id=approve-doc-${name}]
  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group rejected
  [Arguments]  ${name}
  Element should be visible  jquery=div.form-group[id*=${name}] i.rejected
  Element should not be visible  jquery=div.form-group[id*=${name}] i.approved
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Tarkennettavaa')
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Sibbo Sonja')

Sonja group rejected
  [Arguments]  ${name}
  Group rejected  ${name}
  Element should not be visible  jquery=button[data-test-id=reject-doc-${name}]
  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]

# Clickers

Click reject
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]
  Click Button  jquery=button[data-test-id=reject-doc-${name}]

Click approve
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Click Button  jquery=button[data-test-id=approve-doc-${name}]

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
