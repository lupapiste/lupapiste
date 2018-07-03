*** Settings ***

Documentation   Admin creates finacial users
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Admin logs in and goes to 'users' page
  SolitaAdmin logs in
  Go to page  users
  Wait until  Element should be visible  xpath=//section[@id='users']

Admin filters financial users
  Input text by test id  users-list-input-search  ARA
  Set Suite Variable  ${userRowXpath}  //div[contains(@class, 'users-table')]//table/tbody/tr
  Wait Until  Element Should Be Visible  ${userRowXpath}
  ${userCount} =  Get Matching Xpath Count  ${userRowXpath}
  # There is one existing financial user from minimal
  User count is  1

Admin creates new financial user
  Create financial user  ARA-käsittelijä2  massi.mies@mail.com
  Wait until  Element Should Contain  //div[contains(@class, 'users-table')]//table/tbody/tr[@data-user-email="massi.mies@mail.com"]/td[3]  ARA-käsittelijä
  User count is  2

Admin resets password for financial authority
  Element should be visible  xpath=//section[@id='users']//tr[@data-user-email='massi.mies@mail.com']//td/button[@data-op='resetPassword']
  Click element  xpath=//section[@id='users']//tr[@data-user-email='massi.mies@mail.com']//td/button[@data-op='resetPassword']
  Confirm  dynamic-yes-no-confirm-dialog
  Confirm  dynamic-ok-confirm-dialog

Financial authority gets mail to reset password
  Open last email
  Wait until     Element Text Should Be  xpath=//dd[@data-test-id='to']  massi.mies@mail.com
  Click element  xpath=//a
  Wait until     Element should be visible  xpath=//section[@id='setpw']


*** Keywords ***

User count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  ${userRowXpath}  ${amount}

Create financial user
  [Arguments]  ${firstName}  ${email}

  Click enabled by test id  add-financial-user
  Wait until  Element should be visible  //label[@for='admin.financial.add.name']

  Input text  admin.financial.add.name  ${firstName}
  Input text  admin.financial.add.email  ${email}

  Click enabled by test id  add-financial-user-continue
  Wait test id visible  add-financial-user-ok
  # has link that can be given to Financial authority
  Wait until  Element should contain  xpath=//div[@id='add-financial-user-to-organization-dialog']//a[1]  setpw
  Click enabled by test id  add-financial-user-ok
