*** Settings ***

Documentation   Authority adds couple of neighbors, then we invite them and see how they respond
# Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko wants to build a water slide
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  slide_${secs}
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
  
Sonja meets marks neighbor 'a' as 'done'
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  
Sonja opens status details dialog 
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']  Sonja
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-lastName']  Sibbo
  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-usereid']
  Sleep  5
  Click element  xpath=//div[@id='dialog-neighbor-status']//button[@data-test-id='neighbor-status-ok']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']
  
Sonja has done her part
  Sleep  1
