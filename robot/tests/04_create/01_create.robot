*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  [Tags]  ie8
  Mikko logs in
  Create application  create-app  753  75341600250021
  It is possible to add operation

Mikko sees application in list
  [Tags]  ie8
  Go to page  applications
  Request should be visible  create-app

Mikko creates a new inforequest
  Create inforequest  create-info  753  75341600250022  Hoblaa
  Wait until  Element text should be  //span[@data-test-id='inforequest-application-applicant']  Mikko Intonen
  Wait until  Element text should be  //span[@data-test-id='inforequest-application-operation']  Asuinrakennuksen rakentaminen

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
  Open inforequest  create-info
  Wait until  Xpath Should Match X Times  //section[@id='inforequest']//table[@data-test-id='comments-table']//td[text()='Hoblaa']  1

Mikko creates new application
  Go to page  applications
  Create application  create-app-2  753  75341600250023
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info
  Request should be visible  create-app-2

Mikko closes application at Latokuja 3
  Open application  create-app-2
  Close current application
  Wait Until  Request should be visible  create-app
  Wait Until  Request should be visible  create-info
  Wait Until  Request should not be visible  create-app-2

Mikko decides to submit create-app
  Open application  create-app
  Wait until  Application state should be  draft
  Click by test id  application-submit-btn
  Wait until  Application state should be  submitted

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  create-app

Mikko has worked really hard and now he needs some strong coffee
  Logout

# LUPA-23
Authority (Veikko) can create an application
  Veikko logs in
  Create application  create-veikko-auth-app  837  75341600250021
  Wait until  Application state should be  open
  It is possible to add operation

# LUPA-23
Veikko can submit the application he created
  Wait Until  Element should be visible  //*[@data-test-id='application-submit-btn']

Veikko sees application in list
  Go to page  applications
  Request should be visible  create-veikko-auth-app
  Logout
