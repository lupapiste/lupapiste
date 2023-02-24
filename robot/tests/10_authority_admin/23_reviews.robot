*** Settings ***

Documentation   Review minutes configuration
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin opens reviews page
  Sipoo logs in
  Go to page  reviews

Contact edit is visible
  Wait test id visible  contact-edit
  No such test id  contact-preview

Fill some contact information
  Edit markup  contact  Lupapisteentie 23
  Check markup preview  contact  Lupapisteentie 23

Rectification info not selected
  Toggle not selected  rectification-enabled
  No such test id  rectification-info-edit-tab

Enable rectification and fill info
  Toggle toggle  rectification-enabled
  Edit markup  rectification-info  Good luck!
  Check markup preview  rectification-info  Good luck!

No Pate, no automatic construction started toggle
  No such toggle  automatic-construction-started

Toggle fetch parameters
  Checkbox should be selected  automatic-review-fetch-enabled
  Checkbox should not be selected  only-use-inspection-from-backend
  Click element  jquery=label[for=automatic-review-fetch-enabled]
  Click element  jquery=label[for=only-use-inspection-from-backend]

Configuration survives reload
  Reload page
  Check markup preview  contact  Lupapisteentie 23
  Check markup preview  rectification-info  Good luck!
  Checkbox should not be selected  automatic-review-fetch-enabled
  Checkbox should be selected  only-use-inspection-from-backend

Toggle hides rectification info again
  Toggle toggle  rectification-enabled
  No such test id  rectification-info-edit-tab
  [Teardown]  Logout


*** Keywords ***

Edit markup
  [Arguments]  ${tid}  ${text}
  Wait test id visible  ${tid}-edit-tab
  ${is_active}=  Run Keyword And Return Status  Element should be visible  xpath=//*[@data-test-id="${tid}-edit-tab" and contains(@class, 'pate-tab--active')]
  Run Keyword Unless  ${is_active}  Click link  xpath=//*[@data-test-id="${tid}-edit-tab"]/a
  Fill test id  ${tid}-edit  ${text}

Check markup preview
  [Arguments]  ${tid}  ${text}
  Wait test id visible  ${tid}-preview-tab
  Click link  xpath=//*[@data-test-id="${tid}-preview-tab"]/a
  Test id should contain  ${tid}-preview  ${text}
