*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko goes to parties tab of an application
  Mikko logs in
  Open application  create-app  753-416-17-15
  Open tab  parties

Mikko decides to delete maksaja
  Wait and click  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  Wait and click  xpath=//button[@data-test-id="remove-ok"]
  Wait Until  Element Should Not Be Visible  dialog-remove-doc

Mikko adds party maksaja
  Wait and click  xpath=//button[@data-test-id="add-party"]
  Wait Until  Element should be visible  xpath=//select[@data-test-id='select-party-document']
  Select From List  xpath=//select[@data-test-id="select-party-document"]  maksaja
  Click element  xpath=//button[@data-test-id="add-party-button"]
  Wait Until  Element Should Not Be Visible  dialog-add-party
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  
Mikko decides to submit create-app
  Open application  create-app  753-416-17-15
  Wait until  Application state should be  draft
  Submit application

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  create-app

Mikko has worked really hard and now he needs some strong coffee
  Logout
