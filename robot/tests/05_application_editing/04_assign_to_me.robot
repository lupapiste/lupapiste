*** Settings ***

Documentation  Sonja can assign application to herself
Suite teardown  Logout
Resource       ../../common_resource.robot

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
  Logout

Sonja sees comment indicator on applications list
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-comments']  1

Application is not assigned
  Open application  ${appname}  ${propertyId}
  Wait Until  Application is assigned to  ${EMPTY}

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
  Click link  application-assignee-edit
  Wait Until  Element should be visible  assignee-select
  Wait Until  Select From List  assignee-select  Sibbo Sonja
  Click enabled by test id  modal-dialog-submit-button
  Wait Until  Element should not be visible  assignee-select

Assignee has changed
  Wait Until  Application is assigned to  Sibbo Sonja

Sonja sees Mikko's full person ID
  Open tab  parties
  Open accordions  parties
  Wait Until  Textfield value should be  xpath=//div[@id='application-parties-tab']//input[@data-docgen-path='henkilo.henkilotiedot.hetu']  210281-0002

# LUPA-23
Sonja could add an operation
  It is possible to add operation

Sonja adds a comment
  Add comment  Looking good!
  Logout

# LUPA-463
Open latest email
  Open last email
  Page Should Contain  ${appname}
  Page Should Contain  mikko@example.com

Clicking the first link in email should redirect to front page
  Click link  xpath=//a
  Wait until page contains element  login-username
  Wait Until  Title should be  Lupapiste

Application is shown after login
  # Manual login because 'Mikko logs in' checks a different landing page
  User logs in  mikko@example.com  mikko123  Mikko Intonen
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}
  Logout

# LUPA-791
Sonja cancels the application
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Close current application as authority


*** Keywords ***

Application is assigned to
  [Arguments]  ${to}
  ${path} =   Set Variable  xpath=//span[@class='application_summary_text']/span[@data-bind='fullName: authority']
  Wait until  Element should be visible  ${path}
  Wait until  Element text should be  ${path}  ${to}
