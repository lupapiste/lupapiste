*** Settings ***

Documentation  Mikko adds an attachment to application
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko goes to empty attachments tab
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments

"Download all attachments" should not be visible in the attachment actions dropdown
  [Tags]  attachments
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='downloadAll']

Dropdown options for attachment actions should look correct for Mikko
  [Tags]  attachments
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='newAttachmentTemplates']
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='attachmentsMoveToBackingSystem']

Mikko adds txt attachment without comment
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko deletes attachment immediately by using remove icon
  [Tags]  attachments
  Wait Until  Delete Muu liite
  Wait Until  Element should not be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko adds txt attachment without comment again
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko deletes attachment version
  [Tags]  attachments
  Click element  xpath=//div[@id="application-attachments-tab"]//tr[@id='attachment-row-muut-muu']//td[contains(@class, 'attachment-type-id')]/a
  Wait and click  show-attachment-versions
  Wait and click  //tr[@data-test-id='version-row-1.0']//a[@data-test-id='delete-version']
  Confirm yes no dialog
  Wait until  Element should not be visible  show-attachment-versions

Mikko deletes also the attachment template
  Click by test id  back-to-application-from-attachment
  Wait Until  Delete Muu liite
  Wait Until  Element should not be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko adds again txt attachment with comment
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  Poistetun liitteen kommentti  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]
  Comment count is  1

"Download all attachments" should be visible in the attachment actions dropdown
  [Tags]  attachments
  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='downloadAll']

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu

# Tests against bug fix LPK-1042
Mikko returns to application right away
  [Tags]  attachments
  Wait until  Element should be visible  //a[@data-test-id='back-to-application-from-attachment']
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Wait Until Page Contains  ${propertyId}

Attachment is needed
  [Tags]  attachments
  Scroll to  td.attachment-not-needed input
  Checkbox Should Not Be Selected  jquery=td.attachment-not-needed input

Mikko checks Not needed for the attachment
  [Tags]  attachments
  Scroll to  td.attachment-not-needed input
  Wait until  Select checkbox  jquery=td.attachment-not-needed input
  Sleep  0.5s
  Wait for jQuery

Not needed should be checked after reload
  [Tags]  attachments
  Reload Page
  Wait Until Page Contains  ${propertyId}
  Scroll to  td.attachment-not-needed input
  Wait Until  Element should be visible  jquery=td.attachment-not-needed input
  Checkbox Should Be Selected  jquery=td.attachment-not-needed input

Mikko opens attachment details again
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
  Confirm yes no dialog
  Wait Until Page Contains  ${propertyId}
  Wait Until  Page Should Not Contain  xpath=//a[@data-test-type="muut.muu"]

Comment is present after delete
  [Tags]  attachments
  Open side panel  conversation
  Wait until  Xpath Should Match X Times  //div[@id='conversation-panel']//div[contains(@class, 'is-comment')]//span[@class='deleted']  1
  Close side panel  conversation

Mikko adds txt attachment with comment
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${PNG_TESTFILE_DESCRIPTION}  operation=Asuinkerrostalon tai rivitalon rakentaminen

Mikko opens application to authorities
  [Tags]  attachments
  Open to authorities  pliip
  Wait Until  Application state should be  open

Mikko see that attachment is for authority
  [Tags]  attachments
  Wait Until  Attachment state should be  muut.muu  requires_authority_action

Mikko adds comment
  [Tags]  attachments
  Open attachment details  muut.muu
  Input comment  mahtava liite!

Comment is added
  [Tags]  attachments
  Wait Until  Comment count is  2

Change attachment type
  [Tags]  attachments
  Click enabled by test id  change-attachment-type
  Select From List  attachment-type-select  rakennuspaikka.ote_alueen_peruskartasta
  Wait Until  Element Should Not Be Visible  attachment-type-select-loader
  Click enabled by test id  confirm-yes
  Wait until  Element should be visible  //a[@data-test-id='back-to-application-from-attachment']
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Wait Until  Tab should be visible  attachments
  Page Should Not Contain  xpath=//a[@data-test-type="muut.muu"]

Signature icon is not visible
  [Tags]  attachments
  Element should not be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-rakennuspaikka.ote_alueen_peruskartasta"]

Mikko signs all attachments
  [Tags]  attachments
  Sign all attachments  mikko123

Signature icon is visible
  [Tags]  attachments
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-rakennuspaikka.ote_alueen_peruskartasta"]

Signature is visible
  [Tags]  attachments
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Wait Until  Xpath Should Match X Times  //section[@id="attachment"]//*/div[@data-bind="fullName: user"]  1
  Element text should be  xpath=//section[@id="attachment"]//*/div[@data-bind="fullName: user"]  Intonen Mikko
  Element text should be  xpath=//section[@id="attachment"]//*/span[@data-bind="version: version"]  1.0
  Element should be visible  xpath=//section[@id="attachment"]//*/div[@data-bind="dateTimeString: created"]

