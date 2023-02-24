*** Settings ***

Documentation   Mikko creates a new ya applications
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a kaivulupa
  Mikko logs in
  # Enable the following line if you want to run this with local-standalone
  #Create application the fast way  authority-cant-see-drafts  753-416-25-30  kerrostalo-rivitalo
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Set Suite Variable  ${propertyId}  753-423-2-162
  Create application  ${appname}  753  ${propertyId}  YA-kaivulupa
  Wait until  Permit subtype is  Työlupa
  Open to authorities  viesti

Accordions are open for Mikko
  All visible accordions should be open

Accordions are open for Sonja
  Logout
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  All visible accordions should be open
  Logout
  Mikko logs in

Mikko creates a kayttolupa
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-151  YA-kayttolupa
  Wait until  Permit subtype is  Käyttölupa

Mikko creates a mainostus-viitoitus type of kayttolupa
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-159  YA-kayttolupa-mainostus-viitoitus
  Wait until  Permit subtype is  Käyttölupa

Mikko creates a sijoituslupa
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-160  YA-sijoituslupa
  Wait until  Permit subtype is  Sijoituslupa
  [Teardown]  logout
