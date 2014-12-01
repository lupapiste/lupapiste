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
  Input Text  invite-email  teppo@example.com
  Click by test id  application-invite-foreman
  Element should contain  xpath=//*[@data-test-id='test-application-operation']  Asuinkerrostalon ja/tai rivitalon rakentaminen
  [Teardown]  logout

Foreman can see application
  Teppo logs in
  Go to page  applications
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='create-app'][1]/td[@data-test-col-name='operation']  Työnjohtajan nimeäminen
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='create-app'][2]/td[@data-test-col-name='operation']  Asuinkerrostalon ja/tai rivitalon rakentaminen
