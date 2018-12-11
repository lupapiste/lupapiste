*** Settings ***

Documentation   A new user signs company agreement
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

Setup
  Go to  ${LAST EMAIL URL}
  Go to  ${LOGIN URL}

Bob decides to register his company (he will change his mind later)
  Wait and click  register-button
  Wait and click  xpath=//*[@data-test-id='register-company-start']

There are accounts available
  Xpath Should Match X Times  //div[contains(@class, 'account-type-boxes')]//div[contains(@class, 'account-type-box')]  3

Bob sees that yearly subscription is selected by default
  Company yearly billing is selected
  # Each has 'save5' ribbons LPK-3430
  Xpath Should Match X Times  //div[contains(@class, 'account-type-boxes')]//span[@data-test-id='save-5-ribbon']  3
  # Each have discounted price visible LPK-3430
  Xpath Should Match X Times  //div[contains(@class, 'account-type-boxes')]//div[@data-test-id='normal-yearly-price']  3

Also monthly billing is an option
  Test id visible  monthly-billing

Bob selects account15 with yearly billing to continue
  Test id disabled  register-company-continue
  Select account type  account15
  Click by test id  register-company-continue

Bob checks that input validation works
  Validate input  register-company-y  foobar  2341528-4
  Validate input  register-company-zip  22  22222
  Validate input  register-company-personId  foobar  290735-9501
  Validate input  register-company-email  bob  bob@example.org

Existing user email cannot be used
  Validate input  register-company-email  pena@example.com  bob@example.org

Bob sees that he is entitled to discount in summary page
  # .. after he enters all required fields :D
  Input text by test id  register-company-name        Bobin rakennus Oy
  Input text by test id  register-company-firstName   Bob
  Input text by test id  register-company-lastName    Dylan
  Input text by test id  register-company-address1    Katukatu
  Input text by test id  register-company-po          Kunta
  Click by test id  register-company-continue
  # Ok, summary page now:
  Wait until  Element should be visible  xpath=//div[contains(@class, 'register-company-summary')]
  Element should contain  xpath=//strong[@data-test-id='summary-account-text']  Yritystili 15
  Element should be visible  xpath=//*[@data-test-id='reduction-price']

Bob changes his mind
  Click by test id  register-company-cancel
  Click by test id  register-company-cancel
  Test id enabled  register-company-continue
  Click by test id  register-company-cancel
  Test id enabled  register-company-continue
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-start']
  Wait and click  xpath=//*[@data-test-id='register-company-start']

Bob decides to register his company after all, but still chickens out
  Register wizard until signing
  # Now at sign page
  Click by test id  register-company-cancel
  # Now at fill page
  Click by test id  register-company-cancel
  Test id select is  register-company-language  fi
  Click by test id  register-company-cancel
  Account type selected  account5
  Click by test id  register-company-cancel
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-start']

Bob's first attempt for proper registration fails during signing
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Account type selected  account5
  Register wizard until signing
  Click Element  xpath=//*[@data-test-id='register-company-sign']
  Click by test id  onnistuu-dummy-fail
  Wait until  Element should be visible  register-company-fail
  Click link  jquery=a.logo
  Wait and click  register-button

Bob decides to register his company after all, and this time he means it
  Wait and click  xpath=//*[@data-test-id='register-company-start']
  Account type not selected
  Register wizard until signing  sv
  Click Element  xpath=//*[@data-test-id='register-company-sign']

  Wait until  Element should be visible  xpath=//span[@data-test-id='onnistuu-dummy-status']
  Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  ready
  Page Should Contain  131052-308T
  Click enabled by test id  onnistuu-dummy-success

Registrations succeeds, user gets email
  Wait until  Element should be visible  xpath=//section[@id='register-company-success']
  Open all latest emails
  Wait Until  Page Should Contain  puuha.pete@pete-rakennus.fi
  Page Should Contain  new-company-user
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='subject']  Lupapiste: Inbjudan att administrera Företagskonto i Lupapiste

