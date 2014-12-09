*** Settings ***

Documentation   Mikko creates a new application
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates new application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  foreman-app${secs}
  Create application the fast way  ${appname}  753  753-416-25-22  kerrostalo-rivitalo
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${newApplicationid}  ${newApplicationid}

Mikko invites foreman to application
  Open tab  parties
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-operation-id='tyonjohtajan-nimeaminen']

Mikko sees sent invitation on the original application
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Element text should be  xpath=//span[@data-test-id='application-id']  ${newApplicationid}
  Open tab  parties
  Wait until  Element text should be  xpath=//ul[@data-test-id='invited-foremans']//span[@data-test-id='foreman-email']  (teppo@example.com)
  [Teardown]  logout

Foreman can see application
  Teppo logs in
  Go to page  applications
  # Should work always because latest application is always at the top
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][1]/td[@data-test-col-name='operation']  Työnjohtajan nimeäminen
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][2]/td[@data-test-col-name='operation']  Asuinkerrostalon tai rivitalon rakentaminen
  [Teardown]  logout

Application is submitted
  Mikko logs in
  Open application at index  ${appname}  753-416-25-22  2
  Submit application
  [Teardown]  logout

Application is approved and given a verdict
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Click enabled by test id  approve-application
  Open tab  verdict
  Submit empty verdict

Add työnjohtaja task
  Open tab  tasks
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-vaadittu-tyonjohtaja
  Input text  create-task-name  Ylitarkastaja
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task
  Task count is  task-vaadittu-tyonjohtaja  1
  [Teardown]  logout

Mikko can see invited foremans on tasks list
  Mikko logs in
  Open application at index  ${appname}  753-416-25-22  1
  Open tab  tasks
  Wait until  Element text should be  xpath=//table[@data-test-id='tasks-foreman']//span[@data-test-id='tasks-foreman-email']  (teppo@example.com)
