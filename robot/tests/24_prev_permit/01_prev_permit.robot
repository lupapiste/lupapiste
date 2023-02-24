*** Settings ***

Documentation   Prev permit interaction
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko logs in and does not see the Nouda lupa button
  Mikko logs in
  # test the bigger button
  Wait until  Element should not be visible  //section[@id='applications']//button[@data-test-id='applications-create-with-prev-permit']
  # test the smaller button in the upper-right corner
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname-mikko}  create-mikko-app${secs}
  Create application the fast way  ${appname-mikko}  753-416-25-30  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Element should not be visible  //section[@id='applications']//button[@data-test-id='applications-create-new-with-prev-permit']
  Logout

Järvenpää authority logs in and sees the Nouda lupa button
  Jarvenpaa authority logs in
  # test the bigger button
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-with-prev-permit']
  # test the smaller button in the upper-right corner
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname-jarvenpaa-normal}  create-jarvenpaa-app-${secs}
  Create application the fast way  ${appname-jarvenpaa-normal}  186-2-215-10  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-new-with-prev-permit']

Open 'prev permit' create page and check the fields
  Go to prev permit page and fill the kuntalupatunnus
  Toggle selected  authorize-applicants

Click create button
  Click button  prev-permit-create-button
  #Wait until  Element should be visible  xpath=//section[@id='application']//span[@data-test-id='application-property-id']
  Wait until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  186-3-356-6
  Element text should be  xpath=//section[@id='application']//span[@data-test-id='test-application-primary-operation']  Rakentamisen lupa (haettu paperilla)
  Application state should be  constructionStarted
  ${applicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${applicationid}

Check invitees
  Open tab  parties
  Invite count is  3

Open Rakentaminen tab, and check it contains 14 tasks
  Open tab  tasks
  Task count is  task-katselmus  9
  Task count is  task-lupamaarays  2
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  3

Re-fetch application with same kuntalupatunnus
  Go to page  applications
  Go to prev permit page and fill the kuntalupatunnus
  Click button  prev-permit-create-button

The same application is opened, new one is not created
  Wait until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  186-3-356-6
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Should Be Equal As Strings  ${newApplicationid}  ${applicationid}

Cancel the created application and re-fetch application without inviting applications
  Change application state  appealed
  Cancel current application as authority
  Go to page  applications

  Go to prev permit page and fill the kuntalupatunnus
  Toggle toggle  authorize-applicants
  Toggle not selected  authorize-applicants
  Click button  prev-permit-create-button

A new application is opened, still with same property id
  Wait until  Element should be visible  application
  Wait for jQuery
  Wait until  Element should not contain  xpath=//section[@id='application']//span[@data-test-id='application-id']  ${applicationid}
  Wait until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  186-3-356-6
  ${newApplicationid} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Should Not Be Equal As Strings  ${newApplicationid}  ${applicationid}

No invites
  Open tab  parties
  Invite count is  0


*** Keywords ***

Go to prev permit page and fill the kuntalupatunnus
  Click by test id  applications-create-new-with-prev-permit
  Wait until  Element should be visible  //section[@id='create-page-prev-permit']//input[@data-test-id='test-prev-permit-kuntalupatunnus']
  Element should be visible  //section[@id='create-page-prev-permit']//select[@data-test-id='test-prev-permit-organization-select']
  Element should be disabled  //section[@id='create-page-prev-permit']//button[@data-test-id='test-prev-permit-create-button']
  Wait until  Element Should Contain  xpath=//section[@id='create-page-prev-permit']//select[@data-test-id='test-prev-permit-organization-select']  Järvenpään rakennusvalvonta
  Input text  //section[@id='create-page-prev-permit']//input[@data-test-id='test-prev-permit-kuntalupatunnus']  14-0241-R 3
  Element should be enabled  //section[@id='create-page-prev-permit']//button[@data-test-id='test-prev-permit-create-button']

State select check
  [Arguments]  ${state}
  Wait Until  Application state should be  ${state}
  Wait test id visible  change-state-select
  ${select} =  Get Selected List Value  jquery=[data-test-id=change-state-select]
  Wait Until  Should Be Equal  ${select}  ${state}
  Wait Until  Element Should Be Visible  jquery=.state-indication[data-test-state=${state}]

Change application state
  [Arguments]  ${state}
  Click by test id  change-state-select
  Select From List By Value  jquery=[data-test-id=change-state-select]  ${state}
  State select check  ${state}