Second link in email should lead to password reset
  Click Element  xpath=(//a[contains(., 'new-company-user')])
  Wait Until  Element should be visible  new-company-user
  Wait Until  Page should contain  2341528-4
  Page should contain  puuha.pete@pete-rakennus.fi

Password messages and warnings
  No such test id  password1-warning
  No such test id  password1-message
  No such test id  password2-warning
  Input text by test id  password1  bad
  Wait test id visible  password1-warning
  Input text by test id  password1  1234567
  Wait test id visible  password1-warning
  Input text by test id  password1  12345678
  No such test id  password1-warning
  Wait test id visible  password1-message
  Input text by test id  password2  different
  Wait test id visible  password2-warning
  Input text by test id  password2  12345678
  No such test id  password2-warning

Finally fill in proper password
  Fill in new company password  new-company-user  company123

Login with the new password
  Login  puuha.pete@pete-rakennus.fi  company123
  User should be logged in  Pete Puuha
  Confirm notification dialog
  Language is  SV

Company details include company name, identifier and PDF link
  Open My Page
  Open accordion by test id  mypage-company-accordion
  Wait Until  Element text should be  xpath=//span[@data-test-id='my-company-name']  Peten rakennus Oy
  Wait Until  Element text should be  xpath=//span[@data-test-id='my-company-id']  2341528-4
  Page should contain  /dev/dummy-onnistuu/doc/

Company info page has the registered information
  Click by test id  company-edit-info
  Test id select text is  company-account-select  Företagskonto 5 (69 €/månad)
  Test id input is  edit-company-name        Peten rakennus Oy
  Test id input is  edit-company-y           2341528-4
  Test id input is  edit-company-address1    Katukatu
  Test id input is  edit-company-zip         00001
  Test id input is  edit-company-po          Kunta
  Test id input is  edit-company-netbill     yinhang
  List selection should be  jquery=div[data-test-id=company-pop] select  Basware Oyj (BAWCFI22)
  [Teardown]  logout

*** Keywords ***

Select account type
  [Arguments]  ${type}
  Wait Until  Click Element  xpath=//*[@data-test-id='account-type-${type}']

Account type selected
  [Arguments]  ${type}
  Wait Until  Element should be visible  jquery=div.account-type-box[data-test-id=account-type-${type}].selected

Account type not selected
  Wait Until  Element should not be visible  jquery=div.account-type-box.selected

Validate input
  [Arguments]  ${tid}  ${bad}  ${good}
  No such test id  ${tid}-warning
  Input text by test id  ${tid}  ${bad}
  Wait test id visible  ${tid}-warning
  Input text by test id  ${tid}  ${good}
  No such test id  ${tid}-warning

Register wizard until signing
  [Arguments]  ${lang}=fi  ${billing}=monthly  ${account}=account5
  Click by test id  ${billing}-billing
  Company ${billing} billing is selected
  Select account type  ${account}
  Click enabled by test id  register-company-continue
  Wait until  Element should be visible  xpath=//*[@data-test-id='register-company-continue']
  Input text by test id  register-company-name        Peten rakennus Oy
  Input text by test id  register-company-y           2341528-4
  Input text by test id  register-company-firstName   Pete
  Input text by test id  register-company-lastName    Puuha
  Input text by test id  register-company-address1    Katukatu
  Input text by test id  register-company-zip         00001
  Input text by test id  register-company-po          Kunta
  Input text by test id  register-company-email       puuha.pete@pete-rakennus.fi
  Input text by test id  register-company-netbill     yinhang
  Input text by test id  register-company-personId    131052-308T
  Run Keyword If  '${lang}' == 'fi'  Test id select is  register-company-language  fi
  Run Keyword If  '${lang}' <> 'fi'  Select from test id  register-company-language  ${lang}
  Select From test id by text  register-company-pop  Basware Oyj (BAWCFI22)
  Click enabled by test id  register-company-continue
  Wait until  Element should be visible  xpath=//div[contains(@class, 'register-company-summary')]
  Run Keyword If  '${account}' == 'account5'  Element should contain  xpath=//strong[@data-test-id='summary-account-text']  Yritystili 5
  Run Keyword If  '${account}' == 'account15'  Element should contain  xpath=//strong[@data-test-id='summary-account-text']  Yritystili 15
  Run Keyword If  '${account}' == 'account30'  Element should contain  xpath=//strong[@data-test-id='summary-account-text']  Yritystili 30
  Click enabled by test id  register-company-continue
  Wait Until  Element Should Be Disabled  xpath=//*[@data-test-id='register-company-sign']
  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel']
  Run Keyword If  '${lang}' == 'fi' and '${billing}' == 'monthly'  Element should contain  register-confirmation-text  €/kuukausi
  Run Keyword If  '${lang}' == 'fi' and '${billing}' == 'yearly'  Element should contain  register-confirmation-text  €/vuosi
  Toggle not Selected  register-company-agree
  Toggle toggle  register-company-agree
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-sign']
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='register-company-cancel']
