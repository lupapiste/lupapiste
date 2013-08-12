*** Settings ***

Documentation   Authority adds couple of neighbors, then we invite them and see how they respond
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko wants to build a water slide
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application the fast way  ${appname}  753  753-416-25-22
  Add comment  Lapsille vesiliuku
  Logout

Sonja adds some neighbors
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Click by test id  manage-neighbors
  # Add neighbor "a"
  Click by test id  manager-neighbors-add
  Input text by test id  neighbors.edit.propertyId  1-2-3-4
  Input text by test id  neighbors.edit.name  a
  Input text by test id  neighbors.edit.email  a@example.com
  Click by test id  neighbors.edit.ok
  # Add neighbor "b"
  Click by test id  manager-neighbors-add
  Input text by test id  neighbors.edit.propertyId  1-2-3-4
  Input text by test id  neighbors.edit.name  b
  Input text by test id  neighbors.edit.email  b@example.com
  Click by test id  neighbors.edit.ok
  # Add neighbor "c" with wrong email, ups. Sonja must correct that later.
  Click by test id  manager-neighbors-add
  Input text by test id  neighbors.edit.propertyId  1-2-3-4
  Input text by test id  neighbors.edit.name  c
  Input text by test id  neighbors.edit.email  x@example.com
  Click by test id  neighbors.edit.ok
  # Add neighbor "d". This is a mistake that Sonja must fix later.
  Click by test id  manager-neighbors-add
  Input text by test id  neighbors.edit.propertyId  1-2-3-4
  Input text by test id  neighbors.edit.name  d
  Input text by test id  neighbors.edit.email  d@example.com
  Click by test id  neighbors.edit.ok
  # Check that they are all listed
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-b@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']

Sonja removes mistakenly added neighbor d
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']
  Click element  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//div[@id='dialog-confirm-neighbor-remove']
  Click element  xpath=//div[@id='dialog-confirm-neighbor-remove']//*[@data-test-id='confirm-yes']
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']

Sonja corrects the email address of neighbor c
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-edit']
  Click element  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-edit']
  Input text by test id  neighbors.edit.email  c@example.com
  Click by test id  neighbors.edit.ok
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']//a[@data-test-id='manage-neighbors-remove']

Sonja checks that everything is ok
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-b@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']
  Click by test id  manager-neighbors-done
  # Make sure all neihgbors are in state "open":
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Sonja meets user 'a' IRL and marks her as 'done'
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']

Sonja opens status details dialog
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']  Sonja
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-lastName']  Sibbo
  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-usereid']
  Click element  xpath=//div[@id='dialog-neighbor-status']//button[@data-test-id='neighbor-status-ok']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']

Sonja has done her part
  Logout

Mikko sees neighbors and their status
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Mikko can't mark neighbors as done, but can send an email invitation
  Element should not be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']

Mikko sends an email invitation to neighbor 'b'
  Click element   xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  xpath=//div[@id='dialog-send-neighbor-email']
  Wait until  Input text  xpath=//input[@id='neighbors-sendemail-email']  b@example.com
  Click element  xpath=//div[@id='dialog-send-neighbor-email']//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-send-neighbor-email']
  Logout

Mail is sent
  Go to  ${SERVER}/api/last-email
  Wait until  Element should contain  id=to  b@example.com

Neighbor clicks on email link and sees epplication
  Click element  xpath=//a
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-title']
  Wait until  Element should contain  xpath=//*[@data-test-id='application-title']  ${appname}
  Element should contain  xpath=//*[@data-test-id='application-property-id']  753-416-25-22
  Element should contain  xpath=//*[@data-test-id='test-application-operation']  Asuinrakennuksen rakentaminen

Neighbor clicks vetuma button to identify herself
  [Tags]  fail integration
  Click element  xpath=//*[@data-test-id='vetuma-init']
  Click element  xpath=//img[@alt='Pankkitunnistus']
  Click element  xpath=//a[@class='nordea']
  Wait Until  Element Should Be Visible  xpath=//input[@name='Ok']
  Click element  xpath=//input[@name='Ok']
  Click element  xpath=//input[@type='submit']
  Click element  xpath=//button[@type='submit']

Neighbor is back and leaves a comment
  [Tags]  fail integration
  Wait until  Element should be visible  xpath=//input[@data-test-id='neighbor-response-comments']
  Click element  xpath=//input[@data-test-id='neighbor-response-comments']
  Wait until  Element should be enabled  xpath=//*[@data-test-id='neighbor-response-message']
  Input text  xpath=//*[@data-test-id='neighbor-response-message']  No fucking way
  Wait until  Element should be enabled  xpath=//*[@data-test-id='neighbor-response-send']
  Click element  xpath=//*[@data-test-id='neighbor-response-send']
  Wait until  Element should be visible  xpath=//*[@data-test-id='neighbor-response-done']
  Element text should be  xpath=//*[@data-test-id='neighbor-response-done']  KIITOS VASTAUKSESTASI!

Mikko sees that the neighbor has given a comment
  [Tags]  fail integration
  Go to login page
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']

Mikko opens dialog to see neighbors response
  [Tags]  fail integration
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbors-row-status-response-given-comments']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-firstName']  PORTAALIA
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-lastName']  TESTAA
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-message']  No fucking way

Mikko can not see neighbor sotu
  [Tags]  fail integration
  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-userid']

Mikko goes to pursuit happines in life
  [Tags]  fail integration
  Click element  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-ok']
  Logout

Sonja sees that the neighbour has given a comment
  [Tags]  fail integration
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbors-row-status-response-given-comments']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-firstName']  PORTAALIA
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-lastName']  TESTAA
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-message']  No fucking way

Sonja can see neighbor sotu
  [Tags]  fail integration
  Wait until  Element text should be  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-userid']  210281-9988
  Click element  xpath=//div[@id='dialog-neighbor-status']//*[@data-test-id='neighbor-status-ok']
