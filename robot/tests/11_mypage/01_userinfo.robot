*** Settings ***

Documentation   User changes account details
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

## For some strange reason, firstName and lastName fields are left blank.

Mikko goes to own page
  [Tags]  firefox
  Mikko logs in
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Title Should Be  Lupapiste

There is no company info
  [Tags]  firefox
  Element should not be visible  //div[@data-test-id='mypage-company-accordion']

Mikko changes his name and experience
  [Tags]  firefox
  Change Textfield Value  firstName  Mikko  Mika
  Change Textfield Value  lastName  Intonen  Intola
  Select From List  architect-degree-select  Arkkitehti
  Change Textfield Value  architect.graduatingYear  2000  2001
  Change Textfield Value  architect.fise  f  fise
  Select From List  architect-fiseKelpoisuus-select  tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)
  Checkbox Should Not Be Selected  allowDirectMarketing
  Select Checkbox  allowDirectMarketing

  Save User Data
  Positive indicator should be visible
  User should be logged in  Mika Intola

Name and experience should have changed in Swedish page too
  [Tags]  firefox
  Language To  SV
  Wait for Page to Load  Mika  Intola
  User should be logged in  Mika Intola
  Checkbox Should Be Selected  allowDirectMarketing
  Wait until  List Selection Should Be  architect-degree-select  Arkitekt
  Textfield Value Should Be  architect.graduatingYear  2001
  Textfield Value Should Be  architect.fise  fise
  Wait until  List Selection Should Be  architect-fiseKelpoisuus-select  sedvanlig huvudplanering (nybyggnad)

Mika changes the name and experience back
  [Tags]  firefox
  Change Textfield Value  firstName  Mika  Mikko
  Change Textfield Value  lastName  Intola  Intonen
  Select From List  architect-degree-select  Timmerman
  Change Textfield Value  architect.graduatingYear  2001  2000
  Textfield Value Should Be  architect.graduatingYear  2000
  Change Textfield Value  architect.fise  fise  f
  Select From List  architect-fiseKelpoisuus-select  krävande byggnadsplanering (nybyggnad)
  Positive indicator should not be visible
  Save User Data
  Positive indicator should be visible

Name and experience should have changed in Finnish page too
  [Tags]  firefox
  Language is  FI
  Checkbox Should Be Selected  allowDirectMarketing
  Wait until  List Selection Should Be  architect-degree-select  Kirvesmies
  Wait until  Textfield Value Should Be  architect.graduatingYear  2000
  Textfield Value Should Be  architect.fise  f
  Wait until  List Selection Should Be  architect-fiseKelpoisuus-select  vaativa rakennussuunnittelu (uudisrakentaminen)
  [Teardown]  Logout

Sven logs in and sees language indicator
  Sven logs in
  Wait test id visible  indicator-primary
  Click element  jquery=div.indicator-close
  No such test id  indicator-primary
  Language is  FI

Sven changes his user language to Swedish
  Click element  user-name
  List selection should be  userinfo-language  fi
  Select from list by id  userinfo-language  sv
  Save User Data

Upon saving the UI language changes
  Language is  SV

Clicking logout refers to the Swedish login page
  Click link  jquery=div#logout-header-box a
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
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}

Change Textfield Value
  [Arguments]  ${field}  ${old}  ${new}
  Wait Until  Textfield Value Should Be  ${field}  ${old}
  Input text with jQuery  input[id="${field}"]  ${new}
  Textfield Value Should Be  ${field}  ${new}
