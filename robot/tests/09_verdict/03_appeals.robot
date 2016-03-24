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

Sonja edits appeal
  Wait test id visible  edit-appeal-0-0-0
  Click by test id  edit-appeal-0-0-0
  Check appeal bubble  0-0-0  appeal  Veijo  01.04.2016  Hello world
  Edit authors  0-0-0  Liisa
  Edit date  0-0-0  5.5.2015
  # TODO: omit after full file support
  Add file 0-0-0
  OK bubble 0-0-0
  Appeals row check  0-0  0  appeal  Liisa  5.5.2015

Invalid date should show warning
  Click by test id  edit-appeal-0-0-0
  Edit date  0-0-0  33.88.99999
  Element should be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Edit date  0-0-0  12.4.2011
  Element should not be visible  jquery=div.bubble-dialog .form-cell__message .lupicon-warning
  Cancel bubble 0-0-0
  Appeals row check  0-0  0  appeal  Liisa  5.5.2015

Sonja deletes appeal
  Click by test id  delete-appeal-0-0-0
  Wait Until  Element should not be visible  jquery=table.appeals-table

Sonja adds rectification
  Add to verdict  0-0  appeal  Bob  1.4.2016
  Add to verdict  0-0  rectification  Dot  1.5.2016
  Wait test id visible  edit-appeal-0-0-0
  Wait test id visible  edit-appeal-0-0-1

Sonja adds appealVerdict thus locking appeals
  Add to verdict  0-0  appealVerdict  Phong  1.6.2016
  Scroll to test id  appeal-0-0-bubble-dialog-ok
  No such test id  edit-appeal-0-0-0
  No such test id  edit-appeal-0-0-1
  Wait test id visible  show-appeal-0-0-0
  Wait test id visible  show-appeal-0-0-1


*** Keywords ***

Edit authors
  [Arguments]  ${postfix}  ${authors}
  Fill test id  appeal-authors-${postfix}  ${authors}

Edit date
  [Arguments]  ${postfix}  ${date}
  Fill test id  appeal-date-${postfix}  ${date}

Edit extra
  [Arguments]  ${postfix}  ${extra}
  Fill test id  appeal-extra-${postfix}  ${extra}

Add file ${postfix}
  Execute JavaScript  $('div[data-test-id=appeal-files-${postfix}] input[type=file]').attr("class", "")
  Choose File  jquery=div[data-test-id=appeal-files-${postfix}] input[type=file]  ${TXT_TESTFILE_PATH}

OK bubble ${postfix}
  Scroll and click test id  appeal-${postfix}-bubble-dialog-ok

Cancel bubble ${postfix}
  Scroll and click test id  appeal-${postfix}-bubble-dialog-cancel

Check appeal bubble
  [Arguments]  ${postfix}  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Scroll to test id  appeal-${postfix}-bubble-dialog-ok
  Element should be visible  jquery=span[data-test-id=appeal-type-${postfix}][data-appeal-type=${appealType}]
  Textfield value should be  jquery=input[data-test-id=appeal-authors-${postfix}]  ${authors}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
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
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Textfield value should be  jquery=input[data-test-id=appeal-date-${postfix}]  ${EMPTY}
  Edit date  ${postfix}  ${date}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Textarea value should be  jquery=textarea[data-test-id=appeal-extra-${postfix}]  ${EMPTY}
  Edit extra  ${postfix}  ${extra}
  Test id disabled  appeal-${postfix}-bubble-dialog-ok
  Add file ${postfix}
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
