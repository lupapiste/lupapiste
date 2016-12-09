*** Settings ***

Documentation   Authority creates assignment for attachments
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Do
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application with state  ${appname}  ${propertyid}  pientalo  submitted
  Open tab  attachments
  Add attachment  application  ${TXT_TESTFILE_PATH}  oma piirustus  paapiirustus.asemapiirros
  Logout

Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info

Create assignment button is visible in attachments tab
  Open tab  attachments
  Wait until  Element should be visible  xpath=//div[@id='application-attachments-tab']//button[@data-test-id="create-assignment-editor-button"]

Sonja creates assignment for Ronja about paasuunnittelija
  Create attachment assignment  Ronja Sibbo  attachments  paapiirustus.asemapiirros  Onhan asemapiirros

Sonja sees assignment in attachment
  Set Suite Variable  ${attachmentRow}  tr[@data-test-type='paapiirustus.asemapiirros']
  Set Suite Variable  ${assignmentRow}  ${attachmentRow}/following-sibling::tr[@class='attachment-assignment-row']
  Wait until  Element should be visible  xpath=//${assignmentRow}//accordion-assignments
  Wait until  Element should be visible  xpath=//${assignmentRow}//button[@data-test-id='mark-assignment-complete']
  Wait until  Element should contain  xpath=//${assignmentRow}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-header']//span[@class='creator']  Sonja Sibbo
  Wait until  Element should contain  xpath=//${assignmentRow}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-header']//span[@class='receiver']  Ronja Sibbo
  Element should contain  xpath=//${assignmentRow}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Onhan asemapiirros

Sonja edits description of the assignment
  Click by test id  edit-assignment
  Wait until  Element should be visible  xpath=//${assignmentRow}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  ${description}=  Get Value  xpath=//${assignmentRow}//bubble-dialog[@data-test-id='edit-assignment-bubble']//textarea[@id='assignment-description']
  Should be equal  ${description}  Onhan asemapiirros
  Input text  xpath=//bubble-dialog[@data-test-id='edit-assignment-bubble']//textarea[@id='assignment-description']  Katsoisitko?
  Scroll and click test id  bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//${assignmentRow}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  Wait until  Element should contain  xpath=//${assignmentRow}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Katsoisitko?

Sonja logs out
  Logout

Pena does not see assignment
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should be visible  xpath=//${attachmentRow}
  Element should not be visible  xpath=//${assignmentRow}//accordion-assignments
  Logout

No frontend errors
  [Tags]  non-roboto-proof
  There are no frontend errors
