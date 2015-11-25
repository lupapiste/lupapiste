*** Settings ***

Documentation   Bulletin commenting
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot
Resource        ../common_keywords/vetuma_helpers.robot

*** Test Cases ***

Init, go to bulletin and authenticate via Vetuma
  Create a bulletin and go to bulletin page
  Authenticate via Osuuspankki via Vetuma  vetuma-init

Comment bulletin without any additional form fields
  Write comment for bulletin  Kommentoidaan julkipantua ilmoitusta
  Send comment
  Positive indicator should be visible

Comment bulletin with alternate contact info
  Click by test id  otherReceiver
  Fill out alternate receiver form
  Fill out alternate receiver email field
  Write comment for bulletin  Toinen kommentti
  Send comment
  Positive indicator should be visible