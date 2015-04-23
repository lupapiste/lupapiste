*** Settings ***

Documentation   Prev permit interaction
Suite teardown  Logout
Resource        ../../common_resource.robot
#Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko logs in and does not see the Nouda lupa button
  Mikko logs in
  # test the bigger button
  Wait until  Element should not be visible  //section[@id='applications']//button[@data-test-id='applications-create-with-prev-permit']
  # test the smaller button in the upper-right corner
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-mikko-app${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Element should not be visible  //section[@id='applications']//button[@data-test-id='applications-create-new-with-prev-permit']
  Logout

Sonja logs in and sees the Nouda lupa button
  Sonja logs in
  # test the bigger button
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-with-prev-permit']
  # test the smaller button in the upper-right corner
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-sonja-app${secs}
  Create application the fast way  ${appname}  753  753-423-2-159  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Element should be visible  //section[@id='applications']//button[@data-test-id='applications-create-new-with-prev-permit']
  Logout