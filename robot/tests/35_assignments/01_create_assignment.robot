*** Settings ***

Documentation   Authority creates assignment
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Do
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo
  Submit application
  Logout

Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info

Create assignment button is visible in both info and parties tab
  Element should be visible  xpath=//div[@id='application-info-tab']//button[@data-test-id="create-assignment-editor-button"]
  Open tab  parties
  Wait until  Element should be visible  xpath=//div[@id='application-parties-tab']//button[@data-test-id='create-assignment-editor-button']

Sonja creates assignment for Ronja about paasuunnittelija
  Create assignment  Ronja Sibbo  parties  paasuunnittelija  Katoppa tää

Sonja sees assignment in document
  Set Suite Variable  ${docPath}  section[@class='accordion' and @data-doc-type='paasuunnittelija']
  Wait until  Element should be visible  xpath=//${docPath}//accordion-assignments
  Wait until  Element should be visible  xpath=//${docPath}//button[@data-test-id='mark-assignment-complete']
  Wait until  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-header']//span[@class='creator']  Sonja Sibbo
  Wait until  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-header']//span[@class='receiver']  Ronja Sibbo
  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Katoppa tää

Sonja edits description of the assignment
  Click by test id  edit-assignment
  Wait until  Element should be visible  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  ${description}=  Get Value  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']//textarea[@id='assignment-description']
  Should be equal  ${description}  Katoppa tää
  Input text  xpath=//bubble-dialog[@data-test-id='edit-assignment-bubble']//textarea[@id='assignment-description']  Katsoisitko?
  Scroll and click test id  bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  Wait until  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Katsoisitko?

Sonja has no open assignments
  Go to page  applications
  Count of open assignments is  0
  Logout

Pena does not see assignment
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Element should be visible  xpath=//${docPath}
  Element should not be visible  xpath=//${docPath}//accordion-assignments
  Logout

Luukas does not see assignment
  Luukas logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Element should be visible  xpath=//${docPath}
  Element should not be visible  xpath=//${docPath}//accordion-assignments
  Logout

Ronja logs in, sees assignment in document
  Ronja logs in
  Count of open assignments is  1
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Element should be visible  xpath=//${docPath}//accordion-assignments
  Wait until  Element should be visible  xpath=//${docPath}//button[@data-test-id='mark-assignment-complete']
  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Katsoisitko?

Ronja changes assignment back to Sonja
  Click by test id  edit-assignment
  Wait until  Element should be visible  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  List selection should be  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']//select[@id='assignment-recipient']  Ronja Sibbo
  Select from list by label  xpath=//${docPath}//select[@id='assignment-recipient']  Sonja Sibbo
  Scroll and click test id  bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//${docPath}//bubble-dialog[@data-test-id='edit-assignment-bubble']
  Wait until  Element text should be  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-header']/span[@class='receiver']  Sonja Sibbo


Only one assignment has been created
  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  1

No frontend errors
  [Tags]  non-roboto-proof
  Logout
  There are no frontend errors
