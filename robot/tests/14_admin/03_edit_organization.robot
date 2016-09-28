*** Settings ***

Documentation  Admin edits organization
Suite Teardown  Apply minimal fixture now
Resource       ../../common_resource.robot
Library        DebugLibrary  

*** Test Cases ***

Mikko creates an inforequest and an application in Sipoo
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  admin${secs}
  Create inforequest the fast way  ${appname}ir  360603.153  6734222.95  753-416-25-30  kerrostalo-rivitalo  The winter is coming
  Create application the fast way  ${appname}app  753-416-25-31  kerrostalo-rivitalo
  Open to authorities  Hello admin!
  [Teardown]  Logout

Solita admin goes to organizations page
  SolitaAdmin logs in
  Click link  Organisaatiot
  Wait test id visible  organization-search-term
  Test id text is  organization-result-count  ${EMPTY}

Admin shows all organizations
  Scroll and click test id  organization-show-all
  Test id text is  organization-result-count  18 organisaatiota.

Admin searchs just 753-R
  Fill test id  organization-search-term  753-R
  Scroll and click test id  organization-search
  Test id text is  organization-result-count  1 organisaatio.

Admin edits organization with id 753-R
  Scroll and Click test id  edit-organization-753-R
  Wait until  Element should be visible  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="application-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Input text by test id  open-inforequest-email-753-R  root@localhost
  Click enabled by test id  save-753-R
  Wait Until  Positive indicator should be visible
  Go back

Organization 753-R has now inforequest and application enabled
  Wait Until  Element should be visible  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  neuvonta
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="application-enabled-753-R"]  hakemukset
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost

Admin sets application disabled
  Wait test id visible  edit-organization-753-R
  Scroll and click test id  edit-organization-753-R
  Wait until  Element should be visible  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Select Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="inforequest-enabled-753-R"]/input
  Scroll to test id  application-enabled-753-R
  Unselect Checkbox  xpath=//section[@id="organization"]//td[@data-test-id="application-enabled-753-R"]/input
  Click enabled by test id  save-753-R
  Wait Until  Positive indicator should be visible
  Go back

Organization 753-R has now inforequest enabled and application disabled
  Wait Until  Element should be visible  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="inforequest-enabled-753-R"]  neuvonta
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="application-enabled-753-R"]  -
  Wait Until  Element text should be  xpath=//section[@id="organizations"]//td[@data-test-id="open-inforequest-email-753-R"]  root@localhost

Admin impersonated Sipoo authority
  Wait Until  Positive indicator should not be visible
  Click link  Organisaatiot
  Wait until  Element Should be Visible  xpath=//table[@data-test-id="organizations-table"]
  Scroll to  [data-impersonate=753-R]
  Wait until  Click link  xpath=//a[@data-impersonate="753-R"]
  Wait Until  Element should be visible  login-as-password
  Input text  login-as-password  admin
  Click enabled by test id  submit-login-as

Admin sees Mikko's inforequest
  Request should be visible  ${appname}ir

Admin sees comment on Mikko's application
  Open application  ${appname}app  753-416-25-31
  Comment count is  1

Admin is back to admin-page after logout
  Element should not be visible  admin
  Click link  xpath=//a[@title="Kirjaudu ulos"]
  Wait until  Element should be visible  admin

Logout for real
  Logout
