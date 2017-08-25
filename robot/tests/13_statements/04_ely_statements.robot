*** Settings ***

Documentation   Statement is sent to ELY-keskus
Library         DateTime
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Sonja sets up application by filling person details and submitting
  Sonja logs in
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
  Submit application


Sonja statement to ELY
  Open tab  statement
  Wait until  Element should be visible by test id  add-ely-statement
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

Time flies and ELY acknowledges statement request
  ${messageId}=  Get Element Attribute  xpath=(//table[@data-test-id='application-statements']/tbody/tr)[1]@data-message-id
  Go to  ${SERVER}/dev/ah/message-response?id=${appId}&messageId=${messageId}
  Wait until  Page should contain  "ok":true
  Go back
  Wait until  Element should be visible  xpath=//section[@id='application']//tr[@data-message-id='${messageId}']
  # Information about acknowledgedment has been saved to statement
  # (hardcoded value 1006789 from ah-example-response.xml)
  Element should contain  xpath=//tr[@data-message-id='${messageId}']//td//span[@data-test-id='external-received']  1006789


Frontend errors
  There are no frontend errors
