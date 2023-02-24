*** Settings ***

Documentation   Publishing screenmessages
Resource        ../../common_resource.robot

*** Test Cases ***

Admin goes to screen messages view and sets a screenmessage (only in Finnish)
  Solita Admin goes to screen messages view of admin page
  Input text  xpath=//textarea[@id='add-text-fi']  Testi
  Element Should Be Enabled  xpath=//button[@data-test-id="test-add-screenmessage"]
  Click enabled by test id  test-add-screenmessage

Admin sees screenmessage in Finnish when UI is in Swedish
  Check displayed screenmessage in both languages  "Testi"  "Testi"

Admin removes screenmessages
  Click enabled by test id  test-delete-screenmessage
  Wait until  Element should not be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Logout

Mikko (applicant) does not anymore see the screenmessage
  Mikko logs in
  Element should not be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Logout

Admin sets a screenmessage (in both Finnish and Swedish)
  Solita Admin goes to screen messages view of admin page
  Input text  xpath=//textarea[@id='add-text-fi']  Testi
  Element Should Be Enabled  xpath=//button[@data-test-id="test-add-screenmessage"]
  Input text  xpath=//textarea[@id='add-text-sv']  En test
  Click enabled by test id  test-add-screenmessage

Admin sees the screenmessage correctly in both languages
  Check displayed screenmessage in both languages  "Testi"  "En test"
  Logout

Mikko (applicant) sees the screenmessage correctly
  Mikko logs in
  Check displayed screenmessage in both languages  "Testi"  "En test"
  Logout

Finally, admin clears all screenmessages
  Solita Admin goes to screen messages view of admin page
  Click enabled by test id  test-delete-screenmessage
  Wait until  Element should not be visible  xpath=//li[@data-test-id="test-screenmessage"]
  [Teardown]  logout

*** Keywords ***

Verify fields of the screenmessages view
  Element Should Be Visible  xpath=//textarea[@id='add-text-fi']
  Element Should Be Visible  xpath=//textarea[@id='add-text-sv']
  Element Should Be Disabled  xpath=//button[@data-test-id="test-add-screenmessage"]

Solita Admin goes to screen messages view of admin page
  SolitaAdmin logs in
  Go to page  screenmessages
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="test-screenmessages-table"]
  Verify fields of the screenmessages view

Check displayed screenmessage in both languages
  [Arguments]  ${fi}  ${sv}
  Wait until page contains element  xpath=//li[@data-test-id="test-screenmessage"]
  Wait until  Element should be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Wait For Condition  return $("li[data-test-id='test-screenmessage'] span").text() == ${fi};
  Language To  SV
  Wait For Condition  return $("li[data-test-id='test-screenmessage'] span").text() == ${sv};
  Language to  FI
