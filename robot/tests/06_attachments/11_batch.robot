*** Settings ***

Documentation  Uploding a batch of attachments
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py

*** Test Cases ***

# ------------------------------
# Pena
# ------------------------------

Pena creates application with empty attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Cabbage Batch${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Pena logs in
  Create application with state  ${appname}  ${propertyId}  kerrostalo-rivitalo  submitted
  Open tab  attachments

There are four empty attachments
  Empty attachment  hakija.valtakirja
  Empty attachment  paapiirustus.asemapiirros
  Empty attachment  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Empty attachment  paapiirustus.pohjapiirustus

Bad batch
  No such test id  upload-progress-title
  Add bad
  No such test id  upload-progress-title
  Element should not be visible  jquery=table.attachment-batch-table

Add good one
  Add to batch
  Test id should contain  upload-progress-title  1 tiedosto lisätty
  Only batch visible

Edit properties
  Select type  0  CV
  Set contents  0  Hello
  Set drawing  0  88
  Select grouping  0  Selvitykset

Visit another tab and come back
  Open tab  parties
  No such test id  batch-ready
  Open tab  attachments
  Only batch visible

Properties are unchanged
  Type is  0  CV
  Contents is  0  Hello
  Drawing is  0  88
  Grouping is  0  Selvitykset

Progress has bar has vanished (not a big deal)
  No such test id  upload-progress-title

Cancel the batch
  Scroll and click test id  batch-cancel
  Wait until  Element should not be visible  jquery=table.attachment-batch-table

Batch properties are stored only within the application context
  Add to batch
  Test id should contain  upload-progress-title  1 tiedosto lisätty
  Only batch visible
  Select type  0  Muistio
  Go to page  applications
  Open application attachments
  Wait until  Element should not be visible  jquery=table.attachment-batch-table

Batch of four files
  No such test id  upload-progress-title
  Add to batch
  Test id should contain  upload-progress-title  1 tiedosto lisätty
  No such test id  sign-all
  Add to batch
  Test id should contain  upload-progress-title  2 tiedostoa lisätty
  Wait test id visible  sign-all
  Add to batch
  Test id should contain  upload-progress-title  3 tiedostoa lisätty
  Add to batch
  Test id should contain  upload-progress-title  4 tiedostoa lisätty
  Test id disabled  batch-ready

Default contents: type
  Select type  0  CV
  Contents is  0  CV
  Grouping is  0  Osapuolet

Default contents: only item
  Select type  1  Muu pelastusviranomaisen suunnitelma
  Contents is  1  Paloturvallisuuden perustietolomake
  Grouping is  1  Tekniset selvitykset

Default contents: empty
  Select type  2  KVV-suunnitelma
  Contents is  2  ${EMPTY}
  Grouping is  2  Asuinkerrostalon tai rivitalon rakentaminen

Fill type
  Fill down  type-0
  Type is  0  CV
  Type is  1  CV
  Type is  2  CV
  Type is  3  CV
  Grouping is  0  Osapuolet
  Grouping is  1  Osapuolet
  Grouping is  2  Osapuolet
  Grouping is  3  Osapuolet

Contents have changed too
  Contents is  0  CV
  Contents is  1  CV
  Contents is  2  CV
  Contents is  3  CV
  Test id enabled  batch-ready

Fill contents
  Set contents  1  New contents
  Sleep  0.5s   # wait for "New" to reach its observable
  Fill down  contents-1
  Contents is  0  CV
  Contents is  1  New contents
  Contents is  2  New contents
  Contents is  3  New contents

Fill grouping
  Deselect grouping  2
  Fill down  grouping-2
  Grouping is  0  Osapuolet
  Grouping is  1  Osapuolet
  Grouping is general  2
  Grouping is general  3

Fill drawing: integers
  Fill test id  batch-drawing-0  1
  Fill down  drawing-0
  Test id input is  batch-drawing-0  1
  Test id input is  batch-drawing-1  2
  Test id input is  batch-drawing-2  3
  Test id input is  batch-drawing-3  4

Fill drawing: decimals with comma
  Fill test id  batch-drawing-1  0,19
  Fill down  drawing-1
  Test id input is  batch-drawing-0  1
  Test id input is  batch-drawing-1  0,19
  Test id input is  batch-drawing-2  0.20
  Test id input is  batch-drawing-3  0.21

Fill drawing: decimals with period
  Fill test id  batch-drawing-2  7.88
  Fill down  drawing-2
  Test id input is  batch-drawing-0  1
  Test id input is  batch-drawing-1  0,19
  Test id input is  batch-drawing-2  7.88
  Test id input is  batch-drawing-3  7.89

Fill drawing: no numbers
  Fill test id  batch-drawing-0  88foo
  Fill down  drawing-0
  Test id input is  batch-drawing-0  88foo
  Test id input is  batch-drawing-1  88foo
  Test id input is  batch-drawing-2  88foo
  Test id input is  batch-drawing-3  88foo

Applicant cannot mark attachments as construction time
  No such test id  batch-construction-0

Checking one signbox reveals password field
  Test id text is  sign-all  Valitse kaikki
  Element should not be visible  batch-password
  Click label by test id  batch-sign-0-label
  Wait until  Element should be visible  batch-password
  Test id text is  sign-all  Tyhjennä kaikki
  Scroll and click test id  sign-all
  Test id text is  sign-all  Valitse kaikki
  Wait until  Element should not be visible  batch-password
  Checkbox wrapper not selected by test id  batch-sign-0
  Checkbox wrapper not selected by test id  batch-sign-1
  Checkbox wrapper not selected by test id  batch-sign-2
  Checkbox wrapper not selected by test id  batch-sign-3
  Scroll and click test id  sign-all
  Test id text is  sign-all  Tyhjennä kaikki
  Wait until  Element should be visible  batch-password
  Checkbox wrapper selected by test id  batch-sign-0
  Checkbox wrapper selected by test id  batch-sign-1
  Checkbox wrapper selected by test id  batch-sign-2
  Checkbox wrapper selected by test id  batch-sign-3

Password checking
  Element should be visible  jquery=div.batch-password i.lupicon-flag
  Test id disabled  batch-ready
  Input text with jQuery  input#batch-password  foobar
  Wait until  Element should be visible  jquery=div.batch-password i.lupicon-warning
  Test id disabled  batch-ready
  Input text with jQuery  input#batch-password  pena
  Wait until  Element should be visible  jquery=div.batch-password i.lupicon-check
  Test id enabled  batch-ready

Remove the second file
  Scroll and click test id  batch-remove-1
  No such test id  batch-type-3
  Test id should contain  upload-progress-title  3 tiedostoa lisätty

Bad file
  Add bad

Make the first batch row to match an empty template
  Select type  0  Valtakirja
  Fill test id  batch-contents-0  My contents
  Click label by test id  batch-sign-0-label  # Not signed
  Sleep  0.5s     # wait for "My contents" to reach its observable

Pena is ready
  Scroll and click test id  batch-ready
  No such test id  batch-remove-0
  No such test id  batch-remove-1
  No such test id  batch-remove-2
  Wait until  Element should not be visible  jquery=div.batch-bad-files li strong
  No such test id  upload-progress-title
  Wait until  Element should not be visible  jquery=table.attachment-batch-table

Conversation shows three attachments
  Open side panel  conversation
  jQuery should match X times  div.is-comment.attachment  3
  Close side panel  conversation

Upload attachments results: still empty
  Empty attachment  paapiirustus.asemapiirros
  Empty attachment  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Empty attachment  paapiirustus.pohjapiirustus

Add paapiirustus.pohjapiirustus file
  Add attachment file  tr[data-test-type='paapiirustus.pohjapiirustus']  ${PNG_TESTFILE_PATH}  Doodle
  Wait test id visible  batch-ready
  Type is  0  Pohjapiirustus
  Contents is  0  Doodle
  Grouping is  0  Asuinkerrostalon tai rivitalon rakentaminen

Cancel batch
  Click visible test id  batch-cancel

Upload attachments results: signed
  jQuery should match X times  i[data-test-icon=signed-icon]  2

Upload attachments results: hakija.valtakirja
  jQuery should match X times  tr[data-test-type='hakija.valtakirja']  1
  Open attachment details  hakija.valtakirja
  Test id input is  attachment-contents-input  My contents
  Element text should be  test-attachment-file-name  ${PNG_TESTFILE_NAME}
  Autocomplete selection by test id contains  attachment-group-autocomplete  Osapuolet
  [Teardown]  Logout

# ------------------------------
# Sonja
# ------------------------------

Sonja logs in
  Sonja logs in
  Open application attachments

Sonja sees construction column
  Add to batch
  Wait test id visible  batch-construction-0-label
  Click label by test id  batch-construction-0-label
  Select type  0  Ote alueen peruskartasta
  No such test id  construction-all

Sonja adds and removes files
  Add to batch
  Test id text is  construction-all  Tyhjennä kaikki
  Scroll and click test id  construction-all
  Test id text is  construction-all  Valitse kaikki
  Scroll and click test id  construction-all
  Test id text is  construction-all  Tyhjennä kaikki
  Scroll and click test id  batch-remove-1

Sonja is readey
  Scroll and click test id  batch-ready
  Wait until  Element should not be visible  jquery=table.attachment-batch-table

New attachment is not visible when post verdict filter is off
  Click label by test id  postVerdict-filter-label
  Checkbox wrapper not selected by test id  postVerdict-filter
  Element should not be visible  jquery=tr[data-test-type='rakennuspaikka.ote_alueen_peruskartasta']

New attachment is a post-verdict attachment and automatically approved
  Click label by test id  postVerdict-filter-label
  Rollup approved  RAKENNUSPAIKAN LIITTEET

Check the attachment details just in case
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta
  Test id input is  attachment-contents-input  Ote alueen peruskartasta
  Element text should be  test-attachment-file-name  ${PNG_TESTFILE_NAME}
  Autocomplete selection by test id contains  attachment-group-autocomplete  Rakennuspaikka
  [Teardown]  Logout

# ------------------------------
# Luukas
# ------------------------------

Luukas logs in but cannot add attachments
  Luukas logs in
  Open application attachments
  No such test id  add-attachments-label
  [Teardown]  Logout

# ------------------------------
# Pena
# ------------------------------

Pena logs in
  Pena logs in
  Create application the fast way  Area 51  753-416-6-2  pientalo
  Open tab  attachments

No attachments required
  Wait test id visible  no-unfiltered-attachments-info

Upload 51 files
  FOR  ${i}  IN RANGE  51
    Add to batch
  END
  Wait test id visible  page-flip-text-1

Select and fill down type and contents
  Select type  0  CV
  Contents is  0  CV
  Grouping is  0  Osapuolet
  Fill down  type-0
  Fill down  contents-0

Fill down drawings
  Set drawing  0  1
  Fill down  drawing-0

Page flip is is visible
  Test id text is  page-flip-text-1  1/2
  Test id disabled  page-flip-previous-1
  Test id enabled  page-flip-next-1

Go to next page
  Drawing is  0  1
  Click by test id  page-flip-next-1
  Drawing is  0  51
  Test id text is  page-flip-text-1  2/2
  Test id disabled  page-flip-next-1
  Test id enabled  page-flip-previous-1

Visit another tab and come back, page is remembered
  Open tab  parties
  No such test id  batch-ready
  Open tab  attachments
  Only batch visible
  Drawing is  0  51
  Test id text is  page-flip-text-1  2/2
  Test id disabled  page-flip-next-1
  Test id enabled  page-flip-previous-1

Go to previous page and delete the first file
  Click by test id  page-flip-previous-1
  Drawing is  0  1
  Click by test id  batch-remove-0
  No such test id  page-flip-text-1
  Drawing is  0  2

Add one more file and submit the batch
  Add to batch
  Wait test id visible  page-flip-text-1
  Click by test id  page-flip-next-1
  Select type  0  Valokuva
  Scroll and click test id  batch-ready
  Sleep  1s

Fifty-one attachments were added
  # waiting that there are several lines rendered
  Wait until  Element should be visible  //tr[@data-test-type='osapuolet.cv'][45]
  Wait until  Xpath should match x times  //tr[@data-test-type='osapuolet.cv']  50
  Wait until  Xpath should match x times  //tr[@data-test-type='muut.valokuva']  1
  [Teardown]  Logout

No errors
  There are no frontend errors


*** Keywords ***

Empty attachment
  [Arguments]  ${type}
  Wait until  Element should be visible  jquery=tr[data-test-type='${type}'] label[data-test-id=add-attachment-file-label]

Add to batch
  [Arguments]  ${path}=${PNG_TESTFILE_PATH}  ${good}=True
  Execute Javascript  $("input[data-test-id=add-attachments-input]").css( "display", "block").toggleClass( "hidden", false )
  Choose file  jquery=input[data-test-id=add-attachments-input]  ${path}
  Execute Javascript  $("input[data-test-id=add-attachments-input]").css( "display", "none").toggleClass( "hidden", true )
  Run Keyword if  ${good} == True  Wait Until  Element should be visible  jquery=div.upload-progress--finished
  # Maximize browser window no longer works
  Set window size  1500  1000

Select type
  [Arguments]  ${index}  ${type}
  Select From Autocomplete  div.batch-autocomplete[data-test-id=batch-type-${index}]  ${type}

Type is
  [Arguments]  ${index}  ${type}
  Wait until  Element text should be  jquery=div.batch-autocomplete[data-test-id=batch-type-${index}] span.caption  ${type}

Set contents
  [Arguments]  ${index}  ${value}
  Fill test id  batch-contents-${index}  ${value}

Contents is
  [Arguments]  ${index}  ${value}
  Test id input is  batch-contents-${index}  ${value}

Set drawing
  [Arguments]  ${index}  ${value}
  Fill test id  batch-drawing-${index}  ${value}

Drawing is
  [Arguments]  ${index}  ${value}
  Test id input is  batch-drawing-${index}  ${value}

Select grouping
  [Arguments]  ${index}  ${grouping}
  Select from autocomplete  [data-test-id=batch-grouping-${index}]  ${grouping}

Deselect grouping
  [Arguments]  ${index}
  Scroll to test id  batch-grouping-${index}
  Click element  jquery=[data-test-id=batch-grouping-${index}] .tag-remove

Grouping is
  [Arguments]  ${index}  ${value}
  Wait Until  Autocomplete selection by test id contains  batch-grouping-${index}  ${value}

Grouping is general
  [Arguments]  ${index}
  Wait Until  Autocomplete selection by test id is empty  batch-grouping-${index}

Fill down
  [Arguments]  ${cell}
  ${icon-button}=  Set Variable  icon-button[data-test-id=fill-${cell}]
  Scroll to test id  batch-${cell}
  Execute Javascript  $("${icon-button}").css( "display", "block")
  Click button  jquery=${icon-button} button
  Execute Javascript  $("${icon-button}").css( "display", "none")

Open application attachments
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Add bad
  Element should not be visible  jquery=div.batch-bad-files
  Add to batch  ${XML_TESTFILE_PATH}  False
  Wait until  Element should be visible  jquery=div.batch-bad-files li strong
  Element should be visible  jquery=div.batch-bad-files i.lupicon-warning

Only batch visible
  Wait test id visible  batch-ready
  No such test id  add-attachment-templates
  Element should not be visible  jquery=div.attachment-operations
  No such test id  automatic-assignments-component
  No such test id  create-assignment-component
  Element should not be visible  jquery=filters
