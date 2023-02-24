*** Settings ***

Documentation   Authority admin adds and edits automatic assignments
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***


Authority admin goes to the tasks page
  Sipoo logs in
  Go to page  assignments

Authority admin adds new handler role
  Click enabled by test id  add-handler-role
  Input text by test id  edit-role-2-fi  Käsittelijä robotti
  Input text by test id  edit-role-2-en  Handler robot
  Input text by test id  edit-role-2-sv  Handläggare robot
  Wait until  Positive indicator should be visible
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-fi']  Käsittelijä robotti
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-en']  Handler robot
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-sv']  Handläggare robot

Authority admin should see automatic assignments only when assignments are enabled
  Toggle selected  assignments-enabled
  Wait until  Element should be visible   automatic-assignments
  Toggle toggle  assignments-enabled
  Toggle not selected  assignments-enabled
  Wait until  Element should not be visible   automatic-assignments
  Toggle toggle  assignments-enabled

Organization already have two automatic assignments, destroy them
  Wait Until  Xpath Should Match X Times  //div[@id='automatic-assignments']//table/tbody/tr  2
  Click element  //div[@id='automatic-assignments']//table/tbody/tr//button[@data-test-id='delete-filter-0']
  Confirm yes no dialog
  Click element  //div[@id='automatic-assignments']//table/tbody/tr//button[@data-test-id='delete-filter-0']
  Confirm yes no dialog

Authority adds automatic assignment
  Click enabled by test id  automatic-add
  Click by test id  activate-attachment-types-filter
  Select from cljs-autocomplete by test id  automatic-attachment-types  Aitapiirustus
  Click by test id  activate-handler-role-id-filter
  Select From cljs-autocomplete by test id  automatic-handler-role-id  Käsittelijä robotti
  Test id disabled  save-filter
  Input text  filter-edit-input  Description
  Click enabled by test id  save-filter
  Wait Until  Xpath Should Match X Times  //div[@id='automatic-assignments']//table/tbody/tr  1
  Click Element  partial link=Description
  Wait until  Element text should be  xpath=//div[@data-test-id=":automatic.attachment-types.title"]//div[2]  Aitapiirustus

Authority should be able to edit trigger...
  Click by test id  edit-filter-0

...attachments
  Select from cljs-autocomplete by test id  automatic-attachment-types  Johtokartta
  Click element  xpath=//div[@id='automatic-assignments']//span[@class='selected' and text()="Aitapiirustus"]/button

...description
  Input text  filter-edit-input  Updated description

... enable inspections
  Click by test id  activate-reviews-filter
  Input text by test id  automatic-reviews  rev1\nrev2

..set receiver
  Select from cljs-autocomplete by test id  autocomplete-handler-role-id  Käsittelijä robotti
  # Done
  Click enabled by test id  save-filter

Edited values are
  Click link  partial link=Updated description
  Check row field  1  automatic.handler-role-id.title  Käsittelijä robotti
  Check row field  1  automatic.attachment-types.title  Johtokartta
  Check row field  1  automatic.reviews.title  rev1, rev2
  Check row field  1  automatic.targets  Käsittelijä robotti

Authority should be able to delete trigger
  Click element  //div[@id='automatic-assignments']//table/tbody/tr//button[@data-test-id='delete-filter-0']
  Confirm yes no dialog
  Wait until  Positive indicator should be visible
  Wait Until  Xpath Should Match X Times  //div[@id='automatic-assignments']//table/tbody/tr  0

** Keywords **

Check row field
  [Arguments]  ${idx}  ${attr}  ${value}
  ${selector}=  Set Variable  (//div[@id='automatic-assignments']//tr[contains(@class,'details')])[${idx}]//div[@data-test-id=":${attr}"]
  Element should be visible  xpath=${selector}
  Element text should be  xpath=${selector}//div[@data-test-id='row-value']  ${value}
