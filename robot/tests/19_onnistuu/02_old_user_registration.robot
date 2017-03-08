*** Settings ***

Documentation   Existing User signs company agreement
Resource        ../../common_resource.robot

*** Test Cases ***

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
  Click enabled by test id  register-company-continue

Name and person id should be filled in
  Test id text is  register-company-firstName  Teppo
  Test id text is  register-company-lastName   Nieminen
  Test id text is  register-company-email      teppo@example.com
  Element Should Not Be Visible  xpath=//input[@data-test-id='register-company-personId']

Fill in info
  Input text by test id  register-company-name        Peten saneeraus Oy
  Input text by test id  register-company-y           2341529-2
  Input text by test id  register-company-address1    Katukatu 2
  Input text by test id  register-company-zip         00002
  Input text by test id  register-company-po          Kunta
  Select From Test id  register-company-pop  Basware Oyj (BAWCFI22)
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

*** Keywords ***
Disabled input value is
  [Arguments]  ${dataTestId}  ${expected}
  Wait Until  Textfield Value Should Be  xpath=//input[@data-test-id='${dataTestId}']  ${expected}
  Element Should Be Disabled  xpath=//input[@data-test-id='${dataTestId}']
