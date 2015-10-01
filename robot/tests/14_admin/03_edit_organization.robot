*** Settings ***

Documentation  Admin edits organization
Suite teardown  Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates an inforequest and an application in Sipoo
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  admin${secs}
  Create inforequest the fast way  ${appname}ir  360603.153  6734222.95  753-416-25-30  kerrostalo-rivitalo  The winter is coming
  Create application the fast way  ${appname}app  753-416-25-31  kerrostalo-rivitalo
  Open to authorities  Hello admin!
  [Teardown]  Logout

Solita admin sees the list of organizations
  SolitaAdmin logs in
  Click link  Organisaatiot
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="organizations-table"]

Admin edits organization with id 753-R
  Wait Until  Click link  xpath=//a[@data-test-id="edit-organization-753-R"]
  Wait until  Element should be visible  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="application-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="open-inforequest-753-R"]/input
  Input text by test id  open-inforequest-email-753-R  root@localhost
  Click enabled by test id  save-753-R
  Wait Until  Page should contain  Tiedot tallennettu
  Confirm  dynamic-ok-confirm-dialog
  Go back

Organization 753-R has now inforequest and application enabled
  Wait Until  Element should be visible  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="application-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost

Admin sets application disabled
  Wait Until  Element should be visible  xpath=//a[@data-test-id="edit-organization-753-R"]
  Click link  xpath=//a[@data-test-id="edit-organization-753-R"]
  Wait until  Element should be visible  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Unselect Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="application-enabled-753-R"]/input
  Click enabled by test id  save-753-R
  Wait Until  Page should contain  Tiedot tallennettu
  Confirm  dynamic-ok-confirm-dialog
  Go back

Organization 753-R has now inforequest enabled and application disabled
  Wait Until  Element should be visible  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="application-enabled-753-R"]  false
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-753-R"]  true
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost
  [Teardown]  Logout

Admin impersonated Sipoo authority
  SolitaAdmin logs in
  Click link  Organisaatiot
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="organizations-table"]
  Wait until  Click link  xpath=//a[@data-impersonate="753-R"]
  Wait Until  Element should be visible  login-as-password
  Input text  login-as-password  admin
  Click enabled by test id  submit-login-as

Admin sees Mikko's inforequest
  Request should be visible  ${appname}ir

Admin sees comment on Mikko's application
  Open application  ${appname}app  753-416-25-31
  Comment count is  1
  [Teardown]  Logout
