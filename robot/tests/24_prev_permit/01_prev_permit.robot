*** Settings ***

Documentation   Prev permit interaction
Suite teardown  Logout
Resource        ../../common_resource.robot
#Variables      ../06_attachments/variables.py

*** Test Cases ***


Järvenpää authority logs in and sees the Nouda lupa button
  Jarvenpaa authority logs in
  # test the bigger button
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-with-prev-permit']
  # test the smaller button in the upper-right corner
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname-jarvenpaa-normal}  create-jarvenpaa-app${secs}
  Create application the fast way  ${appname-jarvenpaa-normal}  186  186-2-215-10  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-new-with-prev-permit']

Open 'prev permit' create page and check the fields
  Click by test id  applications-create-new-with-prev-permit
  Wait until  Element should be visible  //section[@id='create-page-prev-permit']//input[@data-test-id='test-prev-permit-kuntalupatunnus']
  Element should be visible  //section[@id='create-page-prev-permit']//select[@data-test-id='test-prev-permit-organization-select']
  Element should be disabled  //section[@id='create-page-prev-permit']//button[@data-test-id='test-prev-permit-create-button']

  Wait until  Element Should Contain  xpath=//section[@id='create-page-prev-permit']//select[@data-test-id='test-prev-permit-organization-select']  Järvenpään rakennusvalvonta

  Input text  //section[@id='create-page-prev-permit']//input[@data-test-id='test-prev-permit-kuntalupatunnus']  14-0241-R 3
  Element should be enabled  //section[@id='create-page-prev-permit']//button[@data-test-id='test-prev-permit-create-button']


  Logout