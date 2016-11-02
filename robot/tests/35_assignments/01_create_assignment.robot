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

Create assignment button is visible in parties tab
  Element should not be visible  xpath=//button[@data-test-id="create-assignment-editor"]
  Open tab  parties
  Wait until  Element should be visible  xpath=//button[@data-test-id='create-assignment-editor']

Sonja creates assignment for Ronja about paasuunnittelija
  Create assignment  Ronja Sibbo  parties  paasuunnittelija  Katoppa tää

Sonja sees assignment in document
  Set Suite Variable  ${docPath}  section[@class='accordion' and @data-doc-type='paasuunnittelija']
  Wait until  Element should be visible  xpath=//${docPath}//accordion-assignments
  Wait until  Element should be visible  xpath=//${docPath}//button[@data-test-id='mark-assignment-complete-paasuunnittelija']
  Wait until  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Sibbo Sonja
  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Katoppa tää
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
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Element should be visible  xpath=//${docPath}//accordion-assignments
  Wait until  Element should be visible  xpath=//${docPath}//button[@data-test-id='mark-assignment-complete-paasuunnittelija']

Only one assignment has been created
  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  1


