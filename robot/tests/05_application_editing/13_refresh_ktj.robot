*** Settings ***

Documentation   Authority refreshes property data
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
  [Tags]  integration
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  KTJ_${secs}
  Create application the fast way  ${appname}  753-416-25-22  kerrostalo-rivitalo

Refresh KTJ data
  [Tags]  integration
  Click enabled by test id  application-refresh-ktj-btn
  Wait Until  Page should contain  Tiedot päivitetty
  Confirm  dynamic-ok-confirm-dialog
  [Teardown]  Logout
