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

Add good one and cancel
  Add to batch
  Test id text is  upload-progress-title  1 tiedosto lisätty
  Scroll and click test id  batch-cancel
  No such test id  upload-progress-title
  Wait until  Element should not be visible  jquery=table.attachment-batch-table


Batch of four files
  No such test id  upload-progress-title
  Add to batch
  Test id text is  upload-progress-title  1 tiedosto lisätty
  No such test id  sign-all
  Add to batch
  Test id text is  upload-progress-title  2 tiedostoa lisätty
  Wait test id visible  sign-all
  Add to batch
  Test id text is  upload-progress-title  3 tiedostoa lisätty
  Add to batch
  Test id text is  upload-progress-title  4 tiedostoa lisätty
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

Contents have not changed for non-empties
  Contents is  0  CV
  Contents is  1  Paloturvallisuuden perustietolomake
  Contents is  2  CV
  Contents is  3  CV
  Test id enabled  batch-ready

Fill contents
  Fill down  contents-1
  Contents is  0  CV
  Contents is  1  Paloturvallisuuden perustietolomake
  Contents is  2  Paloturvallisuuden perustietolomake
  Contents is  3  Paloturvallisuuden perustietolomake

Fill grouping
  Select grouping  2  Yleisesti hankkeeseen
  Fill down  grouping-2
  Grouping is  0  Osapuolet
  Grouping is  1  Osapuolet
  Grouping is  2  Yleisesti hankkeeseen
  Grouping is  3  Yleisesti hankkeeseen

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
  Checkbox wrapper not selected by test id  batch-sign-0-input
  Checkbox wrapper not selected by test id  batch-sign-1-input
  Checkbox wrapper not selected by test id  batch-sign-2-input
  Checkbox wrapper not selected by test id  batch-sign-3-input
  Scroll and click test id  sign-all
  Test id text is  sign-all  Tyhjennä kaikki
  Wait until  Element should be visible  batch-password
  Checkbox wrapper selected by test id  batch-sign-0-input
  Checkbox wrapper selected by test id  batch-sign-1-input
  Checkbox wrapper selected by test id  batch-sign-2-input
  Checkbox wrapper selected by test id  batch-sign-3-input

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
  Test id text is  upload-progress-title  3 tiedostoa lisätty

Bad file
  Add bad

Make the first batch row to match an empty template
  Select type  0  Valtakirja
  Fill test id  batch-contents-0  My contents
  Click label by test id  batch-sign-0-label  # Not signed

Pena is ready
  Scroll and click test id  batch-ready
  No such test id  batch-remove-0
  No such test id  batch-remove-1
  No such test id  batch-remove-2
  Wait until  Element should not be visible  jquery=div.batch-bad-files li strong
  No such test id  upload-progress-title
  Wait until  Element should not be visible  jquery=table.attachment-batch-table

Upload attachments results: still empty
  Empty attachment  paapiirustus.asemapiirros
  Empty attachment  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Empty attachment  paapiirustus.pohjapiirustus

Upload attachments results: signed
  jQuery should match X times  i[data-test-icon=signed-icon]  2

Upload attachments results: hakija.valtakirja
  jQuery should match X times  tr[data-test-type='hakija.valtakirja']  1
  Open attachment details  hakija.valtakirja
  Test id input is  attachment-contents-input  My contents
  Element text should be  test-attachment-file-name  ${PNG_TESTFILE_NAME}
  Test id select is  attachment-operation-select  parties
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

New attachment is not yet visible
  Element should not be visible  jquery=tr[data-test-type='rakennuspaikka.ote_alueen_peruskartasta']

New attachment is a post-verdict attachment and automatically approved
  Click label by test id  postVerdict-filter-label
  Rollup approved  Rakennuspaikan liitteet

Check the attachment details just in case
  Open attachment details  rakennuspaikka.ote_alueen_peruskartasta
  Test id input is  attachment-contents-input  Ote alueen peruskartasta
  Element text should be  test-attachment-file-name  ${PNG_TESTFILE_NAME}
  Test id select is  attachment-operation-select  Rakennuspaikka
  [Teardown]  Logout

# ------------------------------
# Luukas
# ------------------------------

Luukas logs in but cannot add attachments
  Luukas logs in
  Open application attachments
  No such test id  add-attachments-label
  [Teardown]  Logout


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

Select type
  [Arguments]  ${index}  ${type}
  Select From Autocomplete  div.batch-autocomplete[data-test-id=batch-type-${index}]  ${type}

Type is
  [Arguments]  ${index}  ${type}
  Wait until  Element text should be  jquery=div.batch-autocomplete[data-test-id=batch-type-${index}] span.caption  ${type}

Contents is
  [Arguments]  ${index}  ${value}
  Test id input is  batch-contents-${index}  ${value}

Select grouping
  [Arguments]  ${index}  ${grouping}
  Select from list by label  jquery=[data-test-id=batch-grouping-${index}] select  ${grouping}

Grouping is
  [Arguments]  ${index}  ${value}
  Wait Until  List selection should be  jquery=div[data-test-id=batch-grouping-${index}] select[data-test-id=attachment-operation-select]  ${value}

Fill down
  [Arguments]  ${cell}
  ${icon-button}=  Set Variable  icon-button[data-test-id=fill-${cell}]
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
