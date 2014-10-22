*** Settings ***

Documentation   User signs company agreement
Suite setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

Bob decides to register his company, but then cancels his mind
  Wait and click  register-button
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Enabled   xpath=//*[@data-test-id='register-company-cancel']
  Click Element  xpath=//*[@data-test-id='register-company-cancel']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-start']

Bob decides to register his company after all, but still chikens out
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-submit']
  Input text by test id  register-company-name        Peten rakennus Oy
  Input text by test id  register-company-y           FI2341528-4
  Input text by test id  register-company-firstName   Pete
  Input text by test id  register-company-lastName    Puuha
  Input text by test id  register-company-email       puuha.pete@pete-rakennus.fi
  Input text by test id  register-company-ovt         0037123456710007
  Input text by test id  register-company-pop         003776543212
  Click enabled by test id  register-company-submit
  Wait Until  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-cancel']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-start-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel-sign']
  Click Element  xpath=//*[@data-test-id='register-company-cancel-sign']
  Wait until  Element should be visible  register-button

Bob decides to register his company after all, and this time he means it
  Wait and click  register-button
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-submit']
  Input text by test id  register-company-name        Peten rakennus Oy
  Input text by test id  register-company-y           FI2341528-4
  Input text by test id  register-company-firstName   Pete
  Input text by test id  register-company-lastName    Puuha
  Input text by test id  register-company-email       puuha.pete@pete-rakennus.fi
  Input text by test id  register-company-ovt         0037123456710007
  Input text by test id  register-company-pop         003776543212
  Click enabled by test id  register-company-submit
  Wait Until  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-cancel']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-start-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel-sign']
  Click Element  xpath=//*[@data-test-id='register-company-start-sign']

  Wait until  Element should be visible  xpath=//span[@data-test-id='onnistuu-dummy-status']
  Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  ready
  Click enabled by test id  onnistuu-dummy-success

Registrations succeeds, user gets email
  Wait until  Element should be visible  xpath=//section[@id='register-company-success']
  Open last email
  Wait Until  Page Should Contain  puuha.pete@pete-rakennus.fi

Second link in email should lead to password reset
  Click Element  xpath=(//a)[2]
  Wait Until  Element should be visible  new-company-user
  Wait Until  Page should contain  FI2341528-4
  Page should contain  puuha.pete@pete-rakennus.fi
  Fill in new password  new-company-user  company123

Login with the new password
  Login  puuha.pete@pete-rakennus.fi  company123
  User should be logged in  Pete Puuha
  [Teardown]  logout

