*** Settings ***

Documentation  Rakentamisen aikaiset muutokset (RAM)
Suite Setup    Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Resource       ../../common_keywords/approve_helpers.robot
Resource        ../39_pate/pate_resource.robot
Variables      variables.py

*** Variables ***

${type}           paapiirustus.pohjapiirustus
${shelter}        pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma


*** Test Cases ***

Mikko creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Duram Duram${secs}
  Mikko logs in
  Create application with state   ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted

Mikko adds png attachment without comment
  Open tab  attachments
  Add attachment file  tr[data-test-type='${type}']  ${PNG_TESTFILE_PATH}  ${type}
  Click by test id  batch-ready
  Application state should be  submitted

Mikko opens attachment details
  Open attachment details  ${type}
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
  Open tab  attachments
  Unselect post verdict filter

There are no RAM filters
  No such test id  ram-filter-label

Sonja opens attachment details
  Open attachment details  ${type}
  No such test id  ram-links-table

Sonja cannot add RAM because the attachment has not been approved
  No such test id  add-ram-attachment

Sonja approves the attachment
  Click button  id=test-attachment-approve
  Element should be visible by test id  add-ram-attachment

Since organization supports RAMs, no disabled RAM message
  No such test id  ram-message

Sonja adds new RAM attachment
  Click by test id  add-ram-attachment
  # Attachment page for new ram-attachment opens...
  Wait until  Element should be visible by test id  ram-prefix
  Wait until  Element should be visible by test id  ram-links-table
  Delete allowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo

Sonja adds file to RAM
  Add attachment version  ${PNG_TESTFILE_PATH}
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo

Attachment get automatically approved since it is uploaded by authority
  Wait until  Element should be visible  xpath=//i[@data-test-id='0-0-1-approved']
  Delete disallowed  True

Sonja clicks RAM link and opens old attachment details
  Follow ram link  0
  # Attachment page changes to old attachment
  Wait until  Element should not be visible by test id  ram-prefix
  Wait until  Element should be visible by test id  ram-links-table
  Delete disallowed
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Element should not be visible  jquery=td[data-test-id=ram-link-type-0] a
  Element should be visible  jquery=td[data-test-id=ram-link-type-1] a

Sonja returns to attachments and sees RAM filter
  Return to application
  Wait test id visible  ram-filter-label
  Checkbox wrapper selected by test id  ram-filter
  #Scroll and click test id  ram-filter-label
  Javascript?  $("tr[data-test-type]:visible").length === 1
  Wait test id visible  ram-indicator
  Logout

Mikko logs in and unselects post verdict filter
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  # RAM and postverdict filters are selected by default
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter
  Wait until  Checkbox wrapper selected by test id  ram-filter
  Unselect post verdict filter
  # Now only RAM attachments are visible

RAM indicator is visible for RAM attachment
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='${type}'] span[data-test-id=ram-indicator]:visible

Mikko opens RAM attachment and sees RAM links but cannot delete the attachment
  # Only RAM is visible
  Open attachment details  ${type}  0
  Wait until  Element should be visible by test id  ram-prefix
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Sonja Sibbo  Sonja Sibbo
  Delete disallowed  False
  Element should be visible by test id  add-ram-attachment

Mikko adds new file version but still cannot delete the attachment
  Add attachment version  ${PNG_TESTFILE_PATH}
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete disallowed
  No such test id  add-ram-attachment

Mikko follows RAM link and cannot delete the base attachment either
  Follow ram link  0
  # Attachment page changes to to old attachment
  Wait until  Element should be visible by test id  ram-links-table
  No such test id  ram-prefix
  No such test id  add-ram-attachment
  Delete disallowed  True
  [Teardown]  Logout

Sonja logs in and goes to attachments tab
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Unselect post verdict filter
  # Now only RAM attachments are visible

Sonja rejects base attachment but can not delete it
  Open attachment details  ${type}  0
  Follow ram link  0
  No such test id  ram-prefix
  Delete disallowed
  Reject attachment with note  test-attachment-reject  details-reject  Rejected!
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete disallowed

Sonja opens and approves RAM (again)
  Follow ram link  1
  Wait until  Element should be visible by test id  ram-prefix
  Wait Until  Click button  id=test-attachment-approve
  Delete disallowed  True
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  Sonja Sibbo
  Logout

Mikko logs in and goes to attachment tab
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Unselect post verdict filter

