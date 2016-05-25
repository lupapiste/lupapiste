*** Settings ***

Documentation  Rakentamisen aikaiset muutokset (RAM)
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Duram Duram${secs}
  Mikko logs in
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

Mikko adds txt attachment without comment
  [Tags]  attachments
  Open tab  attachments
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu
  Assert file latest version  ${TXT_TESTFILE_NAME}  1.0
  Title Should Be  ${appname} - Lupapiste

Add RAM button not visible in the draft state
  No such test id  add-ram-attachment
  No such test id  ram-links-table
  [Teardown]  Go back

Mikko submits application
  Submit application
  [Teardown]  Logout

Sonja logs in and gives verdict
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Submit empty verdict

Sonja opens the attachment and can add new RAM attachment
  Open tab  attachments
  Open attachment details  muut.muu
  No such test id  ram-links-table
  Scroll and click test id  add-ram-attachment
  Wait test id visible  ram-links-table
  Wait test id visible  ram-link-filename-1

Sonja adds file to the attachment
  Add attachment version  ${PNG_TESTFILE_PATH}
  Check link row  1  ${PNG_TESTFILE_NAME}  0.1  Sibbo Sonja
  Wait test id visible  ram-link-link-0
  No such test id  ram-link-link-1

Sonja clicks RAM link and opens old attachment details
  Click link  jquery=[data-test-id=ram-link-link-0]
  Wait Until  Check link row  0  ${TXT_TESTFILE_NAME}  1.0  Intonen Mikko
  Wait Until  No such test id  ram-link-link-0
  Wait test id visible  ram-link-link-1
  [Teardown]  Logout

Mikko logs in and can see the RAM links
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  muut.muu
  Check link row  1  ${PNG_TESTFILE_NAME}  0.1  Sibbo Sonja  
  Check link row  0  ${TXT_TESTFILE_NAME}  1.0  Intonen Mikko
  No such test id  ram-link-link-1
  Wait test id visible  ram-link-link-0
  Wait test id visible  add-ram-attachment
  
Mikko deletes attachment version
  Wait and click  show-attachment-versions
  Wait and click  //tr[@data-test-id='version-row-0.1']//a[@data-test-id='delete-version']
  Confirm yes no dialog
  Wait until  Element should not be visible  show-attachment-versions

RAM links table has been updated after delete
  Check link row  1  -  ${EMPTY}  ${EMPTY}
  Check link row  0  ${TXT_TESTFILE_NAME}  1.0  Intonen Mikko
  No such test id  ram-link-link-1
  Wait test id visible  ram-link-link-0
  

*** Keywords ***
Check link row
  [Arguments]  ${index}  ${filename}  ${version}  ${name}
  Wait until element contains  jquery=td[data-test-id=ram-link-filename-${index}]  ${filename}
  Wait until element contains  jquery=td[data-test-id=ram-link-version-${index}]  ${version}
  Wait until element contains  jquery=td[data-test-id=ram-link-name-${index}]  ${name}  
  

