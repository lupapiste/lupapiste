*** Settings ***

Documentation  Sonja can assign application to herself
Suite Teardown  Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       assignment_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  assign-to-me${secs}
  Set Suite Variable  ${propertyId}  753-416-25-30
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open to authorities  hojo-hojo
  Element should not be visible  applicationUnseenComments

Mikko sets himself as the applicant
  Open tab  parties
  Open accordions  parties
  Select From List  henkilo.userId  Intonen Mikko

Person ID is fully masked
  Wait Until  Textfield value should be  xpath=//div[@id='application-parties-tab']//input[@data-docgen-path='henkilo.henkilotiedot.hetu']  ******-****

# LUPA-23
Mikko could add an operation
  It is possible to add operation
  [Teardown]  Logout

Sonja sees comment indicator on applications list
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@data-test-id='unseen-comments']  1

Application is not assigned
  Open application  ${appname}  ${propertyId}
  Wait Until  Application assignee select empty

Sonja sees Mikko's person ID masked
  Open tab  parties
  Open accordions  parties
  Wait Until  Textfield value should be  xpath=//div[@id='application-parties-tab']//input[@data-docgen-path='henkilo.henkilotiedot.hetu']  210281-****

Sonja sees comment indicator on application
  Element text should be  applicationUnseenComments  1

Sonja resets indicators
  Click enabled by test id  application-mark-everything-seen-btn
  Wait until  Element should not be visible  applicationUnseenComments

Sonja assign application to herself
  Assign application to  Sibbo Sonja

Assignee has changed
  Wait Until  Application assignee select is  Sibbo Sonja

Viewing attachment should not reset the assignee select
  Open tab  attachments
  Open attachment details  hakija.valtakirja
  Wait until  Element should be visible  //a[@data-test-id='back-to-application-from-attachment']
  Scroll and click test id  back-to-application-from-attachment
  Wait Until  Application assignee select is  Sibbo Sonja
  Open tab  parties

Sonja checks property owners
  Click enabled by test id  application-property-owners-btn
  # Local dummy endpoint returns 2 results
  Wait Until  Xpath Should Match X Times  //tbody[@data-test-id="owner-query-results"]/tr  2
  Click enabled by test id  ok-button

Sonja sees Mikko's full person ID
  Open tab  parties
  Open accordions  parties
  Scroll to  input[data-docgen-path='henkilo.henkilotiedot.hetu']
  Wait Until  Textfield value should be  xpath=//div[@id='application-parties-tab']//input[@data-docgen-path='henkilo.henkilotiedot.hetu']  210281-9988

# LUPA-23
Sonja could add an operation
  It is possible to add operation

Sonja adds a comment
  Add comment  Looking good!
  [Teardown]  Logout

# LUPA-463
Open latest email
  Open last email
  Page Should Contain  ${appname}
  Page Should Contain  mikko@example.com

Clicking the first link in email should redirect 'login required' page
  Click link  xpath=//a
  Wait Until  Title should be  Lupapiste
  Wait Until  Element Should Be Visible  hashbang
  Click by test id  login

Application is shown after login
  # Manual login because 'Mikko logs in' checks a different landing page
  User logs in  mikko@example.com  mikko123  Mikko Intonen
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Mikko sees that application is assigned to Sonja
  Application assignee span is  Sibbo Sonja

... even after a page reload
  Reload page
  Application assignee span is  Sibbo Sonja
  [Teardown]  Logout

Sonja logs in and clears authority
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Assign application to nobody
  Reload page
  Application assignee select empty

Sonja assigns application to Ronja
  Assign application to  Sibbo Ronja
  [Teardown]  Logout

Sipoo admin logs in and removes Ronja
  Sipoo logs in
  Wait Until  Element should be visible  jquery=div.users-table tr[data-user-email='ronja.sibbo@sipoo.fi']
  Click Element  jquery=tr[data-user-email='ronja.sibbo@sipoo.fi'] a[data-op=removeFromOrg]
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should not be visible  jquery=tr[data-user-email='ronja.sibbo@sipoo.fi']
  [Teardown]  Logout

Sonja logs in and sees Ronja still assigned
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Application assignee select is  Sibbo Ronja
  Element Should Be Disabled  jquery=option[value=777777777777777777000024]

Sonja assigns application to herself
  Assign application to  Sibbo Sonja
  Reload Page
  Application assignee select is  Sibbo Sonja
  Page Should Not Contain  jquery=option[value=777777777777777777000024]

# LUPA-791
Sonja cancels the application with reason
  Open application  ${appname}  ${propertyId}
  Close current application as authority  Hao de

Sonja checks that the reason is in the comments
  Open canceled application  ${appname}  ${propertyId}
  Check comment  Hao de
  Logout

No errors so far
  There are no frontend errors
