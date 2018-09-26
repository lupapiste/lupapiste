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
  Element should not be visible  jquery=table.pate-appeals
  Wait test id visible  new-appeal

Sonja adds appeal to the first verdict
  Add appeal  appeal  Veijo  1.4.2016  Hello world
  Appeals row check  0  appeal  Veijo  1.4.2016

The appeal is visible on the Attachments tab
  Sleep  1.0s
  Click back
  Open tab  attachments
  Element should be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
  Open tab  verdict
  debug

Sonja edits appeal
  Wait test id visible  appeal-0-toggle
  Click by test id  appeal-0-toggle
  Check appeal form  0-0-0  appeal  Veijo  01.04.2016  Hello world
  Edit authors  0-0-0  Liisa
  Edit date  0-0-0  5.5.2015
  OK bubble 0-0-0
  Appeals row check  0-0  0  appeal  Liisa  5.5.2015

Invalid date should show warning
  Click by test id  edit-appeal-0-0-0
  Edit date  0-0-0  33.88.99999
  Element should be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Test id disabled  appeal-0-0-0-bubble-dialog-ok
  Edit date  0-0-0  12.4.2011
  Element should not be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Cancel bubble 0-0-0
  Appeals row check  0-0  0  appeal  Liisa  5.5.2015

Appeal can contain multiple files
  Click by test id  edit-appeal-0-0-0
  Add file  0-0-0  ${PNG_TESTFILE_PATH}
  OK bubble 0-0-0
  Appeals row file check  0-0  0  ${PNG_TESTFILE_NAME}  1
  # Originally TXT file was uploaded, but it is converted to PDF/A by Libre Office
  Appeals row file check  0-0  0  ${PDF_TESTFILE_NAME}  0

Both files are shown in the Attachments tab
  Open tab  attachments
  Page should contain  ${PNG_TESTFILE_NAME}
  Page should contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja removes the image file from appeal
  Click by test id  edit-appeal-0-0-0
  Click by test id  remove-appeal-file-1
  OK bubble 0-0-0
  Wait Until  Page should not contain  ${PNG_TESTFILE_NAME}

The image file has also been removed from the Attachments tab
  Open tab  attachments
  Page should not contain  ${PNG_TESTFILE_NAME}
  Page should contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja deletes appeal
  Click by test id  delete-appeal-0-0-0
  Wait Until  Element should not be visible  jquery=table.appeals-table

No appeal attachments
  Open tab  attachments
  Page should not contain  ${TXT_TESTFILE_NAME}
  Page should not contain  ${PDF_TESTFILE_NAME}
  Open tab  verdict

Sonja adds appeal and rectification
  Add to verdict  0-0  appeal  Bob  1.4.2016  I am unhappy!
  Add to verdict  0-0  rectification  Dot  1.5.2016
  Wait test id visible  edit-appeal-0-0-0
  Wait test id visible  edit-appeal-0-0-1

Sonja adds appealVerdict thus locking appeals
  Add to verdict  0-0  appealVerdict  Phong  1.6.2016
  Scroll to test id  add-appeal-0-0
  Sleep  1.0s
  No such test id  edit-appeal-0-0-0
  No such test id  edit-appeal-0-0-1
  Wait test id visible  show-appeal-0-0-0
  Wait test id visible  show-appeal-0-0-1

Every appeal type is shown in the Attachments tab
  Open tab  attachments
  Element should be visible  jquery=tr[data-test-type='muutoksenhaku.valitus']
  Element should be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
  # There is a verdict and an appealVerdict
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.paatos']  3

Show info buttons show correct data
  Open tab  verdict
  Scroll to test id  add-appeal-0-0
  Click by test id  show-appeal-0-0-0
  Test id should contain  info-appeal-0-0-0  I am unhappy!
  Click by test id  show-appeal-0-0-1
  Test id should contain  info-appeal-0-0-1  Ei lisätietoja.

Adding appeal locks appealVerdict
  Add to verdict  0-0  appeal  Frisket  1.7.2016
  Scroll to test id  add-appeal-0-0
  No such test id  edit-appeal-0-0-2
  Wait test id visible  show-appeal-0-0-2

