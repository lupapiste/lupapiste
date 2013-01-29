*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Wait until  Number of requests on page  application-row  0
  Wait until  Number of requests on page  inforequest-row  0
  Create application  Latokuja 1, Sipoo  753  75341600250021
  Click by test id  application-requests
  
Mikko sees one application in list
  Wait until  Number of requests on page  application-row  1
  Wait until  Number of requests on page  inforequest-row  0

Mikko creates a new inforequest to Latokuja 2
  Create inforequest  Latokuja 2, Sipoo  753  75341600250022  Hoblaa
  Click by test id  inforequest-requests
  
Mikko sees one application and one inforequest
  Wait until  Number of requests on page  application-row  1
  Wait until  Number of requests on page  inforequest-row  1
  Logout

Teppo should not see Mikko's application
  Teppo logs in
  Wait until  Number of requests on page  application-row  0
  Wait until  Number of requests on page  inforequest-row  0
  Logout

Mikko comes back and sees hes application and inforequest
  Mikko logs in
  Wait until  Number of requests on page  application-row  1
  Wait until  Number of requests on page  inforequest-row  1  

Mikko inspects inforequest at Latokuja 2 and sees he's initial comments
  Click element  xpath=//*[@id='applications']//tr[contains(@class,'inforequest')]//td[text()='Latokuja 2, Sipoo']
  Wait until  Xpath Should Match X Times  //section[@id='inforequest']//table[@data-test-id='comments-table']//td[text()='Hoblaa']  1 
  Click by test id  inforequest-requests

Mikko creates new application to Latokuja 3
  Create application  Latokuja 3, Sipoo  753  75341600250023
  Click by test id  application-requests
  Wait until  Number of requests on page  application-row  2
  Wait until  Number of requests on page  inforequest-row  1  

Mikko closes application at Latokuja 3
  Click element  xpath=//*[@id='applications']//tr[contains(@class,'application')]//td[text()='Latokuja 3, Sipoo']
  Click by test id  application-cancel-btn
  Wait until  Number of requests on page  application-row  1
  Wait until  Number of requests on page  inforequest-row  1

Mikko goes to lunch
  Logout
