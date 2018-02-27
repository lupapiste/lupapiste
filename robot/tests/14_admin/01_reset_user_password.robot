*** Settings ***

Documentation   Admin edits authority admin users
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Solita admin logs in and goes to 'users' page
  SolitaAdmin logs in
  Go to page  users
  Wait until  Element should be visible     xpath=//section[@id='users']

Solita admin search Ronja and resets her password
  Wait until     Element should be visible    xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  ronj
  Wait Until     Page Should Contain Element  xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']
  Wait until     Element should be visible    xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='resetPassword']
  Wait until     Click element                xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='resetPassword']
  Confirm  dynamic-yes-no-confirm-dialog
  Confirm  dynamic-ok-confirm-dialog

Ronja receives an email
  Open last email
  Wait until     Element Text Should Be  xpath=//dd[@data-test-id='to']  Ronja Sibbo <ronja.sibbo@sipoo.fi>
  Click element  xpath=//a
  Wait until     Element should be visible  xpath=//section[@id='setpw']
