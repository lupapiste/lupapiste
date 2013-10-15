*** Settings ***

Documentation   Publishing screenmessages
Resource        ../../common_resource.robot

*** Test Cases ***

Solita admin sees the list of screenmessages
  SolitaAdmin logs in
  Click link  [screen messages]
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="test-screenmessages-table"]

Admin sets a screenmessage
  Element Should Be Disabled  xpath=//button[@data-test-id="test-add-screenmessage"]
  Wait For Condition  return $("[id='add-text-fi']").val("Testi").change() && true;
  Element Should Be Enabled  xpath=//button[@data-test-id="test-add-screenmessage"]
  Wait For Condition  return $("[id='add-text-sv']").val("En test").change() && true;
  Click enabled by test id  test-add-screenmessage

Admin sees the screenmessage correctly in both languages
  Wait until page contains element  xpath=//li[@data-test-id="test-screenmessage"]
  Wait until  Element should be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Wait For Condition  return $("[data-test-id='test-screenmessage']").text() == "Testi";
  Click link  xpath=//*[@data-test-id='lang-sv']
  Wait Until  Page Should Contain  Suomeksi >>
  Wait For Condition  return $("[data-test-id='test-screenmessage']").text() == "En test";
  Click link  xpath=//*[@data-test-id='lang-fi']
  Wait Until  Page Should Contain  På svenska >>
  Logout

Mikko, as applicant, sees the screenmessage, too
  Mikko logs in
  Wait until page contains element  xpath=//li[@data-test-id="test-screenmessage"]
  Wait until  Element should be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Wait For Condition  return $("#sys-notification").find("li[data-test-id='test-screenmessage']").text() == "Testi";

Screenmessage should be visible in swedish, too
  Click link  xpath=//*[@data-test-id='lang-sv']
  Wait until page contains element  xpath=//li[@data-test-id="test-screenmessage"]
  Wait until  Element should be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Wait For Condition  return $("#sys-notification").find("li[data-test-id='test-screenmessage']").text() == "En test";
  Logout

Admin removes screenmessages
  SolitaAdmin logs in
  Click link  [screen messages]
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="test-screenmessages-table"]
  Click enabled by test id  test-delete-screenmessage
  Wait until  Element should not be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Logout

Mikko does not anymore see the screenmessage
  Mikko logs in
  Element should not be visible  xpath=//li[@data-test-id="test-screenmessage"]
  Logout
