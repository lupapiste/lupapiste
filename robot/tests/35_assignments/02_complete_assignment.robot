*** Settings ***

Documentation   Authority completes assignment
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

Sonja creates assignment for Ronja about paasuunnittelija
  As Sonja
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info
  Open tab  parties
  Wait until  Element should be visible  xpath=//button[@data-test-id='create-assignment-editor-button']
  Create assignment  Ronja Sibbo  parties  paasuunnittelija  Katoppa tää

Sonja creates another assignment for herself
  Create assignment  Sonja Sibbo  parties  paasuunnittelija  Muista tarkistaa!  2
  Go to page  applications
  Count of open assignments is  1
  Logout

Ronja logs in, sees two assignments in document
  Set Suite Variable  ${doctype}  paasuunnittelija
  Set Suite Variable  ${docPath}  section[@class='accordion' and @data-doc-type='paasuunnittelija']
  Ronja logs in
  Count of open assignments is  1
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Element should be visible  xpath=//${docPath}//accordion-assignments
  Xpath Should Match X Times  //${docPath}//div[@data-test-id='accordion-assignment']  2

Ronja completes her assignment
  ${target}=  Set Variable  section[@data-doc-type='paasuunnittelija']//div[@data-test-id='accordion-assignment' and //div[@data-test-id='assignment-text' and contains(., 'Katoppa')]]
  #Hide nav-bar
  Scroll to  section[data-doc-type=paasuunnittelija]
  Click element  xpath=//${target}//button[@data-test-id='mark-assignment-complete']
  Positive indicator should be visible
  Wait until  Xpath Should Match X Times  //${docPath}//div[@data-test-id='accordion-assignment']  1

Ronja can also complete Sonja's assignment
  Element should contain  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//div[@data-test-id='assignment-text']  Muista tarkistaa!
  Click element  xpath=//${docPath}//div[@data-test-id='accordion-assignment'][1]//button[@data-test-id='mark-assignment-complete']
  Positive indicator should be visible

No assignments visible in paasuunnittelija doc nor parties tab
  Wait until  Xpath Should Match X Times  //${docPath}//div[@data-test-id='accordion-assignment']  0
  Wait until  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  0
  Go to page  applications
  Count of open assignments is  0
  Logout

No assignments for Sonja either
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait until  Xpath Should Match X Times  //div[@data-test-id='accordion-assignment']  0
  Go to page  applications
  Count of open assignments is  0
  Logout
