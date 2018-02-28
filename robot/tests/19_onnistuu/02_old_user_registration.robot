*** Settings ***

Documentation   Existing User signs company agreement
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

# ---------------------
# Teppo
# ---------------------
Teppo opens own details
  Teppo logs in
  Click Element  user-name
  Open accordion by test id  mypage-personal-info-accordion
  Open accordion by test id  mypage-register-company-accordion

There is no company info
  Element should not be visible  //div[@data-test-id='mypage-company-accordion']

Start registration
  Click by test id  logged-user-register-company-start
  Test id disabled  register-company-continue
  Click by test id  account-type-account5
  Company yearly billing is selected
  Click enabled by test id  register-company-continue

Userinfo is filled
  Userinfo is  Teppo Nieminen, teppo@example.com

Fill in info
  Input text by test id  register-company-name        Peten saneeraus Oy
  Input text by test id  register-company-y           2341529-2
  Input text by test id  register-company-address1    Katukatu 2
  Input text by test id  register-company-zip         00002
  Input text by test id  register-company-po          Kunta
  Select From Test id  register-company-pop  Basware Oyj (BAWCFI22)
  Click enabled by test id  register-company-continue

Summary page is opened
  Wait until  Element should be visible  xpath=//div[contains(@class, 'register-company-summary')]
  Element should contain  xpath=//strong[@data-test-id='summary-account-text']  Yritystili 5
  Click enabled by test id  register-company-continue

Accept terms
  Wait Until  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-sign']
  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel']
  Toggle toggle  register-company-agree
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel']

Sign
  Click Element  xpath=//*[@data-test-id='register-company-sign']
  Wait until  Element should be visible  xpath=//span[@data-test-id='onnistuu-dummy-status']
  Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  ready
  Page Should Contain  210281-0002
  Click enabled by test id  onnistuu-dummy-success
  Wait until  Page Should Contain  Rekisteröinti onnistui

Company data is set
  Click link  Yrityksen tiedot
  Wait until  Element Should Be Visible  //div[@id='company-content']//button[@data-test-id='company-details-save']
  Element Should Be Disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']
  Disabled input value is  edit-company-name  Peten saneeraus Oy
  Disabled input value is  edit-company-y     2341529-2
  [Teardown]  Logout

# ---------------------
# Pena
# ---------------------
Pena starts registering company without logging in
  Start registering  account30

Pena fills info
  Input text by test id  register-company-name        Pena & Co.
  Input text by test id  register-company-y           2341529
  Input text by test id  register-company-address1    Katukatu 8
  Input text by test id  register-company-zip         0008
  Input text by test id  register-company-po          City

Warnings are visible
  Wait test id visible  register-company-y-warning
  Wait test id visible  register-company-zip-warning

Login fields become visible when Pena enters his email
  Initiate login  pena@example.com

Pena logs in
  Finalize login  pena  pena

The page reflects login, but old data is retained
  User should be logged in  Pena Panaani
  Userinfo is  Pena Panaani, pena@example.com
  Test id input is  register-company-name        Pena & Co.
  Test id input is  register-company-y           2341529
  Test id input is  register-company-address1    Katukatu 8
  Test id input is  register-company-zip         0008
  Test id input is  register-company-po          City
  Wait test id visible  register-company-y-warning
  Wait test id visible  register-company-zip-warning
  [Teardown]  Logout

# ---------------------
# Authority
# ---------------------
Sonja starts registering company
  Start registering  account5
  Initiate login  sonja.sibbo@sipoo.fi

Sonja is ultimately redirected to the Applications page
  Finalize login  sonja  sonja
  Confirm ok dialog
  User should be logged in  Sonja Sibbo
  Wait test id visible  own-applications
  [Teardown]  Logout

# ---------------------
# Company user
# ---------------------
Kaino starts registering company
  Start registering  account15
  Initiate login  kaino@solita.fi

Kaino is ultimately redirected to the Applications page
  Finalize login  kaino@solita.fi  kaino123
  Confirm ok dialog
  User should be logged in  Kaino Solita
  Wait test id visible  own-applications
  [Teardown]  Logout



*** Keywords ***

Start registering
  [Arguments]  ${account-type}  ${billing-type}=yearly
  Wait and click  register-button
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Click by test id  ${billing-type}-billing
  Click by test id  account-type-${account-type}
  Click by test id  register-company-continue

Disabled input value is
  [Arguments]  ${dataTestId}  ${expected}
  Wait Until  Textfield Value Should Be  xpath=//input[@data-test-id='${dataTestId}']  ${expected}
  Element Should Be Disabled  xpath=//input[@data-test-id='${dataTestId}']

Userinfo is
  [Arguments]  ${userinfo}
  No such test id  register-company-firstName
  No such test id  register-company-lastName
  No such test id  register-company-email
  No such test id  register-company-personId
  No such test id  register-company-language
  Test id text is  register-company-userinfo  ${userinfo}

Initiate login
  [Arguments]  ${email}
  No such test id  login-button
  Input text by test id  register-company-email  ${email}
  Wait test id visible  register-company-email-warning
  Test id input is  login-username  ${email}
  Test id input is  login-password  ${EMPTY}
  Test id disabled  login-button

Finalize login
  [Arguments]  ${username}  ${password}
  Input text by test id  login-username  ${username}
  Input text by test id  login-password  ${password}
  Click by test id  login-button
