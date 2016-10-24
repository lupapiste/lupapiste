*** Settings ***

Documentation   Bulletin commenting
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot
Resource        ../common_keywords/ident_helpers.robot

*** Test Cases ***

Init, go to bulletin and authenticate via Vetuma
  [Tags]  integration
  Create a bulletin and go to bulletin page
  Open bulletin tab  info
  Authenticate via dummy page  vetuma-init
  Open bulletin tab  info

Comment bulletin without any additional form fields
  [Tags]  integration
  Write comment for bulletin  Kommentoidaan julkipantua ilmoitusta
  Send comment
  Positive indicator should be visible

Comment bulletin with alternate contact info
  [Tags]  integration
  Click element  xpath=//label[@for="otherReceiver"]
  Fill out alternate receiver form
  Fill out alternate receiver email field
  Write comment for bulletin  Toinen kommentti
  Send comment
  Positive indicator should be visible
