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
  Wait Until  Element should be visible  jquery=select[name=attachmentType]
  Select From List  attachmentType  osapuolet.cv
  Choose File      xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  Click enabled by test id  userinfo-upload-ok
  Wait until  Element should be visible  xpath=//div[@id='dialog-userinfo-architect-upload']//div[@data-test-id='userinfo-upload-error']
  Click by test id  userinfo-upload-cancel

# FIXME: copy user attachments not implemented
Mikko copies his attachments to application
  [Tags]  firefox
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Omat-liitteet-${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  open
  Open tab  attachments
  #Click by test id  copy-user-attachments
  #Confirm yes no dialog
  #Wait Until  Table Should Contain  css=table.attachments-template-table  ${PDF_TESTFILE_NAME}

Copy own attachments button is not shown to non-architect
  [Tags]  firefox
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Wait until  Click Label  architect
  Save User Data
  Click by test id  back-button
  Wait until  Page should contain element  jquery=button[data-test-id=add-attachment]
  Page should not contain element  jquery=button[data-test-id=copy-own-attachments]

# FIXME: copy user attachments not implemented, cv is not added
Mikko deletes own attachment from application and submits
  [Tags]  fail
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Delete attachment  osapuolet.cv
  Wait Until  Element should not be visible  jquery=div#application-attachments-tab a:contains('${PDF_TESTFILE_NAME}')
  Submit application
  Logout

Sonja asks for the cv
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Add empty attachment template  CV  osapuolet  cv
  Wait Until Element Is Visible  jquery=div#application-attachments-tab tr[data-test-type='osapuolet.cv']
  ${trCount}=   Get Matching Xpath Count  //div[@id='application-attachments-tab']//tr[@data-test-type='osapuolet.cv']/preceding-sibling::tr
  ${index}=  Evaluate  ${trCount}+${1}
  Set Suite Variable  ${cvIndex}  ${index}
  Logout

Mikko logs in and sets himself architect
  As Mikko
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Wait until  Click Element  architect
  Save User Data
  Reload Page
  Wait Until  Checkbox Should Be Selected  architect

# FIXME: copy user attachments not implemented
Mikko copies own CV to application
  [Tags]  fail
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Click by test id  copy-user-attachments
  Confirm yes no dialog

# FIXME: copy user attachments not implemented
Mikko's CV should be uploaded to placeholder requested by Sonja
  [Tags]  fail
  Wait until  Element should contain  jquery=div#application-attachments-tab tr[data-test-type='osapuolet.cv']:nth-child(${cvIndex}) a  ${PDF_TESTFILE_NAME}
  Logout

# FIXME: copy user attachments not implemented
Application is given verdict
  [Tags]  fail
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Fetch verdict
  Wait until  Application state should be  verdictGiven
  Logout

# FIXME: copy user attachments not implemented
Mikko can add his attachments in post verdict state
  [Tags]  fail
  As Mikko
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Wait until  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='attachmentsCopyOwn']
  Click by test id  copy-user-attachments
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
