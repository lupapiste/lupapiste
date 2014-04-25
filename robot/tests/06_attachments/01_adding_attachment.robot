*** Settings ***

Documentation  Mikko adds an attachment
Suite teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko goes to empty attachments tab
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Mikko logs in
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Open tab  attachments

Download all attachments should be disabled
  Wait Until  Element should not be visible   xpath=//a[@data-test-id="application-download-all-attachement"]

Mikko adds txt attachment without comment
  [Tags]  attachments
  Add attachment  ${TXT_TESTFILE_PATH}  ${EMPTY}
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Download all attachments should be enabled
  Wait Until  Element should be visible   xpath=//a[@data-test-id="application-download-all-attachement"]

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu

Mikko does not see Reject-button
  [Tags]  attachments
  Element should not be visible  test-attachment-reject

Mikko does not see Approve-button
  [Tags]  attachments
  Element should not be visible  test-attachment-approve

Mikko deletes attachment
  [Tags]  attachments
  Click enabled by test id  delete-attachment
  Confirm  dialog-confirm-delete-attachment
  Wait Until Page Contains  753-416-25-30
  Wait Until  Page Should Not Contain  xpath=//a[@data-test-type="muut.muu"]

Mikko adds txt attachment with comment
  [Tags]  attachments
  Add attachment  ${TXT_TESTFILE_PATH}  ${TXT_TESTFILE_DESCRIPTION}

Mikko opens application to authorities
  Open to authorities  pliip
  Wait Until  Application state should be  open

Mikko see that attachment is for authority
  [Tags]  attachments
  Wait Until  Attachment state should be  muut.muu  requires_authority_action

Mikko adds comment
  [Tags]  attachments
  Open attachment details  muut.muu
  Input comment  attachment  mahtava liite!

Comment is added
  [Tags]  attachments
  Wait Until  Comment count is  2

Change attachment type
  [Tags]  attachments
  Select From List  attachment-type-select  rakennuspaikka.ote_alueen_peruskartasta
  Wait Until  Element Should Not Be Visible  attachment-type-select-loader
  Click element  xpath=//a[@data-test-id="back-to-application-from-attachment"]
  Tab should be visible  attachments
  Wait Until  Page Should Not Contain  xpath=//a[@data-test-type="muut.muu"]

Switch user
  [Tags]  attachments
  Logout
  Sonja logs in

Sonja goes to conversation tab
  [Tags]  attachments
  Open application  ${appname}  753-416-25-30
  Open tab  conversation
  Click Element  link=Ote alueen peruskartasta
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}

Sonja goes to attachments tab
  [Tags]  attachments
  Click element  xpath=//a[@data-test-id="back-to-application-from-attachment"]
  Open tab  attachments

Sonja see that attachment is for authority
  [Tags]  attachments
  Wait Until  Attachment state should be  rakennuspaikka.ote_alueen_peruskartasta  requires_authority_action

Sonja opens attachment details
  [Tags]  attachments
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta

Sonja sees Reject-button which is enabled
  [Tags]  attachments
  Wait Until  Element should be visible  test-attachment-reject
  Element should be enabled  test-attachment-reject

Sonja sees Approve-button which is enabled
  [Tags]  attachments
  Wait until  Element should be visible  test-attachment-approve
  Element should be enabled  test-attachment-approve

Sonja rejects attachment
  [Tags]  attachments
  Element should be enabled  test-attachment-reject
  Click element  test-attachment-reject

Reject-button should be disabled
  [Tags]  attachments
  Wait until  Element should be disabled  test-attachment-reject

Sonja approves attachment
  [Tags]  attachments
  Wait until  Element should be enabled  test-attachment-approve
  Click element  test-attachment-approve

Approve-button should be disabled
  [Tags]  attachments
  Wait until  Element should be disabled  test-attachment-approve

*** Keywords ***

Open attachment details
  [Arguments]  ${type}
  Open tab  attachments
  Wait Until  Page Should Contain Element  xpath=//a[@data-test-type="${type}"]
  # Make sure the element is visible on browser view before clicking. Take header heigth into account.
  #Execute Javascript  window.scrollTo(0, $("[data-test-type='muut.muu']").position().top - 130);
  Focus  xpath=//a[@data-test-type="${type}"]
  Click element  xpath=//a[@data-test-type="${type}"]
  Wait Until  Element Should Be Visible  test-attachment-file-name
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}
  Element Text Should Be  test-attachment-file-name  ${TXT_TESTFILE_NAME}
  Element Text Should Be  test-attachment-version  1.0

Attachment state should be
  [Arguments]  ${type}  ${state}
  ## Fragile: assumes there is only one element that has data-test-state
  ${STATE_ATTR_VALUE} =  Get Element Attribute  xpath=//*[@data-test-state and @data-test-type="${type}"]@data-test-state
  Log  ${STATE_ATTR_VALUE}
  Should Be Equal  ${STATE_ATTR_VALUE}  ${state}

Comment count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='attachment']//div[@data-bind='foreach: comments().slice(0).reverse()']/div  ${amount}
