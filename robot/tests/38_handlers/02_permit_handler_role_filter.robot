*** Settings ***

Documentation  Authority uses handler role filters
Suite Teardown  Run Keywords  Logout  Apply minimal fixture now
Resource        ../../common_resource.robot
Suite Setup     Apply minimal fixture now
Resource       handlers_resource.robot

*** Test Cases ***

# -------------------------
# Applicant
# -------------------------
Pena creates three applications in Sonja's jurisdiction
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${app1}  permit${secs}
  Set Suite Variable  ${app2}  non-permit${secs}
  Set Suite Variable  ${app3}  null${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application with state  ${app1}  ${propertyId}  kerrostalo-rivitalo  open
  Create application with state  ${app2}  ${propertyId}  kerrostalo-rivitalo  open
  Create application with state  ${app3}  ${propertyId}  kerrostalo-rivitalo  open
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja marks herself as the permit handler in the first application
  Sonja logs in
  Open application  ${app1}  ${propertyId}
  Click by test id  edit-handlers
  Click by test id  add-handler
  Edit handler  0  Sibbo Sonja  Käsittelijä

Sonja marks herself as a non-permit handler in the second application
  Open application  ${app2}  ${propertyId}
  Click by test id  edit-handlers
  Click by test id  add-handler
  Edit handler  0  Sibbo Sonja  KVV-Käsittelijä

Sonja leaves the third application without handlers
  Comment  This space intentionally left blank

Sonja views only applications where she is the permit handler
  Go to page  applications
  Click by test id  user-handler-role-true
  Application should be visible  ${app1}
  Application should not be visible  ${app2}
  Application should not be visible  ${app3}

Sonja views only applications where she is a non-permit handler
  Click by test id  user-handler-role-false
  Application should not be visible  ${app1}
  Application should be visible  ${app2}
  Application should not be visible  ${app3}

Sonja views all applications
  Click by test id  user-handler-role-null
  Application should be visible  ${app1}
  Application should be visible  ${app2}
  Application should be visible  ${app3}
  [Teardown]  Logout


*** Keywords ***
Application should be visible
  [Arguments]  ${app}
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${app}"]

Application should not be visible
  [Arguments]  ${app}
  Wait until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${app}"]