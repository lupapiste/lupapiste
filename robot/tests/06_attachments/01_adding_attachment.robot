*** Settings ***

Documentation  Mikko adds an attachment to application
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
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

Mikko sees all "not needed" checkboxes as enabled and not selected
  [Tags]  attachments
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Xpath Should Match X Times  //div[@id='application-attachments-tab']//label[@data-test-id='not-needed-label']  4
  Not needed should not be selected  hakija.valtakirja
  Not needed should not be selected  paapiirustus.asemapiirros
  Not needed should not be selected  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Not needed should not be selected  paapiirustus.pohjapiirustus

Download all buttons are not visible
  No such test id  download-all
  No such test id  download-all-attachments-button

Mikko sets asemapiirros not needed
  [Tags]  attachments
  Click not needed  paapiirustus.asemapiirros
  Not needed should be selected  paapiirustus.asemapiirros

Mikko unsets asemapiirros not needed
  [Tags]  attachments
  Click not needed  paapiirustus.asemapiirros
  Not needed should not be selected  paapiirustus.asemapiirros

As an applicant Mikko does not see approve or reject columns
  [Tags]  attachments
  No such test id  approve-column
  No such test id  reject-column

Dropdown options for attachment actions should look correct for Mikko
  [Tags]  attachments
  # TODO: Check that correct action buttons are visible
  :FOR  ${cmdButton}  IN  stamp-attachments  order-attachment-prints  mark-verdict-attachments
  \  Element should not be visible  xpath=//div[@id='application-attachments-tab']//button[@data-test-id='${cmdButton}']

Mikko adds png attachment without comment
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  First  Asuinkerrostalon tai rivitalon rakentaminen

Download all buttons are now visible
  Wait test id visible  download-all
  Wait test id visible  download-all-attachments-button

Mikko deletes attachment immediately by using remove icon
  [Tags]  attachments
  Wait Until  Delete attachment  muut.muu
  Wait Until  Element should not be visible  xpath=//div[@class='attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Download all buttons are gone again
  No such test id  download-all
  No such test id  download-all-attachments-button

Mikko adds png attachment without comment again
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Second  Asuinkerrostalon tai rivitalon rakentaminen

Mikko opens attachment details
  Open attachment details  muut.muu

Mikko deletes attachment version
  [Tags]  attachments
  Delete attachment version  1.0

Mikko deletes also the attachment template
  [Tags]  attachments
  Return to application
  Wait Until  Delete attachment  muut.muu
  Wait Until  Element should not be visible  xpath=//div[@class='attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko adds png attachment one more time
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Third  Asuinkerrostalon tai rivitalon rakentaminen

Download all buttons are again visible
  Wait test id visible  download-all
  Wait test id visible  download-all-attachments-button

Mikko opens attachment details again
  [Tags]  attachments
  Open attachment details  muut.muu

# Tests against bug fix LPK-1042
Mikko returns to application right away
  [Tags]  attachments
  Wait until  Element should be visible  //a[@data-test-id='back-to-application-from-attachment']
  Return to application
  Wait Until Page Contains  ${propertyId}

Scroll to muut muu
  [Tags]  attachments
  Scroll to  tr[data-test-type='muut.muu'] button[data-test-icon=delete-button]

Attachment not-needed checkbox should not be visible
  [Tags]  attachments
  Not needed should not be visible  muut.muu

Mikko checks Not needed for the attachment
  [Tags]  attachments
  Click not needed  paapiirustus.asemapiirros

Not needed should be checked after reload with correct filters
  [Tags]  attachments
  Reload page and kill dev-box
  Wait Until  Not needed should be visible  hakija.valtakirja
  Not needed should not be visible  paapiirustus.asemapiirros
  Scroll to  div.filter-wrapper:last-child label.filter-label
  Click by test id  notNeeded-filter-label
  Wait until  Not needed should be visible  paapiirustus.asemapiirros
  Not needed should be selected  paapiirustus.asemapiirros
  Click by test id  notNeeded-filter-label
  Wait until  Not needed should not be visible  paapiirustus.asemapiirros

Mikko opens muut.muu attachment details again
  [Tags]  attachments
  Open attachment details  muut.muu

Mikko does not see Reject nor Approve buttons
  [Tags]  attachments
  Element should not be visible  test-attachment-reject
  Element should not be visible  test-attachment-approve

Not needed not visible as attachment contains file
  [Tags]  attachments
  Wait until  Element should not be visible  xpath=//section[@id='attachment']//input[@data-test-id='is-not-needed']

Remove version, not needed selectable
  [Tags]  attachments
  Delete attachment version  1.0
  Wait until  Element should be visible  xpath=//section[@id='attachment']//input[@data-test-id='is-not-needed']
  Checkbox should not be selected  xpath=//section[@id='attachment']//input[@data-test-id='is-not-needed']
  Wait until  Element should be visible  xpath=//section[@id='attachment']//label[@data-test-id='upload-button-label']

