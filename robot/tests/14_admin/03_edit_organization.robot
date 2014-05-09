*** Settings ***

Documentation  Admin edits organization
Suite setup     SolitaAdmin logs in
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Solita admin sees the list of organizations
  Click link  [organizations]
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="organizations-table"]

Admin edits organization with id 753-R
  Wait Until  Click link  xpath=//a[@data-test-id="edit-organization-753-R"]
  Wait until  Element should be visible  xpath=//input[@data-test-id="inforequest-enabled"]
  Select Checkbox  xpath=//input[@data-test-id="inforequest-enabled"]
  Select Checkbox  xpath=//input[@data-test-id="application-enabled"]
  Select Checkbox  open-inforequest
  Input text by test id  open-inforequest-email  root@localhost
  Click enabled by test id  save-organization

Organization 753-R has now inforequest and application enabled
  Wait Until  Element should be visible  xpath=//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="application-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="open-inforequest-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost

Admin sets application disabled
  Wait Until  Element should be visible  xpath=//a[@data-test-id="edit-organization-753-R"]
  Click link  xpath=//a[@data-test-id="edit-organization-753-R"]
  Wait until  Element should be visible  xpath=//input[@data-test-id="inforequest-enabled"]
  Select Checkbox  xpath=//input[@data-test-id="inforequest-enabled"]
  Unselect Checkbox  xpath=//input[@data-test-id="application-enabled"]
  Click enabled by test id  save-organization

Organization 753-R has now inforequest enabled and application disabled
  Wait Until  Element should be visible  xpath=//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="application-enabled-753-R"]  false
  Wait Until  Element text should be  xpath=//td[@data-test-id="open-inforequest-753-R"]  true
  Wait Until  Element text should be  xpath=//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost
  [Teardown]  Logout

Mikko creates an inforequest in Sipoo
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  admin${secs}
  Create inforequest the fast way  ${appname}  360603.153  6734222.95  753  753-416-25-30  asuinrakennus  The winter is coming
  [Teardown]  Logout

Admin impersonated Sipoo authority
  SolitaAdmin logs in
  Click link  [organizations]
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="organizations-table"]
  Click link  xpath=//a[@data-impersonate="753-R"]
  Wait Until  Element should be visible  login-as-password
  Input text  login-as-password  admin
  Click enabled by test id  submit-login-as

Admin sees Mikko's inforequest
  Request should be visible  ${appname}

*** Keywords ***

Edit user
  [Arguments]  ${organization}  ${email}  ${firstName}  ${lastName}
  Wait Until       Element Should be Visible  xpath=//a[@data-test-edit-email="${email}"]
  Click Element    xpath=//a[@data-test-edit-email="${email}"]
  Wait until       Element should be visible  user-edit-firstName
  Select From List by test id  edit-admin-organizations-select  ${organization}
  Input text       user-edit-firstName  ${firstName}
  Input text       user-edit-lastName  ${lastName}
  Click element    test-edit-user-save
  Wait Until       Element Should Not Be Visible  dialog-modify-user

Change user password
  [Arguments]  ${email}  ${newPassword}
  Click Element    xpath=//a[@data-test-password-email="${email}"]
  Wait until       Element should be visible  change-user-password
  Input text       change-user-password  ${newPassword}
  Click element    test-change-password-save
  Wait Until       Element Should Not Be Visible  dialog-reset-password

Users full name equals
  [Arguments]  ${email}  ${fullname}
  Wait until  Element text should be  //*[@data-test-full-name-by-email="${email}"]  ${fullname}
