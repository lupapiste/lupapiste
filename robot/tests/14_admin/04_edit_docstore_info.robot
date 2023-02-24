*** Settings ***

Documentation  Admin edits organization
Suite Teardown  Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       ./keywords.robot

*** Test Cases ***

Solita admin goes to organizations page
  SolitaAdmin logs in
  Go to page  organizations
  Wait test id visible  organization-search-term
  Test id text is  organization-result-count  ${EMPTY}
  Admin shows all organizations

Admin searchs just 186-R
  Fill test id  organization-search-term  186-R
  Test id text is  organization-result-count  1 organisaatio.

Admin edits organization with id 186-R
  Scroll and Click test id  edit-organization-186-R
  Wait until  Element should be visible  xpath=//section[@id="organization"]//input[@id="docstore-enabled"]

No docstore boxes selected, no pricing, no description, save button enabled
  Checkbox should not be selected  docstore-enabled
  Checkbox should not be selected  docterminal-enabled
  Checkbox should not be selected  docdepartmental-enabled
  No such test id  docstore-enabled-settings
  No such test id  docstore-description-table
  Test id enabled  save-docstore-info

Admin enables terminal
  Select checkbox  docterminal-enabled
  No such test id  docstore-enabled-settings
  Wait test id visible  docstore-description-table
  Test id enabled  save-docstore-info

Admin enables departmental and disables terminal
  Select checkbox  docdepartmental-enabled
  Unselect checkbox  docterminal-enabled
  No such test id  docstore-enabled-settings
  Wait test id visible  docstore-description-table
  Test id enabled  save-docstore-info

Admin disables departmental
  Unselect checkbox  docdepartmental-enabled
  No such test id  docstore-enabled-settings
  No such test id  docstore-description-table
  Test id enabled  save-docstore-info

Admin enables docstore, and new options appear
  Select Checkbox  xpath=//section[@id="organization"]//input[@id="docstore-enabled"]
  Wait until  Element should be visible  xpath=//section[@id="organization"]//div[@data-test-id="docstore-enabled-settings"]
  Test id disabled  save-docstore-info

Admin attempts to enter a negative price
  Input price  -5,99
  Pricing error

Admin enters a positive price
  Input price  5,99
  Pricing OK

Admin saves the changes
  Wait until  Element should be enabled  xpath=//section[@id="organization"]//button[@data-test-id="save-docstore-info"]
  Click by test id  save-docstore-info
  Wait Until  Positive indicator should be visible

Admin enters negative fee
  Input fee  -1
  Pricing error

Admin enters proper fee
  Input fee  1
  Pricing OK

Admin enters too large fee
  Input fee  10
  Pricing error

Nonsense price and fee
  Input price  PRICE
  Input fee  FEE
  Pricing error

Proper price and fee
  Input price  12.00
  Input fee    6
  Pricing OK

Admin saves the changes again
  Click enabled by test id  save-docstore-info
  Wait Until  Positive indicator should be visible

Admin reloads page
  Reload page
  Wait until  Textfield value should be  docstore-price  12,00
  Wait until  Textfield value should be  docstore-fee  6,00
  Checkbox should be selected  docstore-enabled
  Checkbox should not be selected  docterminal-enabled
  Checkbox should not be selected  docdepartmental-enabled

Admin enters a description of the organization
  Input text by test id  docstore-desc-fi  Organisaatiokuvausteksi
  Input text by test id  docstore-desc-sv  Organisation berättelse
  Input text by test id  docstore-desc-en  Organization description

Admin changes pricing details but disabled store
  Input price  999
  Input fee  bad
  Pricing error
  Unselect checkbox  docstore-enabled
  Pricing OK

Admin enables terminal and departmental and saves info
  Select checkbox  docterminal-enabled
  Select checkbox  docdepartmental-enabled
  Click by test id  save-docstore-info
  Wait Until  Positive indicator should be visible

Reload and check
  Reload page
  Checkbox should not be selected  docstore-enabled
  Checkbox should be selected  docterminal-enabled
  Checkbox should be selected  docdepartmental-enabled
  Test id input is  docstore-desc-fi  Organisaatiokuvausteksi
  Test id input is  docstore-desc-sv  Organisation berättelse
  Test id input is  docstore-desc-en  Organization description

Enable store and note that hidden pricing has not been sent
  No such test id  docstore-enabled-settings
  Select checkbox  docstore-enabled
  Wait until  Textfield value should be  docstore-price  12,00
  Wait until  Textfield value should be  docstore-fee  6,00
  [Teardown]  Logout


*** Keywords ***

Input price
  [Arguments]  ${price}
  Input text  docstore-price  ${price}

Input fee
  [Arguments]  ${fee}
  Input text  docstore-fee  ${fee}

Pricing OK
  Wait until element is not visible  jquery=input#docstore-price.warning
  Wait until element is not visible  jquery=input#docstore-fee.warning
  Test id enabled  save-docstore-info

Pricing error
  Wait until element is visible  jquery=input#docstore-price.warning
  Wait until element is visible  jquery=input#docstore-fee.warning
  Test id disabled  save-docstore-info
