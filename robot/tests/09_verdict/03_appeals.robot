*** Settings ***

Documentation   Appeals management
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py

*** Test Cases ***

# ---------------------
# Mikko
# ---------------------

Mikko wants to build Skyscraper
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Skyscraper${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted
  [Teardown]  logout

# ---------------------
# Sonja
# ---------------------

Sonja logs in
  Sonja logs in  False
  Open application  ${appname}  753-416-25-30

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Open verdict

There are no appeals yet
  No appeals
  Wait test id visible  new-appeal

Sonja adds appeal to the first verdict
  Add appeal  appeal  Veijo  1.4.2016  Hello world
  Appeals row check  0  appeal  Veijo  1.4.2016

The appeal is visible on the Attachments tab
  Sleep  1.0s
  Click back
  Open tab  attachments
  # TODO: Change this after the attachments types have been restricted
  Element should be visible  jquery=tr[data-test-type='paatoksenteko.valitusosoitus']
  Open tab  verdict

Sonja edits appeal
  Open verdict
  Wait test id visible  appeal-0-edit
  Click by test id  appeal-0-edit
  Check appeal form  Valitus  Veijo  1.4.2016  Hello world
  Edit authors  Liisa
  Edit date  5.5.2015
  Save appeal
  Appeals row check  0  appeal  Liisa  5.5.2015

Invalid date should show warning
  [Tags]  Fail
  Click by test id  edit-appeal-0-0-0
  Edit date  0-0-0  33.88.99999
  Element should be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Test id disabled  appeal-0-0-0-bubble-dialog-ok
  Edit date  0-0-0  12.4.2011
  Element should not be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Cancel bubble 0-0-0
  Appeals row check  0-0  0  appeal  Liisa  5.5.2015

Appeal can contain multiple files
  Click by test id  appeal-0-edit
  Add file   appeal  ${PNG_TESTFILE_PATH}
  Save appeal
  Appeals row file check  0  ${PNG_TESTFILE_NAME}  1
  # Originally TXT file was uploaded, but it is converted to PDF/A by Libre Office
  Appeals row file check  0  ${PDF_TESTFILE_NAME}

Both files are shown in the Attachments tab
  Click back
  Open tab  attachments
  Page should contain  ${PNG_TESTFILE_NAME}
  Page should contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja removes the image file from appeal
  Open verdict
  Click by test id  appeal-0-edit
  Click by test id  delete-appeal-file-1
  Save appeal
  No such test id  appeal-file-0-1

The image file has also been removed from the Attachments tab
  Click back
  Open tab  attachments
  Page should not contain  ${PNG_TESTFILE_NAME}
  Page should contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja deletes appeal
  Open verdict
  Click by test id  appeal-0-delete
  Wait indicators
  No appeals
  Click back

No appeal attachments
  Open tab  attachments
  Page should not contain  ${TXT_TESTFILE_NAME}
  Page should not contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja adds appeal and rectification
  Open verdict
  Add appeal  appeal  Bob  1.4.2016  I am unhappy!
  Add appeal  rectification  Dot  1.5.2016
  Wait test id visible  appeal-0-edit
  Wait test id visible  appeal-0-edit

Sonja adds appealVerdict thus locking appeals
  Add appeal  appealVerdict  Phong  1.6.2016
  No such test id  appeal-0-edit
  No such test id  appeal-1-edit
  Wait test id visible  appeal-0-show
  Wait test id visible  appeal-1-show

Every appeal type is shown in the Attachments tab
  Click back
  Open tab  attachments
  # TODO: attachment types (see above)
  Element should be visible  jquery=tr[data-test-type='paatoksenteko.valitusosoitus']
  Element should be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
  Element should be visible  jquery=tr[data-test-type='ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos']
  # There is a verdict and an appealVerdict
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.paatos']  2

Show info buttons show correct data
  Open tab  verdict
  Open verdict
  Scroll to test id  new-appeal
  Click by test id  appeal-0-show
  Test id should contain  appeal-0-note  I am unhappy!
  Click by test id  appeal-1-show
  Wait test id visible  appeal-1-empty

Adding appeal locks appealVerdict
  Add appeal  appeal  Frisket  1.7.2016
  No such test id  appeal-2-edit
  Wait test id visible  appeal-2-show

Making appeal date earlier than appealVerdict makes the latter editable
  Click by test id  appeal-3-edit
  Edit date  1.1.2010
  Save appeal
  Wait test id visible  appeal-3-edit
  Appeals row check  3  appealVerdict  Phong  1.6.2016
  [Teardown]  Logout

# ---------------------
# Mikko
# ---------------------

Mikko logs in and sees that only the first verdict has appeals
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Open verdict  0
  Yes appeals
  Click back
  Open verdict  1
  No appeals
  [Teardown]  Logout

# ---------------------
# Sonja
# ---------------------

Sonja logs in and adds more appeals
  Sonja logs in  False
  Open application  ${appname}  753-416-25-30
  Open tab  verdict

The first appeal cannot be appealVerdict
  Open verdict  1
  Scroll and click test id  new-appeal
  Test id select values are  appeal-type  ${EMPTY}  rectification  appeal
  Cancel appeal
  Click back

Appeals in different verdicts do not affect each other
  Open verdict  1
  Add appeal  appeal  Megabyte  7.7.2016
  Wait test id visible  appeal-0-edit
  No such test id  appeal-1-edit
  No such test id  appeal-1-show
  Click back

Verdict fetch now shows confirmation dialog
  Scroll and click test id  fetch-verdict
  Deny yes no dialog

Sonja creates and submits application for building Meishuguan
  ${secs} =  Get Time  epoch
  Set test variable  ${meishuguan}  Meishuguan${secs}
  Create application with state  ${meishuguan}  753-416-88-88  kerrostalo-rivitalo  submitted
  Open application  ${meishuguan}  753-416-88-88

Fetching verdict for Meishuguan should not show confirmation
  Open tab  verdict
  Fetch verdict
  [Teardown]  Logout

# ---------------------
# Mikko
# ---------------------

Mikko logs in. He can see the appeals but not edit them.
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Open verdict
  Yes appeals
  Appeals row check  3  appealVerdict  Phong  1.6.2016
  [Teardown]  Logout

Sonja logs in and deletes the first verdict.
  Sonja logs in  False
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Delete verdict  0

There is only one appeal in the Attachments tab
  Open tab  attachments
  # TODO: attachment types
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.valitusosoitus']  1
  Xpath should match X times  //tr[@data-test-type='muutoksenhaku.oikaisuvaatimus']  0
  Xpath should match X times  //tr[@data-test-type='ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos']  0
  Open tab  verdict

Fetching new verdicts will nuke appeals
  Scroll and click test id  fetch-verdict
  Confirm yes no dialog
  R verdict fetched

There are no appeals attachments in the Attachments tab
  Open tab  attachments
# TODO: attachment types
  Wait until  Element should not be visible  jquery=tr[data-test-type='paatoksenteko.valitusosoitus']
  Wait until  Element should not be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
  Wait until  Element should not be visible  jquery=tr[data-test-type='ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos']
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.paatos']  2
  [Teardown]  Logout


*** Keywords ***

Edit authors
  [Arguments]  ${authors}
  Fill test id  appeal-authors  ${authors}

Edit date
  [Arguments]  ${date}
  Fill test id  appeal-date  ${date}

Edit extra
  [Arguments]  ${extra}
  Fill test id  appeal-text  ${extra}

Add file
  [Arguments]  ${appealType}  ${path}=${TXT_TESTFILE_PATH}
  Run keyword if  "${appealType}" == "appeal"  Pate upload  0  ${path}  Valitus  Complaint  pate-upload-input
  Run keyword if  "${appealType}" == "rectification"  Pate upload  0  ${path}  Oikaisuvaatimus  Complaint  pate-upload-input
  Run keyword if  "${appealType}" == "appealVerdict"  Pate upload  0  ${path}  Päätös  Complaint  pate-upload-input

Wait indicators
  Positive indicator should be visible
  Positive indicator should not be visible

Save appeal
  Wait until  Test id enabled  save-appeal
  Scroll and click test id  save-appeal
  Wait indicators

Cancel appeal
  Scroll and click test id  cancel-appeal

Check appeal form
  [Arguments]  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll to test id  save-appeal
  Test id text is  appeal-type  ${appealType}
  Test id input is  appeal-authors  ${authors}
  Test id input is  appeal-date  ${date}
  Test id input is  appeal-text  ${extra}

Add appeal
  [Arguments]  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll and click test id  new-appeal
  Scroll to test id  save-appeal
  Test id disabled  save-appeal
  List selection should be  jquery=select[data-test-id=appeal-type]  ${EMPTY}
  Select From List By Value  jquery=select[data-test-id=appeal-type]  ${appealType}
  Test id disabled  save-appeal
  Textfield value should be  jquery=input[data-test-id=appeal-authors]  ${EMPTY}
  Edit authors  ${authors}
  Test id disabled  save-appeal
  Textfield value should be  jquery=input[data-test-id=appeal-date]  ${EMPTY}
  Edit date  ${date}
  Test id disabled  save-appeal
  Textarea value should be  jquery=textarea[data-test-id=appeal-text]  ${EMPTY}
  Edit extra  ${extra}
  Test id disabled  save-appeal
  Add file  ${appealType}
  Save appeal

Set Row Selector
  [Arguments]  ${postfix}  ${row}
  Set Suite Variable  ${selector}  jquery=table[data-test-id=appeals-table-${postfix}] tr[data-test-id=appeals-table-row-${row}]

Appeals row check
  [Arguments]  ${index}  ${appealType}  ${authors}  ${date}
  Wait test id visible  appeal-${index}-${appealType}
  Test id text is  appeal-${index}-authors  ${authors}
  Test id text is  appeal-${index}-date  ${date}

Appeals row file check
  [Arguments]    ${row-index}  ${filename}  ${file-index}=0
  Wait test id visible  appeal-file-${row-index}-${file-index}

No frontend errors
  Logout
  There are no frontend errors

No appeals
    Wait Until  Element should not be visible  jquery=table.pate-appeals

Yes appeals
    Wait Until  Element should be visible  jquery=table.pate-appeals
