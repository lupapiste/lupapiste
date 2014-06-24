*** Settings ***

Documentation   Application gets verdict
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko want to build Olutteltta
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Olutteltta${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus

Application does not have verdict
  Open tab  verdict
  Verdict is not given

Mikko submits application & goes for lunch
  Submit application
  [Teardown]  logout

Sonja logs in and throws in a verdict
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Throw in a verdict
  Verdict is given  123567890

Stamping dialog opens and closes
  Open tab  attachments
  Element should be visible  xpath=//section[@id='application']//button[@data-test-id='application-stamp-btn']
  Click enabled by test id  application-stamp-btn
  Wait Until  Element should be visible  dialog-stamp-attachments
  Click enabled by test id   application-stamp-dialdog-ok
  Wait Until  Element should not be visible  dialog-stamp-attachments

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Verdict is given  2013-01
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  [Teardown]  Logout

Mikko sees that the application has verdict
  Mikko logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-indicators']  2
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Verdict is given  2013-01

*** Keywords ***

Verdict is given
  [Arguments]  ${kuntalupatunnus}
  Wait until  Element should be visible  application-verdict-details
  Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-0']  ${kuntalupatunnus}

Verdict is not given
  Wait until  Element should not be visible  application-verdict-details

