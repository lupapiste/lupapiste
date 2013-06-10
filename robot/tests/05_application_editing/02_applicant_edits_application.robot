*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-416-17-15
  Set Suite Variable  ${newId}  753-416-17-14
  Create application the fast way  ${appname}  753  ${propertyId}
  Open application  ${appname}  ${propertyId}

Mikko removes apartment
  Wait Until  Element Should Be Visible  //span[@data-test-class="delete-schemas.huoneistot"]
  Click Element  //span[@data-test-class="delete-schemas.huoneistot"]
  Wait Until  Element Should Be Visible  xpath=//button[@data-test-id='confirm-yes']
  Click Element  xpath=//button[@data-test-id='confirm-yes']
  Wait Until  Element Should Not Be Visible  //span[@data-test-class="delete-schemas.huoneistot"]

Mikko goes to parties tab of an application
  Open tab  parties

Mikko decides to delete maksaja
  Wait and click  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  Click enabled by test id   confirm-yes
  Wait Until  Element Should Not Be Visible  dynamic-yes-no-confirm-dialog

Mikko adds party maksaja
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//select[@data-test-id='select-party-document']
  Select From List  xpath=//select[@data-test-id="select-party-document"]  maksaja
  Click enabled by test id  add-party-button
  Wait Until  Element Should Not Be Visible  dialog-add-party
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']

Mikko changes application address and property id
  Page should not contain  ${newName}
  Page should not contain  ${newId}
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
