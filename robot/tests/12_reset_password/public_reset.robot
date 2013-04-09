*** Settings ***

Documentation   Teppo resets password
Resource       ../../common_resource.robot

*** Test Cases ***
Go to reset password page
  Go to login page
  Click Link  Oletko unohtanut salasanasi?
  Wait Until  Element Should Be Visible  reset
  Page Should Contain  Salasanan vaihtaminen

Fill in wrong email
  Input text  email  teppo@exaple.com
  Click enabled by test id  reset-send
  Wait Until  Page Should Contain  ei löydy

Fill in right email
  Input text  email  teppo@example.com
  Click enabled by test id  reset-send
  Wait Until  Page Should Not Contain  ei löydy
  Wait Until  Page Should Contain  Sähköposti lähetetty
