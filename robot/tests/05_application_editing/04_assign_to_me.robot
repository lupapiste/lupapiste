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
  Create application the fast way  ${appname}  753  ${propertyId}  asuinrakennus
  Open to authorities  hojo-hojo
  Element should not be visible  applicationUnseenComments

# LUPA-23
Mikko could add an operation
  It is possible to add operation
  Logout

Sonja sees comment indicator on applications list
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-comments']  1

Application is not assigned
  Open application  ${appname}  ${propertyId}
  Wait Until  Application is assigned to  Valitse..

Sonja sees comment indicator on application
  Element text should be  applicationUnseenComments  1

Sonja resets indicators
  Click enabled by test id  application-mark-everything-seen-btn
  Wait until  Element should not be visible  applicationUnseenComments

Sonja assign application to herself
  Select From List  xpath=//select[@data-test-id='application-assigneed-authority']  Sonja Sibbo

Assignee has changed
  Wait Until  Application is assigned to  Sonja Sibbo

# LUPA-23
Sonja could add an operation
  It is possible to add operation

Sonja adds a comment
  Add comment  Looking good!
  Logout

# LUPA-463
Open latest email
  Go to  ${SERVER}/api/last-email
  Page Should Contain  ${appname}
  Page Should Contain  mikko@example.com

Clicking the first link in email should redirect to front page
  Click link  xpath=//a
  Wait until page contains element  login-username
  Wait Until  Title should be  Lupapiste

Application is shown after login
  User logs in  mikko@example.com  mikko123  Mikko Intonen
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}
  Logout

# LUPA-791
Sonja cancels the application
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='application-cancel-btn']
  Click enabled by test id  application-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog


*** Keywords ***

Application is assigned to
  [Arguments]  ${to}
  Wait until  Element should be visible  xpath=//select[@data-test-id='application-assigneed-authority']
  ${assignee} =  Get selected list label  xpath=//select[@data-test-id='application-assigneed-authority']
  Should be equal  ${assignee}  ${to}

