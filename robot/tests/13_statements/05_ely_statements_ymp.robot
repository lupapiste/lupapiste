*** Settings ***

Documentation   Statement is sent to ELY-keskus, YMP permit type
Library         DateTime
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Pena sets up YMP application by filling person details and submitting
  Pena logs in
  Create application the fast way  ely-statements-ymp  564-416-25-22  lannan-varastointi
  ${id}=  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Set Suite Variable  ${appId}  ${id}
  Open tab  parties
  Open accordions  parties
  Select From List by label  xpath=//section[@data-doc-type='ilmoittaja']//select[@data-test-id='henkilo.userId']  Panaani Pena
  Sleep  0.5s
  Open accordions  parties
  Input text with jQuery  section[data-doc-type='ilmoittaja'] input[data-docgen-path='henkilo.henkilotiedot.hetu']  250876-8620
  Open accordions  parties
  Sleep  0.5s
  Submit application

Pena doesnt see anything in statements tab
  Open tab  statement
  Wait until  Element should not be visible by test id  add-ely-statement
  Wait until  Element should not be visible by test id  application-statements
  Logout

Olli creates statement request to ELY
  As Olli
  Open application  ely-statements-ymp  564-416-25-22
  Open tab  statement
  Wait until  Element should be visible by test id  add-ely-statement
  Click by test id  add-ely-statement
  Wait until  Element should be visible by test id  ely-statement-bubble
  Element should be disabled  xpath=//button[@data-test-id='bubble-dialog-ok']
  Select from list by label  ely-subtypes  Lausuntopyyntö maisematyöluvasta
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
  Logout

Pena comes to see the statement request in place
  As Pena
  Open application  ely-statements-ymp  564-416-25-22
  Open tab  statement
  ${messageId}=  Get Element Attribute  xpath=(//table[@data-test-id='application-statements']/tbody/tr)[1]  data-message-id
  Element should contain  xpath=//tr[@data-message-id='${messageId}']//td//span[@data-test-id='external-received']  1006789
  # Pena doesnt see link, only span containing description of the subtype
  Element should not be visible  xpath=//tr[@data-message-id='${messageId}']//a[@data-test-id='open-statement-0']
  Element should be visible  xpath=//tr[@data-message-id='${messageId}']//span[@data-test-id='open-statement-0']
  Element text should be  xpath=//tr[@data-message-id='${messageId}']//span[@data-test-id='open-statement-0']  Lausuntopyyntö maisematyöluvasta
  Logout

Olli logs back in, now ELY sends us the statement response
  As Olli
  Open application  ely-statements-ymp  564-416-25-22
  Open tab  statement
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
  Element should not be visible  xpath=//table[@data-test-id='targetted-attachments-table']/tbody//tr//td[contains(@class, 'remove-col')]//i

Olli asks for reply
  # For other than R, reply is visible
  Kill dev-box
  Element should be visible by test id  statement-tab-selector-reply-request
  Click by test id  statement-tab-selector-reply-request
  Wait until  Element should be visible  statement-reply-covering-note
  Input text  statement-reply-covering-note  Pistäppä vastaten
  Click element  statement-submit
  # Redirect back to statement tab
  Wait until  Element should be visible by test id  application-statements
  Wait until  Element text should be  xpath=//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-0']  Vastine pyydetty
  Logout

Pena logs in to check statement status
  As Pena
  Open application  ely-statements-ymp  564-416-25-22
  Open tab  statement
  Wait until  Element should contain  xpath=//table[@data-test-id='application-statements']//span[@data-test-id='statement-giver-name']  ELY-keskus
  Wait until  Element should contain  xpath=//table[@data-test-id='application-statements']//a[@data-test-id='open-statement-reply-0']  Anna vastine

Pena gives reply
  Click element  xpath=//table[@data-test-id='application-statements']//a[@data-test-id='open-statement-reply-0']
  Wait until  Element should contain  xpath=//section[@id='statement']//p[@id='statement-reply-cover-note']/span  Pistäppä vastaten
  Input text  statement-reply-text  OK
  Click element  statement-submit
  Confirm yes no dialog
  Wait until  Element text should be  xpath=//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-0']  Vastine annettu
  Logout

Frontend errors
  There are no frontend errors
