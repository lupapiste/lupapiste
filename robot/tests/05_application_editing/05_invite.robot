*** Settings ***

Documentation   Application invites
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application  Latokuja 5, Sipoo  753  75341600250025
  Wait until  Element should contain     xpath=//section[@id='application']//span[@data-test-id='application-title']  Latokuja 5, Sipoo
  
Mikko can see invitation button
  Element should be visible  xpath=//*[@data-test-id='application-add-invite']

Mikko adds comment so thate application will be visible to admin
  Click by test id  application-open-conversation-tab
  Wait Until  Element should be visible  application-conversation-tab
  Input text  xpath=//textarea[@data-test-id='application-new-comment-text']  foo
  Click by test id  application-new-comment-btn

Mikko logs out and got to nearest bar
  Logout
  
Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Click element  xpath=//section[@id='applications']//tr[contains(@class,'application')]//td[text()='Latokuja 5, Sipoo']
  Wait until  Element should contain     xpath=//section[@id='application']//span[@data-test-id='application-title']  Latokuja 5, Sipoo
  Element should not be visible  xpath=//*[@data-test-id='application-add-invite']
  Logout
