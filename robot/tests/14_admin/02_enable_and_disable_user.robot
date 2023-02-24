*** Settings ***

Documentation   Admin edits authority admin users
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Solita admin logs in and goes to 'users' page
  SolitaAdmin logs in
  Go to page  users
  Wait until  Element should be visible     xpath=//section[@id='users']

Solita admin search Ronja and disables her access
  Wait until     Element should be visible  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  ronj
  Wait for jQuery
  Wait until     Element should be visible  xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='disable']
  Wait until     Click element              xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='disable']
  Confirm        dynamic-yes-no-confirm-dialog
  [Teardown]  Logout

Ronja tries to login but can't
  Login fails  ronja  sonja

Solita admin enables Ronjas account
  SolitaAdmin logs in
  Go to page  users
  Wait until     Element should be visible  xpath=//section[@id='users']
  Wait until     Element should be visible  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  ronj
  Wait for jQuery
  Wait until     Element should be visible  xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='enable']
  Wait until     Click element              xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/button[@data-op='enable']
  Confirm        dynamic-yes-no-confirm-dialog
  [Teardown]  Logout

Ronja can login again
  Login  ronja  sonja
  User should be logged in  Ronja Sibbo
  Go to  ${LOGIN_URL}
  [Teardown]  Logout
