*** Settings ***

Documentation   Neighbor logs in with Vetuma and comments the application
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/ident_helpers.robot

*** Test Cases ***

Mikko prepares the application
  [Tags]  integration
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  VETUMA_${secs}
  Create application the fast way  ${appname}  753-416-25-22  kerrostalo-rivitalo
  Open to authorities  Lapsille vesiliuku eiku Vetuma-tunnukset
  Logout

Sonja adds a neighbor
  [Tags]  integration
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Click by test id  manage-neighbors
  Add neighbor  1-2-3-4  a  a@example.com
  Wait until  Element should be visible  xpath=//div[@id='neighbors-content']//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Logout

Mikko sends an email invitation to neighbor 'a'
  [Tags]  integration
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']
  Click element   xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  xpath=//div[@id='dialog-send-neighbor-email']
  Wait until  Input text  xpath=//input[@id='neighbors-sendemail-email']  a@example.com
  Click element  xpath=//div[@id='dialog-send-neighbor-email']//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-send-neighbor-email']
  [Teardown]  Logout

Mail is sent
  [Tags]  integration
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  a@example.com

Neighbor clicks on email link and sees application
  [Tags]  integration
  Click element  xpath=//a
  Neighbor application address should be  ${appname}
  Element should contain  xpath=//*[@data-test-id='application-property-id']  753-416-25-22
  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen

Neighbor sees some of the documents
  [Tags]  integration
  Page should contain  Hankkeen kuvaus
  Page should contain  Rakennuspaikka
  Page should contain  Rakennuksen tilavuus
  # LPK-546
  Page should not contain  Päätöksen toimitus

Neighbor clicks vetuma button to identify herself
  [Tags]  integration
  Authenticate via dummy page  vetuma-init

Neighbor is back and leaves a comment
  [Tags]  integration
  Wait and click  xpath=//input[@data-test-id='neighbor-response-comments']
  Wait until  Element should be enabled  xpath=//*[@data-test-id='neighbor-response-message']
  Input text  xpath=//*[@data-test-id='neighbor-response-message']  No fucking way
  Click enabled by test id  neighbor-response-send
  Wait until  Element should be visible  xpath=//*[@data-test-id='neighbor-response-done']
  Element text should be  xpath=//*[@data-test-id='neighbor-response-done']  KIITOS VASTAUKSESTASI!

Mikko sees that the neighbor has given a comment
  [Tags]  integration
  Go to login page
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']

Mikko opens dialog to see neighbors response
  [Tags]  integration
  Click element  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-response-given-comments']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-firstName']  Teemu
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-lastName']  Testaaja
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-message']  No fucking way

Mikko can not see neighbor sotu
  [Tags]  integration
  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-userid']

Mikko goes to pursuit happines in life
  [Tags]  integration
  Click element  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-ok']
  Logout

Sonja sees that the neighbour has given a comment
  [Tags]  integration
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']
  Click element  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-response-given-comments']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-firstName']  Teemu
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-lastName']  Testaaja
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-message']  No fucking way

Sonja can see neighbor sotu
  [Tags]  integration
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-userid']  210281-9988
  Click element  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-ok']
