*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  [Tags]  ie8
  Mikko logs in
  Create application  create-app  753  753-416-17-15  R
  It is possible to add operation

Mikko sees application in list
  [Tags]  ie8
  Go to page  applications
  Request should be visible  create-app

Mikko creates a new inforequest
  Create inforequest  create-info  753  753-416-25-22  Hoblaa  R
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Mikko Intonen
  Wait until  Element should be visible  //section[@id='inforequest']//span[@data-test-operation-id='asuinrakennus']
  Element should not be visible  //h2[@data-test-id='wanna-join']

Mikko sees one application and one inforequest
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info

Mikko is really hungry and runs to Selvi for some delicious liha-annos
  Logout

Teppo should not see Mikko's application
  Teppo logs in
  Request should not be visible  create-app
  Request should not be visible  create-info
  Logout

Mikko comes back and sees his application and inforequest
  Mikko logs in
  Request should be visible  create-app
  Request should be visible  create-info

Mikko inspects inforequest and sees his initial comments
  Open inforequest  create-info  753-416-25-22
  Wait until  Xpath Should Match X Times  //section[@id='inforequest']//table[@data-test-id='comments-table']//span[text()='Hoblaa']  1

#LUPA-585
The contents of unsent inforequest's message field is resetted properly when moving to another inforequest
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  roskaa
  # XXX 'Element Should Contain' or 'Textfield Value Should Be' do not work for some reason
  Wait For Condition  return $("#inforequest").find("textarea[data-test-id='application-new-comment-text']").val() == "roskaa";

  Create inforequest the fast way  create-info-2  753  753-416-25-22  init-comment-2
  Wait For Condition  return $("#inforequest").find("textarea[data-test-id='application-new-comment-text']").val() == "";

Mikko creates new application
  Go to page  applications
  Wait until  Element should be visible  xpath=//*[@data-test-id='applications-create-new']
  Create application the fast way  create-app-2  753  753-416-25-22
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info
  Request should be visible  create-app-2

Mikko closes application at Latokuja 3 and logs out
  Open application  create-app-2  753-416-25-22
  Close current application
  Wait Until  Request should be visible  create-app
  Wait Until  Request should be visible  create-info
  Wait Until  Request should not be visible  create-app-2
  [Teardown]  logout

# LUPA-23
Authority (Veikko) can create an application
  Veikko logs in
  Create application the fast way  create-veikko-auth-app  837  837-416-17-15
  Wait until  Application state should be  open
  It is possible to add operation

# LUPA-23
Veikko can submit the application he created
  Wait Until  Element should be visible  //*[@data-test-id='application-submit-btn']

Veikko sees application in list
  Go to page  applications
  Request should be visible  create-veikko-auth-app
  [Teardown]  logout
