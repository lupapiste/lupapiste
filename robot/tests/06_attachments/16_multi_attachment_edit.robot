*** Settings ***

Documentation  Uploading and updating multiple files for attachments at once.
Suite setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py


*** Test Cases ***

Pena logs in and creates application
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Robottitesti
  Set Suite Variable  ${propertyId}  753-419-4-1604
  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments

Update multiple button and the update multiple attachments dialog are not visible
  No such test id  update-multiple-attachments-button
  No such test id  multi-upload-dialog
  No such test id  multi-upload-no-button
  No such test id  multi-upload-upload-button

Pena uploads a png attachment
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  First  Asuinkerrostalon tai rivitalon rakentaminen

Update multiple button is now visible, but the update multiple attachments dialog is not
  Wait test id visible  update-multiple-attachments-button
  No such test id  multi-upload-dialog
  No such test id  multi-upload-no-button
  No such test id  multi-upload-upload-button

Pena deletes the attachment and the upload button should not be visible once more
  [Tags]  attachments
  Wait Until  Delete attachment  muut.muu
  No such test id  update-multiple-attachments-button

Pena reuploads the attachment and opens update multiple attachments dialog
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  First  Asuinkerrostalon tai rivitalon rakentaminen
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Wait test id visible  multi-upload-dialog
  Wait test id visible  multi-upload-no-button
  Wait test id visible  multi-upload-upload-button

Pena closes the update multiple attachments dialog and opens it again
  Click by test id  multi-upload-no-button
  No such test id  multi-upload-dialog
  No such test id  multi-upload-no-button
  No such test id  multi-upload-upload-button
  Click by test id  update-multiple-attachments-button
  Wait test id visible  multi-upload-dialog
  Wait test id visible  multi-upload-no-button
  Wait test id visible  multi-upload-upload-button

Pena tries to upload a TXT file via the update multiple attachments dialog but closes the dialog after upload
  No such test id  multi-confirm-dialog
  Choose File  xpath=//input[@type='file']  ${TXT_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Wait test id visible  no-match-icon
  No such test id  multi-upload-dialog
  No such test id  multi-update-attachments-button
  Click by test id  multi-update-no-button

Pena opens a new dialog and tries to upload a PNG file
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Wait test id visible  multi-update-attachments-button
  Wait test id visible  success-icon
  Click by test id  multi-update-attachments-button
  Wait test id visible  multi-success-dialog
  Wait test id visible  multi-success-no-button
  Click by test id  multi-success-no-button

Pena opens a new dialog and tries to upload an XML file
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  # This was here, but not visible. The dialog contained error that XML file upload failed?
  # Wait test id visible  multi-confirm-dialog
  # Wait test id visible  multi-update-no-button
  # Wait test id visible  type-warning-icon
  # Click by test id  multi-update-no-button
  ## ADDED FIXES:
  Wait test id visible  multi-upload-dialog
  Wait test id visible  multi-upload-no-button
  Wait test id visible  warning-icon
  Click by test id  multi-upload-no-button

Pena creates a new attachment with a similar file, then tries to update both of them - no match
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  First  Asuinkerrostalon tai rivitalon rakentaminen
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Wait test id visible  no-match-icon
  Click by test id  multi-update-no-button

Pena creates a new attachment with a different file, then tries to update all of the attachments
  [Tags]  attachments
  Upload attachment  ${PDF_TESTFILE_PATH}  Muu liite  First  Asuinkerrostalon tai rivitalon rakentaminen
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${PDF_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${TXT_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Wait test id visible  multi-update-attachments-button
  Wait test id visible  success-icon
  Wait test id visible  warning-icon
  Wait test id visible  no-match-icon
  Click by test id  multi-update-attachments-button
  Wait test id visible  multi-success-dialog
  Wait test id visible  multi-success-no-button
  Click by test id  multi-success-no-button

Pena tries to upload two files with the same name at the same time, the fool
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Click by test id  multi-update-no-button
  Submit application
  Logout

Sonja logs in and tries to update the attachment files
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Wait test id visible  update-multiple-attachments-button
  Click by test id  update-multiple-attachments-button
  Choose File  xpath=//input[@type='file']  ${PNG_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${PDF_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${TXT_TESTFILE_PATH}
  Choose File  xpath=//input[@type='file']  ${XML_TESTFILE_PATH}
  Wait test id visible  multi-confirm-dialog
  Wait test id visible  multi-update-no-button
  Wait test id visible  multi-update-attachments-button
  Wait test id visible  success-icon
  Wait test id visible  warning-icon
  Wait test id visible  no-match-icon
  Click by test id  multi-update-attachments-button
  Wait test id visible  multi-success-dialog
  Wait test id visible  multi-success-no-button
  Click by test id  multi-success-no-button

Sonja approves the application
  Approve application no dialogs
  Logout

Pena logs in and tries to look for an attachment update button in vain
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  No such test id  update-multiple-attachments-button
