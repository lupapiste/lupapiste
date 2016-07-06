*** Settings ***

Documentation   User's own attachments
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko uploads CV
  [Tags]  firefox
  Mikko logs in
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Click enabled by test id  test-add-architect-attachment
  Select From List  attachmentType  osapuolet.cv
  Choose File      xpath=//input[@type='file']  ${TXT_TESTFILE_PATH}
  Click enabled by test id  userinfo-upload-ok
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}

Mikko uploads attachment with invalid mime
  [Tags]  firefox
  Element should be visible by test id  test-add-architect-attachment
  Click enabled by test id  test-add-architect-attachment
  Select From List  attachmentType  osapuolet.cv
  Choose File      xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  Click enabled by test id  userinfo-upload-ok
  Wait until  Element should be visible  xpath=//div[@id='dialog-userinfo-architect-upload']//div[@data-test-id='userinfo-upload-error']
  Click by test id  userinfo-upload-cancel

Mikko copies his attachments to application
  [Tags]  firefox
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Omat-liitteet-${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo
  Open tab  attachments
  Select attachment operation option from dropdown  attachmentsCopyOwn
  Confirm yes no dialog
  Wait Until  Table Should Contain  css=table.attachments-template-table  ${PDF_TESTFILE_NAME}

Copy own attachments button is not shown to non-architect
  [Tags]  firefox
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Wait until  Click Element  architect
  Save User Data
  Reload Page
  Wait for Page to Load  Mikko  Intonen
  Wait until  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='attachmentsCopyOwn']

Mikko deletes own attachment from application
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Click element  xpath=//div[@id="application-attachments-tab"]//span[@data-test-icon="delete-osapuolet.cv"]
  Confirm yes no dialog
  Wait Until  Element should not be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Application is submited and given verdict
  Submit application
  Logout
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Fetch verdict
  Wait until  Application state should be  verdictGiven
  Logout

Mikko logs in and sets himself architect
  As Mikko
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Wait until  Click Element  architect
  Save User Data
  Reload Page
  Wait Until  Checkbox Should Be Selected  architect

Mikko can add his attachments in post verdict state
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Wait until  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='attachmentsCopyOwn']
  Select attachment operation option from dropdown  attachmentsCopyOwn
  Confirm yes no dialog
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${PDF_TESTFILE_NAME}')]


*** Keywords ***

Save User Data
  Click enabled by test id  save-my-userinfo
  Positive indicator should be visible

Wait for Page to Load
  [Arguments]  ${firstName}  ${lastName}
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}
  Open accordion by test id  mypage-personal-info-accordion