RAM filter enabled and one attachment row is visible
  Checkbox wrapper selected by test id  ram-filter
  Wait until  Total attachments row count is  1

Mikko opens RAM
  Open attachment details  ${type}  0
  Element should be visible by test id  ram-prefix

Mikko adds new RAM attachment and uploads file
  Click by test id  add-ram-attachment
  # New attachment page opens
  Wait until  Element should be visible by test id  ram-link-type-2
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
  Open tab  attachments
  Unselect post verdict filter

RAM filter enabled and two attachment rows are visible
  Checkbox wrapper selected by test id  ram-filter
  Wait until  Total attachments row count is  2

Sonja rejects the first RAM but cannot delete it
  Open attachment details  ${type}  0
  Wait until  Element should be visible by test id  ram-prefix
  Reject attachment with note  test-attachment-reject  details-reject  Rejected too!
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Delete disallowed
  Logout

Mikko goes to attachments tab
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Unselect post verdict filter

Two RAM attachment rows are visible
  Checkbox wrapper selected by test id  ram-filter
  Wait until  Total attachments row count is  2

Mikko deletes RAM
  Open attachment details  ${type}  0
  Wait until  Element should be visible by test id  ram-prefix
  Follow ram link  2
  Wait until  Element should be visible by test id  delete-attachment
  Click by test id  delete-attachment
  Confirm yes no dialog

Only one attachment is visible now
  Wait until  Tab should be visible  attachments
  Wait until  Total attachments row count is  1
  Open attachment details  ${type}  0
  Check link row  0  Alkuperäinen  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  Check link row  1  RAM-liite  ${PNG_TESTFILE_NAME}  Mikko Intonen  -
  No such test id  ram-link-type-2

Sonja logs in to test filters
  Logout
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments

Unselects filters
  Unselect post verdict filter
  Scroll and click test id  ram-filter-label
  Checkbox wrapper not selected by test id  postVerdict-filter
  Checkbox wrapper not selected by test id  ram-filter
  Wait until  Total attachments row count is  6

Add shelter file and approve it
  Add attachment file  tr[data-test-type='${shelter}']  ${PNG_TESTFILE_PATH}  ${shelter}
  Click by test id  batch-ready
  Open attachment details  ${shelter}
  Attachment is  approved
  Return to application

Two pohjapiirrustus rows exist
  Xpath Should Match X Times  //tr[@data-test-type='${type}']  2

Approve first Pohjapiirustus
  Approve row  tr[data-test-type='${type}']:first

Hide RAM attachments
  Scroll and click test id  preVerdict-filter-label
  Scroll and click test id  postVerdict-filter-label
  Checkbox wrapper not selected by test id  ram-filter

Rollup states
  Rollup rejected  PÄÄPIIRUSTUKSET
  Rollup approved  MUUT SUUNNITELMAT
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja removes asemapiirros
  Remove row  tr[data-test-type='paapiirustus.asemapiirros']

Sonja approves RAM
  Scroll and click test id  ram-filter-label
  Checkbox wrapper selected by test id  ram-filter
  Approve row  tr[data-test-type='${type}']:last
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup approved  MUUT SUUNNITELMAT
  Rollup approved  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja rejects shelter
  Reject row  tr[data-test-type='${shelter}']
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup rejected  MUUT SUUNNITELMAT
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja adds CV. It does not support RAMs
  Upload attachment  ${PNG_TESTFILE_PATH}  CV  CV  Osapuolet
  Open attachment details  osapuolet.cv
  Wait Until  Element should be disabled  test-attachment-approve
  Attachment is  approved
  No such test id  add-ram-attachment
  [Teardown]  Logout

Sipoo admin disallows RAMs
  Sipoo logs in
  Go to page  applications
  Click label by test id  disable-ram-label
  Fill test id  ramDisabledFi  Morjens

Reload page to make sure that options have been saved
  Reload page
  Wait until  Checkbox should be selected  organization-ram-disabled
  Test id input is  ramDisabledFi  Morjens
  [Teardown]  Logout

Sonja logs in and opens shelter file details
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Click by test id  toggle-all-filters-checkbox
  Open attachment details  ${shelter}

Attachment is rejected and RAM message is not shown
  Attachment is  rejected
  No such test id  ram-message

Sonja approves attachment: no RAM button but message
  Click button  id=test-attachment-approve
  No such test id  add-ram-attachment
  Test id text is  ram-message  Morjens
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
