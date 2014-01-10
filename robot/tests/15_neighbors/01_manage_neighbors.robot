*** Settings ***

Documentation   Authority add neighbors to be heard
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New applications does not have neighbors
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Jalkapesula_${secs}
  Create application the fast way  ${appname}  753  753-416-25-22
  Add comment  Jalkapesulaa rakentaisin

  Open tab  statement
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-no-neigbors']

User cant manage neighbors
  Wait until  Element should not be visible  xpath=//*[@data-test-id='manage-neighbors']
  [Teardown]  logout

Sonja can manage neigbors
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-no-neigbors']
  Wait until  Element should be visible  xpath=//*[@data-test-id='manage-neighbors']