Checking not needed in attachment page affects attachment listing
  [Tags]  attachments
  Select checkbox  xpath=//section[@id='attachment']//input[@data-test-id='is-not-needed']
  Wait until  Element should not be visible  xpath=//section[@id='attachment']//label[@data-test-id='upload-button-label']
  Return to application
  Wait until  Not needed should be visible  muut.muu
  Not needed should be selected  muut.muu

Upload is not possible for notNeeded attachment
  [Tags]  attachments
  Element should not be visible  xpath=//div[@id='application-attachments-tab']//tr[@data-test-type='muut.muu']//a[@data-test-id='add-attachment-file']

Set attachment back to needed
  [Tags]  attachments
  Click not needed  muut.muu
  Wait until  Not needed should not be selected  muut.muu
  Wait until  Element should be visible  xpath=//div[@id='application-attachments-tab']//tr[@data-test-type='muut.muu']//a[@data-test-id='add-attachment-file']

Upload new version for muut.muu (attachment page opens)
  [Tags]  attachments
  Add attachment file  tr[data-test-type='muut.muu']  ${PNG_TESTFILE_PATH}
  # Not needed not visible when file is present
  Element should not be visible  xpath=//section[@id='attachment']//input[@data-test-id='is-not-needed']

Mikko deletes attachment
  [Tags]  attachments
  Click enabled by test id  delete-attachment
  Confirm yes no dialog
  Wait Until Page Contains  ${propertyId}
  Wait Until  Page Should Not Contain  jquery=tr[data-test-type='muut.muu'] a[data-test-id='open-attachment']

# Comment is present after delete
#   [Tags]  attachments
#   Open side panel  conversation
#   Wait until  Xpath Should Match X Times  //div[@id='conversation-panel']//div[contains(@class, 'is-comment')]//span[@class='deleted']  1
#   Close side panel  conversation

Mikko adds png attachment one more time again
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  ${PNG_TESTFILE_DESCRIPTION}  Asuinkerrostalon tai rivitalon rakentaminen

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
  Select from list  attachment-type-select  rakennuspaikka.ote_alueen_peruskartasta
  Wait Until  Element Should Not Be Visible  attachment-type-select-loader
  Click enabled by test id  confirm-yes
  Wait until  Element should be visible  jquery=a[data-test-id=back-to-application-from-attachment]
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Wait Until  Tab should be visible  attachments
  Page Should Not Contain  jquery=tr[data-test-type='muut.muu'] a[data-test-id='open-attachment']

Signature icon is not visible
  [Tags]  attachments
  Wait Until  Attachment indicator icon should not be visible  signed  rakennuspaikka.ote_alueen_peruskartasta

Mikko adds another attachment and signs it as single attachment
  [Tags]  attachments
  Upload attachment  ${PDF_TESTFILE_PATH}  Valtakirja  Valtakirja  Asuinkerrostalon tai rivitalon rakentaminen
  Open attachment details  hakija.valtakirja
  Click enabled by test id  signLatestAttachmentVersion
  Wait Until   Element should be visible  signSingleAttachmentPassword
  Input text by test id  signSingleAttachmentPassword  mikko123
  Click enabled by test id  do-sign-attachment
  Wait Until   Element should not be visible  signSingleAttachmentPassword

One signature is visible in attachment page
  [Tags]  attachments
  Wait Until  Xpath Should Match X Times  //section[@id="attachment"]//*/div[@data-test-id="attachment-signature-fullname"]  1
  Element text should be  xpath=//section[@id="attachment"]//*/div[@data-test-id="attachment-signature-fullname"]  Intonen Mikko
  Element text should be  xpath=//section[@id="attachment"]//*/span[@data-test-id="attachment-signature-version"]  1.0
  Element should be visible  xpath=//section[@id="attachment"]//*/div[@data-test-id="attachment-signature-date"]
  Return to application

Signature icon is visible in attachments tab, and only one
  [Tags]  attachments
  Wait Until  Attachment indicator icon should be visible  signed  hakija.valtakirja
  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//table[contains(@class,'attachments-table')]//tr//td//i[@data-test-icon='signed-icon']  1

Mikko signs all attachments
  [Tags]  attachments
  Sign all attachments  mikko123

Signature icon is visible
  [Tags]  attachments
  Wait Until  Attachment indicator icon should be visible  signed  hakija.valtakirja
  Wait Until  Attachment indicator icon should be visible  signed  rakennuspaikka.ote_alueen_peruskartasta
  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//table[contains(@class,'attachments-table')]//tr//td//i[@data-test-icon='signed-icon']  2

Signature is visible
  [Tags]  attachments
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Wait Until  Xpath Should Match X Times  //section[@id="attachment"]//*/div[@data-test-id="attachment-signature-fullname"]  1
  Element text should be  xpath=//section[@id="attachment"]//*/div[@data-test-id="attachment-signature-fullname"]  Intonen Mikko
  Element text should be  xpath=//section[@id="attachment"]//*/span[@data-test-id="attachment-signature-version"]  1.0
  Element should be visible  xpath=//section[@id="attachment"]//*/div[@data-test-id="attachment-signature-date"]
  Return to application

