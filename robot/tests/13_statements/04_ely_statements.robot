*** Settings ***

Documentation   Statement is sent to ELY-keskus
Library         DateTime
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Sonja sets up application by filling person details and submitting
  Sonja logs in  False
  Create application the fast way  ely-statements  753-416-25-22  kerrostalo-rivitalo
  ${id}=  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Set Suite Variable  ${appId}  ${id}
  Open tab  parties
  Open accordions  parties
  Select From List  xpath=//section[@data-doc-type='hakija-r']//select[@data-test-id='henkilo.userId']  Sibbo Sonja
  Sleep  0.5s
  Open accordions  parties
  Input text with jQuery  section[data-doc-type='hakija-r'] input[data-docgen-path='henkilo.henkilotiedot.hetu']  250876-8620
  Open accordions  parties
  Sleep  0.5s
  Select From List  xpath=//section[@data-doc-type='maksaja']//select[@data-test-id='henkilo.userId']  Sibbo Sonja
  Sleep  0.5s
  Open accordions  parties
  Input text with jQuery  section[data-doc-type='maksaja'] input[data-docgen-path='henkilo.henkilotiedot.hetu']  250876-8620
  Sleep  0.5s
  Submit application

ELY statements aren't visible when not enabled
  Open tab  statement
  Element should not be visible by test id  add-ely-statement
  [Teardown]  Logout

Admin enables ELY statements
  SolitaAdmin logs in
  Go to page  organizations
  Wait test id visible  organization-search-term
  Fill test id  organization-search-term  753-R
  Scroll and Click test id  edit-organization-753-R
  Wait until  Element should be visible  xpath=//section[@id="organization"]//input[@id="docstore-enabled"]
  Select Checkbox  xpath=//section[@id="organization"]//input[@id="elyUspaEnabled"]
  Wait Until  Positive indicator should be visible
  Go back
  Click link  xpath=//a[@title="Kirjaudu ulos"]

ELY statements are visible
  Sonja logs in  False
  Open application  ely-statements  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible by test id  add-ely-statement

Sonja statement to ELY
  Click by test id  add-ely-statement
  Wait until  Element should be visible by test id  ely-statement-bubble
  Element should be disabled  xpath=//button[@data-test-id='bubble-dialog-ok']
  Select from list  ely-subtypes  Lausuntopyyntö rakennusluvasta
  Wait until  Element should be enabled  xpath=//button[@data-test-id='bubble-dialog-ok']
  Input text  ely-statement-saateText  Tama on saateteksti.
  ${TOMORROW} =     Add time to date  ${CURRENT_DATETIME}  1 days  %d.%m.%Y
  Set maaraaika-datepicker field value  ely-maaraaika  ${TOMORROW}
  Element should be visible  ely-lang

  Click by test id  bubble-dialog-ok
  Positive indicator should be visible
  Positive indicator should not be visible
  Statement count is  1
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-0']

Time flies and ELY acknowledges statement request
  ${messageId}=  Get Element Attribute  xpath=(//table[@data-test-id='application-statements']/tbody/tr)[1]  data-message-id
  Go to  ${SERVER}/dev/ah/message-response?id=${appId}&messageId=${messageId}
  Wait until  Page should contain  "ok":true
  Go back
  Wait until  Element should be visible  xpath=//section[@id='application']//tr[@data-message-id='${messageId}']
  # Information about acknowledgedment has been saved to statement
  # (hardcoded value 1006789 from ah-example-response.xml)
  Element should contain  xpath=//tr[@data-message-id='${messageId}']//td//span[@data-test-id='external-received']  1006789

Great, now ELY sends us the statement response
  ${statement}=  Get Element Attribute  xpath=(//table[@data-test-id='application-statements']/tbody/tr)[1]  data-statement-id
  Go to  ${SERVER}/dev/ah/statement-response?id=${appId}&statement-id=${statement}
  Wait until  Page should contain  "ok":true
  Go back
  Wait until  Element should be visible  xpath=//section[@id='application']//tr[@data-statement-id='${statement}']
  # Correct data from ah-example-statement-response.xml is seen in statement table
  Element text should be  xpath=//section[@id='application']//tr[@data-statement-id='${statement}']//span[@data-test-class='statement-status']  Puollettu
  Element should contain  xpath=//section[@id='application']//tr[@data-statement-id='${statement}']//span[@data-test-id='statement-giver-name']  Eija Esimerkki
  # Statement has attachments
  Element should be visible  xpath=//section[@id='application']//tr[@data-statement-id='${statement}']//i[contains(@class, 'lupicon-paperclip')]

Open statement, has data
  Open statement  ely-uspa@lupapiste.fi
  Wait until  Element should be visible  statement-cover-note
  Element should be disabled  statement-type-select
  Element should be disabled  statement-text
  Textarea value should be  statement-text  Hyvä homma
  Xpath should match X times  //table[@data-test-id='targetted-attachments-table']/tbody//tr  1
  Element should not be visible  xpath=//table[@data-test-id='statement-attachments-table']/tbody//tr//td[contains(@class, 'remove-col')]//i

Frontend errors
  There are no frontend errors
