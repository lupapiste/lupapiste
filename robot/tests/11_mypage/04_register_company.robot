*** Settings ***

Documentation   User changes account details
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

## For some strange reason, firstName and lastName fields are left blank.

Mikko goes to own page
  Mikko logs in
  Open My Page
  Wait for Page to Load  Mikko  Intonen

Mikko has no company info
  Element should not be visible  //div[@data-test-id="mypage-company-accordion"]

Mikko opens company register accordion
  Wait Until  Click Element  xpath=//div[@data-test-id="mypage-register-company-accordion"]//button

Mikko start regitration process
  Wait Until  Click Element  xpath=//a[@data-test-id='logged-user-register-company-start']

Company registration process starts
  Wait Until  Element Should Be Visible  register-company-account-type

Mikko logs out
  Logout

Veikko goes to own page
  Veikko logs in
  Open My Page
  # Wait for Page to Load  Veikko  Viranomainen

Veikko has no company info
  Wait Until  Element should not be visible  //div[@data-test-id='mypage-company-accordion']

There is no company registration accordion available
  Wait Until  Element should not be visible  //div[@data-test-id='mypage-register-company-accordion']//button
  Logout

*** Keywords ***

Wait for Page to Load
  [Arguments]  ${firstName}  ${lastName}
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}
