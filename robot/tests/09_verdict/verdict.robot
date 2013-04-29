*** Settings ***

Documentation   Application gets verdict
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko want to build Olutteltta
  Mikko logs in
  Create application the fast way  Olutteltta  753  753-416-25-30

Application does not have verdict
  Open tab  verdict
  Verdict is not given

Mikko submits application & goes for lunch
  Submit application
  Logout

Sonja logs in and throws in a verdict
  Sonja logs in
  Open application  Olutteltta  753-416-25-30
  Open tab  verdict
  Open verdict
  Wait Until  Element Should Be Enabled  verdict-id
  Input text  verdict-id  123567890
  Select From List  verdict-type-select  6
  Input text  verdict-given  01.05.2018\t
  Input text  verdict-official  01.06.2018\t
  Input text  verdict-name  Kaarina Krysp III
  Wait Until  Element Should Be Enabled  verdict-submit
  Click button  verdict-submit
  Verdict is given
  Can't regive verdict
  [Teardown]  Logout

Mikko sees that the application has verdict
  Mikko logs in
  Open application  Olutteltta  753-416-25-30
  Open tab  verdict
  Verdict is given

*** Keywords ***

Open verdict
  Wait and click  xpath=//*[@data-test-id='give-verdict']
  Wait until  element should be visible  verdict-id

Verdict is given
  Wait until  Element should be visible  application-verdict-details
  Wait until  Element text should be  //td[@data-test-id='given-verdict-id-0']  123567890


Verdict is not given
  Wait until  Element should not be visible  application-verdict-details

Can't regive verdict
  Wait until  Element should not be visible  xpath=//*[@data-test-id='give-verdict']