Making appeal date earlier than appealVerdict makes the latter editable
  Click by test id  edit-appeal-0-0-3
  Scroll to test id  appeal-0-0-3-bubble-dialog-ok
  Edit date  0-0-3  1.1.2010
  OK bubble 0-0-3
  Wait test id visible  edit-appeal-0-0-3
  Appeals row check  0-0  3  appealVerdict  Phong  1.6.2016
  [Teardown]  Logout

# ---------------------
# Mikko
# ---------------------

Mikko logs in and sees only one appeal title
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  jQuery should match X times  h2[data-test-id=verdict-appeal-title]  1
  [Teardown]  Logout

# ---------------------
# Sonja
# ---------------------

Sonja logs in and adds more appeals
  Sonja logs in  False
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  jQuery should match X times  h2[data-test-id=verdict-appeal-title]  3

The first appeal cannot be appealVerdict
  Add to verdict  1-0  appealVerdict  Megabyte  29.3.2016
  Wait test id visible  appeal-1-0-bubble-dialog-error
  Cancel bubble 1-0

Appeals in different verdicts do not affect each other
  Add to verdict  1-0  appeal  Megabyte  7.7.2016
  Wait test id visible  edit-appeal-1-0-0
  Wait test id visible  edit-appeal-0-0-3

Verdict fetch now shows confirmation dialog
  Scroll and click test id  fetch-verdict
  Deny  dynamic-yes-no-confirm-dialog
  Wait test id visible  edit-appeal-0-0-3

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
  jQuery should match X times  h2[data-test-id=verdict-appeal-title]  3
  Scroll to test id  show-appeal-0-0-3
  No such test id  add-appeal-0-0
  Wait test id visible  show-appeal-0-0-3
  Appeals row check  0-0  3  appealVerdict  Phong  1.6.2016
  [Teardown]  Logout

Sonja logs in and deletes the first verdict.
  Sonja logs in  False
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Click element  jquery=[data-test-id=delete-verdict-from-listing]:first
  Confirm  dynamic-yes-no-confirm-dialog
  Appeals row check  0-0  0  appeal  Megabyte  7.7.2016

There is only one appeal in the Attachments tab
  Open tab  attachments
  Xpath should match X times  //tr[@data-test-type='muutoksenhaku.valitus']  1
  Xpath should match X times  //tr[@data-test-type='muutoksenhaku.oikaisuvaatimus']  0
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.paatos']  2
  Open tab  verdict

Fetching new verdicts will nuke appeals
  Scroll and click test id  fetch-verdict
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should be visible  dynamic-ok-confirm-dialog
  Confirm  dynamic-ok-confirm-dialog
  Wait Until  Element should not be visible  jquery=table.appeals-table

There are no appeals attachments in the Attachments tab
  Open tab  attachments
  Element should not be visible  jquery=tr[data-test-type='muutoksenhaku.valitus']
  Element should not be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
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
  [Arguments]  ${path}=${TXT_TESTFILE_PATH}
  Pate upload  0  ${path}  Oikaisuvaatimus  Complaint  pate-upload-input

Save appeal
  Wait until  Test id enabled  save-appeal
  Scroll and click test id  save-appeal

Cancel bubble ${postfix}
  Scroll and click test id  appeal-${postfix}-bubble-dialog-cancel

Check appeal form
  [Arguments]  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll to test id  save-appeal
  Element should be visible  jquery=span[data-test-id=appeal-type-${postfix}][data-appeal-type=${appealType}]
  Textfield value should be  jquery=input[data-test-id=appeal-authors-${postfix}]  ${authors}
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Textfield value should be  jquery=input[data-test-id=appeal-date-${postfix}]  ${date}
  Textarea value should be  jquery=textarea[data-test-id=appeal-extra-${postfix}]  ${extra}

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
  Add file
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
  [Arguments]  ${postfix}  ${row}  ${filename}  ${index}=0
  Set Row Selector  ${postfix}  ${row}
  Wait Until  Element should contain  ${selector} li[data-test-id=appeals-files-${index}] a  ${filename}

No frontend errors
  Logout
  There are no frontend errors
