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

Mikko opens attachment details
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

Sonja opens the attachment tab and unselects post verdict filter
  Open attachments tab and unselect post verdict filter

There are no RAM filters
  No such test id  ram-filter-label
  
Sonja opens attachment details
  Open attachment details  muut.muu
  No such test id  ram-links-table

Sonja cannot add RAM because the attachment has not been approved
  No such test id  add-ram-attachment

Sonja approves the attachment
  Wait Until  Click button  id=test-attachment-approve
  Element should be visible by test id  add-ram-attachment

Sonja adds new RAM attachment
  Click by test id  add-ram-attachment
  Element should be visible by test id  ram-links-table
  Element should be visible by test id  ram-prefix
  Delete allowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo

Sonja adds file to RAM
  Add attachment version  ${PNG_TESTFILE_PATH}
  Delete allowed  False
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  -

Sonja can now approve the attachment
  Wait Until  Click button  id=test-attachment-approve
  Delete disallowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo

Sonja clicks RAM link and opens old attachment details
  Follow ram link  0
  Element should be visible by test id  ram-links-table
  No such test id  ram-prefix
  Delete disallowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Element should not be visible  jquery=td[data-test-id=ram-link-type-0] a
  Element should be visible  jquery=td[data-test-id=ram-link-type-1] a

Sonja returns to attachments and sees RAM filter
  Scroll and click test id  back-to-application-from-attachment
  Wait test id visible  ram-filter-label
  Checkbox wrapper selected by test id  ram-filter-checkbox
  #Scroll and click test id  ram-filter-label
  Javascript?  $("tr[data-test-type]:visible").length === 1
  Wait test id visible  ram-indicator
  [Teardown]  Logout

Mikko logs in and unselects post verdict filter
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter

RAM indicator is visible for RAM attachment
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='muut.muu'] span[data-test-id=ram-indicator]:visible

Mikko opens RAM attachment and sees RAM links but cannot delete the attachment
  # Only RAM is visible
  Open attachment details  muut.muu  0
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo
  Element should be visible by test id  ram-prefix
  Delete disallowed  False
  Element should be visible by test id  add-ram-attachment

Mikko adds new file version and thus resetting approval state
  Add attachment version  ${PNG_TESTFILE_PATH}
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete allowed  False
  No such test id  add-ram-attachment

Mikko follows RAM link and cannot delete the base attachment either
  Follow ram link  0
  Element should be visible by test id  ram-links-table
  No such test id  ram-prefix
  No such test id  add-ram-attachment
  Delete disallowed  True
  [Teardown]  Logout

Sonja logs in and goes to attachments tab
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter

Sonja rejects attachment but could not delete base attachment
  Open attachment details  muut.muu  0
  Follow ram link  0
  No such test id  ram-prefix
  Delete disallowed
  Wait Until  Click button  id=test-attachment-reject
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete disallowed

Sonja approves RAM (again)
  Follow ram link  1
  Wait Until  Click button  id=test-attachment-approve
  Delete disallowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  [Teardown]  Logout

Mikko logs in and goes to attachment tab
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter

Mikko opens RAM
  Open attachment details  muut.muu  0
  Element should be visible by test id  ram-prefix

Mikko adds new RAM attachment and uploads file
  Click by test id  add-ram-attachment
  Element should be visible by test id  ram-link-type-2
  Add attachment version  ${PNG_TESTFILE_PATH}
  Delete allowed  False
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
  [Teardown]  Logout

Sonja goes to attachments tab
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter

Sonja rejects the first RAM but cannot delete it
  Open attachment details  muut.muu  0
  Element should be visible by test id  ram-prefix
  Wait Until  Click button  id=test-attachment-reject
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete disallowed
  [Teardown]  Logout

Mikko goes to attachments tab
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter

Mikko deletes RAM
  Open attachment details  muut.muu  0
  Element should be visible by test id  ram-prefix
  Follow ram link  2
  Element should be visible by test id  delete-attachment
  Click by test id  delete-attachment
  Confirm yes no dialog
  Open attachment details  muut.muu  0
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  No such test id  ram-link-type-2
  [Teardown]  Logout

Sonja logs in to test filters
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open attachments tab and unselect post verdict filter 
  Scroll and click test id  ram-filter-label

Add pohjapiirustus file and approve it
  Add attachment file  tr[data-test-type='paapiirustus.pohjapiirustus']  ${PNG_TESTFILE_PATH}
  Return to application
  Approve row  tr[data-test-type='paapiirustus.pohjapiirustus']

Remove vaestonsuojasuunnitelma
  Remove row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']

Approve first muut.muu
  Approve row  tr[data-test-type='muut.muu']:first

Hide RAM attachments
  Scroll and click test id  preVerdict-filter-label
  Scroll and click test id  postVerdict-filter-label
  Checkbox wrapper not selected by test id  ram-filter-checkbox

Rollup states
  Rollup approved  Pääpiirustukset
  Rollup rejected  Muut suunnitelmat
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen

Sonja approves RAM
  Scroll and click test id  ram-filter-label
  Checkbox wrapper selected by test id  ram-filter-checkbox
  Approve row  tr[data-test-type='muut.muu']:last
  Rollup approved  Pääpiirustukset
  Rollup approved  Muut suunnitelmat
  Rollup approved  Asuinkerrostalon tai rivitalon rakentaminen

Sonja rejects Pohjapiirustus
  Reject row  tr[data-test-type='paapiirustus.pohjapiirustus']  
  Rollup rejected  Pääpiirustukset
  Rollup approved  Muut suunnitelmat
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen
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
  Element should be visible by test id  delete-attachment
  Run keyword if  ${click}  Element should be visible by test id  delete-version

Follow ram link
  [Arguments]  ${index}
  Wait until  Click link  jquery=td[data-test-id=ram-link-type-${index}] a

Approve row
  [Arguments]  ${row}
  Scroll and click  ${row} button.approve

Reject row
  [Arguments]  ${row}
  Scroll and click  ${row} button.reject

Remove row
  [Arguments]  ${row}
  Scroll and click  ${row} button[data-test-icon=delete-button]
  Confirm yes no dialog

Rollup approved
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button  
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive

Rollup rejected
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button  
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button
  Element should not be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive
