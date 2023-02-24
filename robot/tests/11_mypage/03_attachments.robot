*** Settings ***

Documentation   User's own attachments
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko uploads CV
  Mikko logs in
  Open My Page
  Wait for Page to Load  Mikko  Intonen
  Click enabled by test id  test-add-architect-attachment
  Select From List by value  attachmentType  osapuolet.cv
  Choose File      xpath=//input[@type='file']  ${TXT_TESTFILE_PATH}
  Click enabled by test id  userinfo-upload-ok
  Wait Until  Element should contain  xpath=//section[@id='mypage']//div[@data-test-id='filename']  ${TXT_TESTFILE_NAME}

Mikko uploads attachment with invalid mime
  Element should be visible by test id  test-add-architect-attachment
  Click enabled by test id  test-add-architect-attachment
  Wait Until  Element should be visible  jquery=select[name=attachmentType]
  Select From List by value  attachmentType  osapuolet.cv
  Choose File      xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  Click enabled by test id  userinfo-upload-ok
  Wait until  Element should be visible  xpath=//div[@id='dialog-userinfo-architect-upload']//div[@data-test-id='userinfo-upload-error']
  Click by test id  userinfo-upload-cancel

Mikko copies his attachments to application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Omat-liitteet-${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted
  Open tab  attachments
  Click by test id  copy-user-attachments
  Confirm yes no dialog
  Wait Until  Element should be visible  jquery=div#application-attachments-tab a:contains('${TXT_CONVERTED_NAME}')

Copy own attachments button is not shown to non-architect
  Open My Page
  Wait for Page to Load  Mikko  Intonen
  Wait until  Click Label  architect
  Save User Data
  Scroll and click test id  back-button
  Wait until  Page should contain element  jquery=label[data-test-id=add-attachments-label]
  Wait until  Element should not be visible  jquery=button[data-test-id=copy-user-attachments]

Mikko deletes own attachment from application
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Delete attachment  osapuolet.cv
  Wait Until  Element should not be visible  jquery=div#application-attachments-tab a:contains('${TXT_CONVERTED_NAME}')
  Logout

Sonja asks for the cv
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Add empty attachment template  CV  osapuolet  cv
  Wait Until  Element Should Be Visible  jquery=div#application-attachments-tab tr[data-test-type='osapuolet.cv']
  ${trCount}=   Get Matching Xpath Count  //div[@id='application-attachments-tab']//tr[@data-test-type='osapuolet.cv']/preceding-sibling::tr
  ${index}=  Evaluate  ${trCount}+${1}
  Set Suite Variable  ${cvIndex}  ${index}
  Logout

Mikko can't copy own attachments yet
  As Mikko
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Element should not be visible by test id  copy-user-attachments

Mikko sets himself architect
  Open My Page
  Wait for Page to Load  Mikko  Intonen
  Wait until  Scroll and click test id  architect-label
  Save User Data

Mikko returns to application
  Scroll and click test id  back-button

Mikko copies own CV to application
  Click by test id  copy-user-attachments
  Confirm yes no dialog

Mikko's CV should be uploaded to placeholder requested by Sonja
  Wait until  Element should contain  jquery=div#application-attachments-tab tr[data-test-type='osapuolet.cv']:nth-child(${cvIndex})  ${TXT_CONVERTED_NAME}

Application is given verdict
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Fetch verdict
  Wait until  Application state should be  verdictGiven
  Logout

Mikko can add his attachments in post verdict state
  As Mikko
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Wait until  Element should be visible  jquery=div#application-attachments-tab button[data-test-id=copy-user-attachments]
  Click by test id  copy-user-attachments
  Confirm yes no dialog
  Wait Until  Element should be visible  jquery=div#application-attachments-tab a:contains('${TXT_CONVERTED_NAME}')


*** Keywords ***

Save User Data
  Click enabled by test id  save-my-userinfo
  Positive indicator should be visible
  Positive indicator should not be visible

Wait for Page to Load
  [Arguments]  ${firstName}  ${lastName}
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}
  Open accordion by test id  mypage-personal-info-accordion
