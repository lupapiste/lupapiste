*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates new application
  Mikko logs in
  Go to page  applications
  Applications page should be open
  Create application the fast way  create-app  753  753-416-25-22  kerrostalo-rivitalo
  Go to page  applications
  Request should be visible  create-app

Mikko invites foreman to application
  Open application  create-app  753-416-25-22
  Open tab  parties
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-operation-id='tyonjohtajan-nimeaminen']

Mikko sees sent invitation on the original application
  Click by test id  test-application-link-permit-lupapistetunnus
  Open tab  parties
  Wait until  Element text should be  xpath=//ul[@data-test-id='invited-foremans']//span[@data-test-id='foreman-email']  (teppo@example.com)
  [Teardown]  logout

Foreman can see application
  Teppo logs in
  Go to page  applications
  # Should work always because latest application is always at the top
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='create-app'][1]/td[@data-test-col-name='operation']  Työnjohtajan nimeäminen
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='create-app'][2]/td[@data-test-col-name='operation']  Asuinkerrostalon tai rivitalon rakentaminen
