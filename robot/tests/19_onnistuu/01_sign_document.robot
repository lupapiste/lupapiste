*** Settings ***

Documentation   User signs company agreement
Suite setup     Apply minimal fixture now
Suite teardown  Logout
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
  
Bod decides to register his company after all, but still chikens out
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-submit']
  Input text by test id  register-company-name        Peten rakennus Oy
  Input text by test id  register-company-y           1234567-8
  Input text by test id  register-company-firstName   Pete
  Input text by test id  register-company-lastName    Puuha  
  Input text by test id  register-company-email       puuha.pete@pete-rakennus.fi
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-submit']
  Click Element  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-cancel']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-start-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel-sign']
  Click Element  xpath=//*[@data-test-id='register-company-cancel-sign']
  Wait until  Element should be visible  register-button

Bod decides to register his company after all, and this time he means it
  Wait and click  register-button
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-submit']
  Input text by test id  register-company-name        Peten rakennus Oy
  Input text by test id  register-company-y           1234567-8
  Input text by test id  register-company-firstName   Pete
  Input text by test id  register-company-lastName    Puuha  
  Input text by test id  register-company-email       puuha.pete@pete-rakennus.fi
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-submit']
  Click Element  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-submit']
  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-cancel']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-start-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel-sign']
  Click Element  xpath=//*[@data-test-id='register-company-start-sign']
  # In DEV only!
  # Wait until  Element should be visible  xpath=//span[@data-test-id='onnistuu-dummy-status']
  # Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  ready
  # Click element  xpath=//a[@data-test-id='onnistuu-dummy-fetch-doc']
  # Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  success
  # Click element  xpath=//a[@data-test-id='onnistuu-dummy-success']
  # Wait until  Element should be visible  xpath=//section[@id='register-company-success']
