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

Frontend errors
  There are no frontend errors