Two signatures are visible
  [Tags]  attachments
  Wait until  Tab should be visible  attachments
  Open attachment details  hakija.valtakirja
  Wait Until  Xpath Should Match X Times  //section[@id="attachment"]//*/div[@data-test-id="attachment-signature-fullname"]  2

Switch to authority
  [Tags]  attachments
  Logout
  Sonja logs in

Sonja goes to conversation tab
  [Tags]  attachments
  Open application  ${appname}  ${propertyId}
  Open side panel  conversation
  Click Element  link=Ote alueen peruskartasta
  Wait Until  Element text should be  jquery=section#attachment span#test-attachment-file-name a  ${PNG_TESTFILE_NAME}
  Close side panel  conversation

Sonja goes to attachments tab
  [Tags]  attachments
  Wait Until  Element should be visible  jquery=a[data-test-id='back-to-application-from-attachment']
  Scroll to test id  back-to-application-from-attachment
  Return to application
  Open tab  attachments

As an authority Sonja sees approve and reject columns
  [Tags]  attachments
  Wait test id visible  approve-column
  Wait test id visible  reject-column

Sonja adds new attachment template
  [Tags]  attachments
  Add empty attachment template  Muu pääpiirustus  paapiirustus  muu_paapiirustus

Sonja sees that new attachment template is visible in attachments list
  [Tags]  attachments
  Wait Until  Element Should Be Visible  jquery=tr[data-test-type='paapiirustus.muu_paapiirustus'] a[data-test-id=add-attachment-file]
  Logout

Mikko logs back in and browses to the Attachments tab
  [Tags]  attachments
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

For the added attachment template added by Sonja, Mikko sees the "not needed" checkbox missing
  [Tags]  attachments
  Not needed should not be visible  paapiirustus.muu_paapiirustus
  Not needed should not be selected  paapiirustus.muu_paapiirustus
  [Teardown]  Logout

Sonja logs back in and browses to the Attachments tab
  [Tags]  attachments
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Sonja deletes the newly created attachment template
  [Tags]  attachments
  Wait Until  Delete attachment  paapiirustus.muu_paapiirustus
  Wait Until  Element should not be visible  jquery=tr[data-test-type='paapiirustus.muu_paapiirustus'] a[data-test-id=add-attachment-file]

Sonja continues with Mikko's attachment. She sees that attachment is for authority
  [Tags]  attachments
  Wait Until  Attachment indicator icon should be visible  state  rakennuspaikka.ote_alueen_peruskartasta

Sonja opens attachment details
  [Tags]  attachments
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta

Approver info is not visible
  [Tags]  attachments
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
  [Tags]  attachments
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

Sign attachments button should not be visible
  [Tags]  attachments
  No such test id  sign-attachments

Sonja adds an attachment for Mikko to sign (LPK-517)
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Fourth  Asuinkerrostalon tai rivitalon rakentaminen

Create new application
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname2}  authAtt${secs}
  Set Suite Variable  ${propertyId2}  753-416-6-12
  Create application the fast way  ${appname2}  ${propertyId2}  kerrostalo-rivitalo
  Open tab  attachments

Authority adds png attachment
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Fifth  Asuinkerrostalon tai rivitalon rakentaminen

Signature icon is not visible to authority
  [Tags]  attachments
  Wait Until  Attachment indicator icon should not be visible  signed  muut.muu

Authority signs the attachment
  [Tags]  attachments
  Sign all attachments  sonja

Signature icon is visible to authority
  [Tags]  attachments
  Wait Until  Attachment indicator icon should be visible  signed  muut.muu
  Logout

Mikko signs the final attachment
  [Tags]  attachments
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Mikko signs everything blindly
  [Tags]  attachments
  Attachment indicator icon should not be visible  signed  muut.muu
  Sign all attachments  mikko123
  Wait Until  Attachment indicator icon should be visible  signed  muut.muu

No frontend errors
  [Tags]  non-roboto-proof
  There are no frontend errors


*** Keywords ***

Attachment state should be
  [Arguments]  ${type}  ${state}
  ## Fragile: assumes there is only one element that has data-test-state
  ${STATE_ATTR_VALUE} =  Get Element Attribute  xpath=//tr[@data-test-state and @data-test-type="${type}"]@data-test-state
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

Not needed matches
  # Helper for matching not needed properties
  [Arguments]  ${type}  ${property}  ${times}
  ${selector} =  Set Variable  table.attachments-table tr[data-test-type='${type}'] input[data-test-id=not-needed-checkbox]
  Javascript?  $("${selector}:${property}").length === ${times}

Not needed should be selected
  [Arguments]  ${type}
  Not needed matches  ${type}  checked  1

Not needed should not be selected
  [Arguments]  ${type}
  Not needed matches  ${type}  checked  0

Not needed should be visible
  [Arguments]  ${type}
  Not needed matches  ${type}  visible  1

Not needed should not be visible
  [Arguments]  ${type}
  Not needed matches  ${type}  visible  0

Not needed should be disabled
  [Arguments]  ${type}
  Not needed matches  ${type}  disabled  1
