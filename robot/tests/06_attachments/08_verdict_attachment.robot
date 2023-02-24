*** Settings ***

Documentation    Mikko adds an attachment to application verdict
Suite Teardown   Logout
Resource         ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
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

Sonja creates verdict with comment and attachment
  Go to give new legacy verdict
  Input legacy verdict  123567890  Kaarina Krysp III  Myönnetty  01.05.2018
  Comment verdict  Myönnetään...
  Pate upload  0  ${TXT_TESTFILE_PATH}  Päätösote  Päätösote
  Pate batch ready

Sonja publishes the verdict and logs out
  Publish verdict
  Click back
  [Teardown]  Logout

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
