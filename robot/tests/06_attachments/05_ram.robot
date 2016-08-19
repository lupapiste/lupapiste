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
  Create application with state   ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted

Mikko adds txt attachment without comment
  Open tab  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  submitted
  Wait Until  Element should be visible  xpath=//div[@id='application-attachments-tab']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko opens attachment details
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Title Should Be  ${appname} - Lupapiste

Add RAM button not visible in the draft state
  No such test id  add-ram-attachment
  No such test id  ram-links-table
  [Teardown]  Logout

Sonja logs in and gives verdict
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Submit empty verdict

Sonja opens the attachment
  Open tab  attachments
  Open attachment details  muut.muu
  No such test id  ram-links-table

Sonja adds new RAM attachment
  Click by test id  add-ram-attachment
  Wait test id visible  ram-links-table
  Wait test id visible  ram-prefix
  Delete allowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -

Sonja adds file to RAM
  Add attachment version  ${PNG_TESTFILE_PATH}
  Delete allowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  -

Sonja can now approve the attachment
  Wait Until  Click button  id=test-attachment-approve
  Delete disallowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo

Sonja clicks RAM link and opens old attachment details
  Click link  jquery=td[data-test-id=ram-link-type-0] a
  Wait test id visible  ram-links-table
  No such test id  ram-prefix
  Delete allowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Element should not be visible  jquery=td[data-test-id=ram-link-type-0] a
  Element should be visible  jquery=td[data-test-id=ram-link-type-1] a
  [Teardown]  Logout

Mikko logs in and can see the RAM links but cannot delete the attachment
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  muut.muu  1
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo
  Wait test id visible  ram-prefix
  Delete disallowed  True
  Wait test id visible  add-ram-attachment

Mikko adds new file version and thus resetting approval state
  Add attachment version  ${PNG_TESTFILE_PATH}
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete allowed  True
  No such test id  add-ram-attachment

Mikko follows RAM link and cannot delete the base attachment either
  Click link  jquery=td[data-test-id=ram-link-type-0] a
  Wait test id visible  ram-links-table
  No such test id  ram-prefix
  No such test id  add-ram-attachment
  Delete disallowed  True
  [Teardown]  Logout

Sonja logs in and could delete base attachment
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  muut.muu  1
  Wait test id visible  ram-link-type-0
  Click link  jquery=td[data-test-id=ram-link-type-0] a
  No such test id  ram-prefix
  Delete allowed  True

Sonja approves RAM (again)
  Wait Until  Click link  jquery=td[data-test-id=ram-link-type-1] a
  Wait Until  Click button  id=test-attachment-approve
  Delete disallowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  [Teardown]  Logout

Mikko logs in and opens RAM
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  muut.muu  1
  Wait test id visible  ram-prefix

Mikko adds new RAM attachment and uploads file
  Click by test id  add-ram-attachment
  Wait test id visible  ram-links-table
  Wait test id visible  ram-link-type-2
  Add attachment version  ${PNG_TESTFILE_PATH}
  Delete allowed  True
  Check link row  2  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -

Mikko deletes attachment version
  # Versions are visible after Delete allowed (see above)
  Click by test id  delete-version
  Confirm yes no dialog
  Wait until  Element should not be visible  show-attachment-versions

RAM links table has been updated after delete
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Element should not be visible  jquery=td[data-test-id=ram-link-file-2] a

Mikko deletes RAM
  Click by test id  delete-attachment
  Confirm yes no dialog
  Open attachment details  muut.muu  0
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  No such test id  ram-link-type-2
  [Teardown]  Logout

Sonja logs in and deletes base attachment
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Open attachment details  muut.muu  1
  Click link  jquery=[data-test-id=ram-link-type-0] a
  Click by test id  delete-attachment
  Confirm yes no dialog

Sonja opens the attachment and checks that the original is gone
  Open attachment details  muut.muu
  Check link row  0  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  No such test id  ram-link-type-1
  [Teardown]  Logout

*** Keywords ***

Check link row
  [Arguments]  ${index}  ${type}  ${filename}  ${editor}  ${approver}
  Wait until  Element Should Contain  jquery=td[data-test-id=ram-link-type-${index}]  ${type}
  Wait until  Element Should Contain  jquery=td[data-test-id=ram-link-file-${index}]  ${filename}
  Wait until  Element Should Contain  jquery=td[data-test-id=ram-link-editor-${index}]  ${editor}
  Wait until  Element Should Contain  jquery=td[data-test-id=ram-link-approver-${index}]  ${approver}

Delete disallowed
  [Arguments]  ${click}=False
  Run keyword if  ${click}  Wait Until  Click button  id=show-attachment-versions
  No such test id  delete-attachment
  Run keyword if  ${click}  No such test id  delete-version

Delete allowed
  [Arguments]  ${click}=False
  Run keyword if  ${click}  Wait Until  Click button  id=show-attachment-versions
  Wait test id visible  delete-attachment
  Run keyword if  ${click}  Wait test id visible  delete-version