Sign single attachment
  [Tags]  attachments
  Click enabled by test id  signLatestAttachmentVersion
  Wait Until   Element should be visible  signSingleAttachmentPassword
  Input text by test id  signSingleAttachmentPassword  mikko123
  Click enabled by test id  do-sign-attachment
  Wait Until   Element should not be visible  signSingleAttachmentPassword

Two signatures are visible
  [Tags]  attachments
  Wait Until  Xpath Should Match X Times  //section[@id="attachment"]//*/div[@data-bind="fullName: user"]  2

Switch to authority
  [Tags]  attachments
  Logout
  Sonja logs in

Sonja goes to conversation tab
  [Tags]  attachments
  Open application  ${appname}  ${propertyId}
  Open side panel  conversation
  Click Element  link=Ote alueen peruskartasta
  Wait Until  Element text should be  xpath=//section[@id="attachment"]//span[@id="test-attachment-file-name"]/a  ${PNG_TESTFILE_NAME}
  Close side panel  conversation

Sonja goes to attachments tab
  [Tags]  attachments
  Wait Until  Element should be visible  xpath=//a[@data-test-id="back-to-application-from-attachment"]
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Open tab  attachments

Sonja adds new attachment template
  [Tags]  attachments
  Add empty attachment template  Muu liite  muut  muu

Sonja sees that new attachment template is visible in attachments list
  [Tags]  attachments
  Wait Until Element Is Visible  xpath=//div[@id="application-attachments-tab"]//a[@data-test-type="muut.muu"]

Sonja deletes the newly created attachment template
  [Tags]  attachments
  Wait Until  Delete Muu liite
  Wait Until  Element should not be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[@data-test-type="muut.muu"]

Sonja continues with Mikko's attachment. She sees that attachment is for authority
  [Tags]  attachments
  Wait Until  Attachment state should be  rakennuspaikka.ote_alueen_peruskartasta  requires_authority_action

Sonja opens attachment details
  [Tags]  attachments
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta

Approver info is not visible
  Wait Until  Element Should Not Be Visible  jquery=#attachment .attachment-info span.form-approval-status

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

Approver info is visible
  Wait Until  Element Should Be Visible  jquery=#attachment .attachment-info span.form-approval-status
  Wait Until  Element Should Contain  jquery=#attachment .attachment-info .approved .is-details  Tiedot OK (Sibbo Sonja

Approve-button should be disabled
  [Tags]  attachments
  Wait until  Element should be disabled  test-attachment-approve

Attachment state should be ok
  [Tags]  attachments
  Wait until  Element should be visible  //a[@data-test-id='back-to-application-from-attachment']
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Tab should be visible  attachments
  Wait Until  Attachment state should be  rakennuspaikka.ote_alueen_peruskartasta  ok

"Sign attachments" should not be visible in the attachment actions dropdown
  [Tags]  attachments
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='signAttachments']

Sonja adds an attachment for Mikko to sign (LPK-517)
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Create new application
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname2}  authAtt${secs}
  Set Suite Variable  ${propertyId2}  753-416-6-12
  Create application the fast way  ${appname2}  ${propertyId2}  kerrostalo-rivitalo
  Open tab  attachments

Authority adds png attachment without comment
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Signature icon is not visible to authority
  [Tags]  attachments
  Element should not be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-muut.muu"]

Authority signs the attachment
  [Tags]  attachments
  Sign all attachments  sonja

Signature icon is visible to authority
  [Tags]  attachments
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-muut.muu"]
  Logout

Mikko signs the final attachment
  [Tags]  attachments
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Mikko signs everything blindly
  [Tags]  attachments
  Element should not be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-muut.muu"]
  Sign all attachments  mikko123
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//i[@data-test-icon="signed-muut.muu"]


*** Keywords ***

Attachment state should be
  [Arguments]  ${type}  ${state}
  ## Fragile: assumes there is only one element that has data-test-state
  ${STATE_ATTR_VALUE} =  Get Element Attribute  xpath=//*[@data-test-state and @data-test-type="${type}"]@data-test-state
  Log  ${STATE_ATTR_VALUE}
  Should Be Equal  ${STATE_ATTR_VALUE}  ${state}

Sign all attachments
  [Arguments]  ${password}
  Tab should be visible  attachments
  Click by test id  sign-attachments
  Wait Until   Element should be visible  signAttachmentPassword
  Input text by test id  signAttachmentPassword  ${password}
  Click enabled by test id  do-sign-attachments
  Wait Until   Element should not be visible  signAttachmentPassword
  Confirm  dynamic-ok-confirm-dialog

Delete Muu liite
  Scroll to  [data-test-icon='delete-muut.muu']
  Click element  xpath=//div[@id="application-attachments-tab"]//span[@data-test-icon="delete-muut.muu"]
  Confirm yes no dialog
