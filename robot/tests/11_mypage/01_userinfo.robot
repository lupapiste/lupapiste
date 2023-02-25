*** Settings ***

Documentation   User changes account details
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko goes to own page
  Mikko logs in
  Open My Page
  Wait for Page to Load  Mikko  Intonen
  Title Should Be  Lupapiste

There is no company info
  Element should not be visible  //div[@data-test-id='mypage-company-accordion']

Mikko changes his name and experience
  Fill test id  firstName  Mika
  Fill test id  lastName  Intola
  Scroll to  div#pw-form
  Select From List by label  architect-degree-select  Arkkitehti
  Change Textfield Value  architect.graduatingYear  2000  2001
  Change Textfield Value  architect.fise  f  fise
  Select From List by label  architect-fiseKelpoisuus-select  tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)
  Checkbox should not be selected  allowDirectMarketing
  Click label  allowDirectMarketing

  Save User Data
  Positive indicator should be visible
  User should be logged in  Mika Intola

Name and experience should have changed in Swedish page too
  Language To  SV
  Wait for Page to Load  Mika  Intola
  User should be logged in  Mika Intola
  Checkbox Should Be Selected  allowDirectMarketing
  Wait until  List Selection Should Be  architect-degree-select  Arkitekt
  Textfield Value Should Be  architect.graduatingYear  2001
  Textfield Value Should Be  architect.fise  fise
  Wait until  List Selection Should Be  architect-fiseKelpoisuus-select  sedvanlig huvudplanering (nybyggnad)

Mika changes the name and experience back
  Change Textfield Value  firstName  Mika  Mikko
  Change Textfield Value  lastName  Intola  Intonen
  Select From List by label  architect-degree-select  Timmerman
  Change Textfield Value  architect.graduatingYear  2001  2000
  Textfield Value Should Be  architect.graduatingYear  2000
  Change Textfield Value  architect.fise  fise  f
  Select From List by label  architect-fiseKelpoisuus-select  krävande byggnadsplanering (nybyggnad)
  Positive indicator should not be visible
  Save User Data
  Positive indicator should be visible

Name and experience should have changed in Finnish page too
  Language To  FI
  Checkbox Should Be Selected  allowDirectMarketing
  Wait until  List Selection Should Be  architect-degree-select  Kirvesmies
  Wait until  Textfield Value Should Be  architect.graduatingYear  2000
  Textfield Value Should Be  architect.fise  f
  Wait until  List Selection Should Be  architect-fiseKelpoisuus-select  vaativa rakennussuunnittelu (uudisrakentaminen)
  [Teardown]  Logout

Sven logs in and sees language indicator
  Sven logs in
  Wait test id visible  indicator-primary
  # User note about language should have link to mypage
  Element should contain  xpath=//div[@data-test-id='indicator-primary']//div[contains(@class, 'indicator-message')]  K\u00e4yt\u00e4t Lupapistett\u00e4
  Click element  jquery=div.indicator-close
  No such test id  indicator-primary
  Language is  FI

Sven changes his user language to Swedish
  Open My Page
  List selection should be  userinfo-language  fi
  Select from list by id and value  userinfo-language  sv
  Save User Data

Upon saving the UI language changes
  Wait until  Language is  SV

Clicking logout refers to the Swedish login page
  Click Element  header-user-dropdown
  Click link  logout-button
  Wait Until  Element should be visible  login-button
  Language is  SV

Sven changes language to Finnish, logs in again, the UI language is now Swedish
  Language to  FI
  Sven logs in
  Language is  SV
  No such test id  indicator-primary
  [Teardown]  Logout

*** Keywords ***

Save User Data
  Click enabled by test id  save-my-userinfo

Wait for Page to Load
  [Arguments]  ${firstName}  ${lastName}
  Wait for jQuery
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}

Change Textfield Value
  [Arguments]  ${field}  ${old}  ${new}
  Wait Until  Textfield Value Should Be  ${field}  ${old}
  Input text with jQuery  input[id="${field}"]  ${new}
  Textfield Value Should Be  ${field}  ${new}
