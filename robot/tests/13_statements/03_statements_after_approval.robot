*** Settings ***

Documentation   Environmental applications support statements after sent
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Sipoo logs in and adds statement giver
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset
  Create statement giver  ronja.sibbo@sipoo.fi  Pelastusviranomainen
  [Teardown]  logout

Mikko logs in and creates environmental application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Chaoyang gongyuan${secs}
  Set Suite Variable  ${appPropertyId}  753-416-25-22
  Create application with state  ${appname}  ${appPropertyId}  muistomerkin-rauhoittaminen  open
  [Teardown]  logout

Sonja logs in and adds statement giver to application
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  # Two statement givers and one empty row
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-2']

  Wait until  Input text  invite-statement-giver-saateText  Tama on saateteksti.
  Invite read-only statement giver  ronja.sibbo@sipoo.fi  01.06.2018
  Open statement  ronja.sibbo@sipoo.fi
  Wait until  Element should be visible  statement-cover-note
  Wait until  Element text should be  statement-cover-note  Tama on saateteksti.
  Return from statement

  # Checkbox selection and maaraaika are cleared, the saate text stays filled with value.
  Wait Until  Checkbox Should Not Be Selected  statement-giver-checkbox-0
  Wait Until  Textfield Value Should Be  //input[contains(@id,'add-statement-giver-maaraaika')]  ${empty}
  Wait Until  Textarea Value Should Be  //*[@id='invite-statement-giver-saateText']  Tama on saateteksti.

  Statement count is  1
  [Teardown]  Logout

Ronja logs in and adds attachment to statement draft
  Ronja logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  ronja.sibbo@sipoo.fi
  Wait test id visible  statement-attachments-no-attachments
  Scroll and click test id  add-statement-attachment
  Add attachment  statement  ${TXT_TESTFILE_PATH}  Important note
  Wait Until  Element should contain  jquery=table[data-test-id=statement-attachments-table] span  Important note
  [Teardown]  Logout

Mikko logs in and submits application
  Mikko logs in
  Open application  ${appname}  ${appPropertyId}
  Submit application
  [Teardown]  Logout

Sonja logs in and approves application
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Approve application ok
  [Teardown]  Logout

Ronja logs in and edits draft
  Ronja logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  ronja.sibbo@sipoo.fi
  Wait Until  Element should be enabled  statement-text
  Input text  statement-text  typed in statement text but not gonna submit the statement.
  Wait until  Select From List By Value  statement-type-select  puollettu
  Positive indicator icon should be visible
  Reload Page
  Wait Until  Text area should contain  statement-text  typed in statement text but not gonna submit the statement.

Ronja can delete and add attachment
  Scroll to test id  add-statement-attachment
  Click element  jquery=table[data-test-id=statement-attachments-table] i.lupicon-remove
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element Should Not Be Visible  jquery=table[data-test-id=statement-attachments-table]
  Scroll and click test id  add-statement-attachment
  Add attachment  statement  ${TXT_TESTFILE_PATH}  New information
  Wait Until  Element should contain  jquery=table[data-test-id=statement-attachments-table] span  New information

Ronja submits statement
  Wait and click  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should be visible  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']
  Open statement  ronja.sibbo@sipoo.fi
  Statement is disabled
  Wait until  Element should not be visible  statement-submit
  Element should not be visible  jquery=i.lupicon-remove
  [Teardown]  Return from statement

Statement status is visible for given statement in summary table
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-0']  Puollettu
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-0']
  [Teardown]  logout

Sonja logs in and request reply to Ronja's statement from Mikko
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  ronja.sibbo@sipoo.fi
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should be visible  statement-submit
  Input text  statement-reply-covering-note  please reply this statement
  Wait and click  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-0']  Vastine pyydetty
  [Teardown]  Logout

Mikko logs in and replies to the statement
  Mikko logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Wait and click  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//a[@data-test-id='open-statement-reply-0']
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Element should be visible  statement-reply-text
  Input text  statement-reply-text  I disagree!
  Positive indicator icon should be visible
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-0']  Vastine annettu
  [Teardown]  Logout

Sonja logs in and adds Vainamoinen as statement giver
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Invite 'manual' statement giver  2  Erikoislausuja  Vainamoinen  vainamoinen@example.com  05.06.2018
  # Three statement givers and one empty row
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-3']

  Statement count is  2

Sonja removes Vainamoinen
  Scroll and click test id  delete-statement-1
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Statement count is  1
  Logout

No frontend errors
  [Tags]  non-roboto-proof
  There are no frontend errors
