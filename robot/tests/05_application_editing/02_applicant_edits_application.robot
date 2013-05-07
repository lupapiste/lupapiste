*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko goes to parties tab of an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-416-17-15
  Set Suite Variable  ${newId}  753-416-17-14
  Create application the fast way  ${appname}  753  ${propertyId}
  Open application  ${appname}  ${propertyId}
  Open tab  parties

Mikko decides to delete maksaja
  Wait and click  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  Click enabled by test id   remove-ok
  Wait Until  Element Should Not Be Visible  dialog-remove-doc

Mikko adds party maksaja
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//select[@data-test-id='select-party-document']
  Select From List  xpath=//select[@data-test-id="select-party-document"]  maksaja
  Click enabled by test id  add-party-button
  Wait Until  Element Should Not Be Visible  dialog-add-party
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']

Mikko changes application address and property id
  Page should not contain  ${newName}
  Element should be visible  xpath=//section[@id='application']//a[@data-test-id='change-location-link']
  Click element  xpath=//section[@id='application']//a[@data-test-id='change-location-link']
  Input text by test id  application-new-address  ${newName}
  Input text by test id  application-new-propertyid  ${newId}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${newName}
  Page should contain  ${newId}

Mikko decides to submit application
  Open application  ${newName}  ${newId}
  Wait until  Application state should be  draft
  Submit application

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  ${newName}

Mikko has worked really hard and now he needs some strong coffee
  Logout
