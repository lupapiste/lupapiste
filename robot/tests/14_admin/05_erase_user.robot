*** Settings ***

Documentation   Admin erases user data
Resource        ../../common_resource.robot

*** Test Cases ***

Solita admin logs in and goes to 'users' page
  SolitaAdmin logs in
  Go to page  users
  Wait until  Element should be visible     xpath=//section[@id='users']

Solita admin finds Pena and erases his info
  Wait until     Element should be visible  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  pena
  Wait for jQuery
  Wait until     Element should be visible  xpath=//section[@id='users']//tr[@data-user-email='pena@example.com']//td/button[@data-op='erase']
  Wait until     Click element              xpath=//section[@id='users']//tr[@data-user-email='pena@example.com']//td/button[@data-op='erase']
  Confirm        dynamic-yes-no-confirm-dialog

Pena can no longer be recognized
  Wait until     Element should be visible  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  pena
  Wait for jQuery
  Page Should Not Contain Element  //section[@id='users']//tr[@data-user-email='pena@example.com']
  Logout

Pena can no longer log in
  Login fails  pena  pena

Pena cannot even reset his password
  Click Link  Oletko unohtanut salasanasi?
  Wait Until  Element Should Be Visible  reset
  Page Should Contain  Salasanan vaihtaminen
  Input text  email  pena@example.com
  Click enabled by test id  reset-send
  Wait Until  Page Should Contain  Antamaasi sähköpostiosoitetta ei löydy järjestelmästä.
