*** Settings ***

Documentation   Appeals management
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py

*** Test Cases ***

Mikko wants to build Skyscraper
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Skyscraper${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted
  [Teardown]  logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  Page Should Contain Element  //div[@data-test-id="given-verdict-id-0-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]
  Page Should Not Contain Element  //div[@data-test-id="given-verdict-id-1-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]

There are no appeals yet
  Element should not be visible  jquery=table.appeals-table

Sonja adds appeal to the first verdict
  Add to verdict  0-0  appeal  Veijo  1.4.2016  Hello world
  Appeals row check  0-0  0  appeal  Veijo  1.4.2016

Sonja opens attachment tab and unselects post verdict filter
  Open attachments tab and unselect post verdict filter

The appeal is visible on the Attachments tab
  Element should be visible  jquery=tr[data-test-type='muutoksenhaku.valitus']
  Open tab  verdict

Sonja edits appeal
  Wait test id visible  edit-appeal-0-0-0
  Click by test id  edit-appeal-0-0-0
  Check appeal bubble  0-0-0  appeal  Veijo  01.04.2016  Hello world
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
  Click by test id  remove-file-1
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

Mikko logs in. He can see the appeals but not edit them.
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Scroll to test id  show-appeal-0-0-3
  No such test id  add-appeal-0-0
  Wait test id visible  show-appeal-0-0-3
  Appeals row check  0-0  3  appealVerdict  Phong  1.6.2016
  [Teardown]  Logout

Sonja logs in and deletes the first verdict.
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Click element  jquery=[data-test-id=delete-verdict-from-listing]:first
  Confirm  dynamic-yes-no-confirm-dialog
  Appeals row check  0-0  0  appeal  Megabyte  7.7.2016

Sonja opens attachments tab and unselects post verdict filter
  Open attachments tab and unselect post verdict filter

There is only one appeal in the Attachments tab
  Xpath should match X times  //tr[@data-test-type='muutoksenhaku.valitus']  1
  Open tab  verdict

Fetching new verdicts will nuke appeals
  Scroll and click test id  fetch-verdict
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should not be visible  jquery=table.appeals-table

There are no appeals attachments in the Attachments tab
  Open tab  attachments
  Element should not be visible  jquery=tr[data-test-type='muutoksenhaku.valitus']
  Element should not be visible  jquery=tr[data-test-type='muutoksenhaku.oikaisuvaatimus']
  Xpath should match X times  //tr[@data-test-type='paatoksenteko.paatos']  2
  [Teardown]  Logout


*** Keywords ***

Edit authors
  [Arguments]  ${postfix}  ${authors}
  Fill test id  appeal-authors-${postfix}  ${authors}

Edit date
  [Arguments]  ${postfix}  ${date}
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Fill test id  appeal-date-${postfix}  ${date}

Edit extra
  [Arguments]  ${postfix}  ${extra}
  Fill test id  appeal-extra-${postfix}  ${extra}

Add file
  [Arguments]  ${postfix}  ${path}=${TXT_TESTFILE_PATH}
  Execute JavaScript  $('div[data-test-id=appeal-files-${postfix}] input[type=file]').attr("class", "")
  Choose File  jquery=div[data-test-id=appeal-files-${postfix}] input[type=file]  ${path}

OK bubble ${postfix}
  Scroll and click test id  appeal-${postfix}-bubble-dialog-ok
  # DOM changes after OK, so let's wait a bit.
  Sleep  2s

Cancel bubble ${postfix}
  Scroll and click test id  appeal-${postfix}-bubble-dialog-cancel

Check appeal bubble
  [Arguments]  ${postfix}  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll to test id  appeal-${postfix}-bubble-dialog-ok
  Element should be visible  jquery=span[data-test-id=appeal-type-${postfix}][data-appeal-type=${appealType}]
  Textfield value should be  jquery=input[data-test-id=appeal-authors-${postfix}]  ${authors}
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Textfield value should be  jquery=input[data-test-id=appeal-date-${postfix}]  ${date}
  Textarea value should be  jquery=textarea[data-test-id=appeal-extra-${postfix}]  ${extra}

Add to verdict
  [Arguments]  ${postfix}  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll and click test id  add-appeal-${postfix}
  Scroll to test id  appeal-${postfix}-bubble-dialog-ok
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  List selection should be  jquery=select[data-test-id=appeal-type-${postfix}]  ${EMPTY}
  Select From List By Value  jquery=select[data-test-id=appeal-type-${postfix}]  ${appealType}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Textfield value should be  jquery=input[data-test-id=appeal-authors-${postfix}]  ${EMPTY}
  Edit authors  ${postfix}  ${authors}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Textfield value should be  jquery=input[data-test-id=appeal-date-${postfix}]  ${EMPTY}
  Edit date  ${postfix}  ${date}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Textarea value should be  jquery=textarea[data-test-id=appeal-extra-${postfix}]  ${EMPTY}
  Edit extra  ${postfix}  ${extra}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Add file  ${postfix}
  Ok bubble ${postfix}

Set Row Selector
  [Arguments]  ${postfix}  ${row}
  Set Suite Variable  ${selector}  jquery=table[data-test-id=appeals-table-${postfix}] tr[data-test-id=appeals-table-row-${row}]

Appeals row check
  [Arguments]  ${postfix}  ${row}  ${appealType}  ${authors}  ${date}
  Set Row Selector  ${postfix}  ${row}
  Wait Until  Element Should Be Visible  ${selector} td[data-appeal-type=${appealType}]
  Wait Until  Element should contain  ${selector}  ${authors}
  Wait Until  Element should contain  ${selector}  ${date}

Appeals row file check
  [Arguments]  ${postfix}  ${row}  ${filename}  ${index}=0
  Set Row Selector  ${postfix}  ${row}
  Wait Until  Element should contain  ${selector} li[data-test-id=appeals-files-${index}] a  ${filename}
