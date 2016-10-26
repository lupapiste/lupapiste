*** Settings ***

Documentation    Mikko adds an attachment to application verdict
Suite Teardown   Logout
Resource         ../../common_resource.robot
Variables        variables.py

*** Test Cases ***

Mikko wants to build Olutteltta
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Olutteltta${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

The verdict tab is not visible
  Element should not be visible  application-verdict-tab

Mikko submits application & goes for lunch
  Submit application
  [Teardown]  logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja creates verdict with adds comment
  Go to give new verdict
  Title Should Be  ${appname} - Lupapiste
  Input verdict  123567890  6  01.05.2018  01.06.2018  Kaarina Krysp III
  Comment verdict  Myönnetään...

Sonja adds attachment to verdict
  Add attachment  verdict  ${TXT_TESTFILE_PATH}  ${EMPTY}  ${EMPTY}
  Wait test id visible  targetted-attachments-table
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait for jQuery
  Logout

Mikko can not delete attachment
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  paatoksenteko.paatosote  0
  Element should not be visible by test id  delete-attachment

*** Keywords ***

Comment verdict
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Wait Until  Element should be enabled  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Click element  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation
