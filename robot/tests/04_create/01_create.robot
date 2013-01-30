*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application  create-app  753  75341600250021
  
Mikko sees application in list
  Go to page  applications
  Request should be visible  create-app

Mikko creates a new inforequest
  Create inforequest  create-info  753  75341600250022  Hoblaa
  
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
  Open the inforequest  create-info
  Wait until  Xpath Should Match X Times  //section[@id='inforequest']//table[@data-test-id='comments-table']//td[text()='Hoblaa']  1 

Mikko creates new application
  Go to page  applications
  Create application  create-app-2  753  75341600250023
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info
  Request should be visible  create-app-2

Mikko closes application at Latokuja 3
  Open the application  create-app-2
  Click by test id  application-cancel-btn
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info
  Request should not be visible  create-app-2

Mikko decides to submit create-app
  Open the application  create-app
  Click by test id  application-submit-btn
  Wait until  Application state should be  submitted   

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  create-app

Mikko has worked really hard and now he needs some strong coffee
  Logout
