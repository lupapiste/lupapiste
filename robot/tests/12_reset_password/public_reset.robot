*** Settings ***

Documentation   Teppo resets password
Resource       ../../common_resource.robot

*** Test Cases ***

Unable to log in
  Go to login page
  Login fails  teppo@example.com  teppo123
  Login fails  teppo@example.com  teppo123
  Login fails  teppo@example.com  teppo123
  Login fails  teppo@example.com  teppo123
  Wait Until  Page Should Contain  Tunnus on lukittu

Go to reset password page
  Click Link  Oletko unohtanut salasanasi?
  Wait Until  Element Should Be Visible  reset
  Page Should Contain  Salasanan vaihtaminen

Fill in wrong email
  Input text  email  teppo@exaple.com
  Click enabled by test id  reset-send
  Wait Until  Page Should Contain  Antamaasi sähköpostiosoitetta ei löydy järjestelmästä.

Fill in right email
  Input text  email  teppo@example.com
  Click enabled by test id  reset-send
  Wait Until  Page Should Not Contain  Antamaasi sähköpostiosoitetta ei löydy järjestelmästä.

Email was send
  Wait Until  Page Should Contain  Sähköposti lähetetty
  Open last email
  Page Should Contain  teppo@example.com
  ## First link
  Click link  xpath=//a

Reset password
  Fill in new password  setpw  teppo123

Can not login with the old password
  Login fails  teppo@example.com  teppo69

Can login with the new password
  User logs in  teppo@example.com  teppo123  Teppo Nieminen
  Logout
