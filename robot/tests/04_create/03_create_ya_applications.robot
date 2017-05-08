*** Settings ***

Documentation   Mikko creates a new ya applications
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a kaivulupa
  [Tags]  firefox
  Mikko logs in
  # Enable the following line if you want to run this with local-standalone
  #Create application the fast way  authority-cant-see-drafts  753-416-25-30  kerrostalo-rivitalo
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-162  YA-kaivulupa
  Wait until  Permit subtype is  Työlupa

Mikko creates a kayttolupa
  [Tags]  firefox
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-163  YA-kayttolupa
  Wait until  Permit subtype is  Käyttölupa

Mikko creates a mainostus-viitoitus type of kayttolupa
  [Tags]  firefox
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-159  YA-kayttolupa-mainostus-viitoitus
  Wait until  Permit subtype is  Käyttölupa

Mikko creates a sijoituslupa
  [Tags]  firefox
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-160  YA-sijoituslupa
  Wait until  Permit subtype is  Sijoituslupa
  [Teardown]  logout

