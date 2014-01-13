*** Settings ***

Documentation   Sonja should see only applications from Sipoo
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-416-17-15
  Create application the fast way  ${appname}  753  ${propertyId}  asuinrakennus

Mikko removes apartment
  Wait Until  Element Should Be Visible  //span[@data-test-class="delete-schemas.huoneistot"]
  Execute Javascript  $("span[data-test-class='delete-schemas.huoneistot']").click();
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element Should Not Be Visible  //span[@data-test-class="delete-schemas.huoneistot"]

Mikko goes to parties tab of an application
  Open tab  parties

Mikko decides to delete maksaja
  Set Suite Variable  ${maksajaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='maksaja']
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  1
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  Execute Javascript  $("span[data-test-class='delete-schemas.maksaja']").click();
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  0

Mikko adds party maksaja using dialog
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//select[@data-test-id='select-party-document']
  Wait Until  Select From List By Value  xpath=//select[@data-test-id="select-party-document"]  maksaja
  List Selection Should Be  xpath=//select[@data-test-id="select-party-document"]  maksaja
  Click enabled by test id  add-party-button
  Wait Until  Element Should Not Be Visible  dialog-add-party
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//span[@data-test-class='delete-schemas.maksaja']
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  1

Mikko adds party hakija using button
  Set Suite Variable  ${hakijaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='hakija']
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  1
  Click element  hakija_append_btn
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  2

Mikko changes application address
  Page should not contain  ${newName}
  Element should be visible  xpath=//section[@id='application']//a[@data-test-id='change-location-link']
  Click element  xpath=//section[@id='application']//a[@data-test-id='change-location-link']
  Input text by test id  application-new-address  ${newName}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${newName}

Mikko decides to submit application
  Open application  ${newName}  ${propertyId}
  Wait until  Application state should be  draft
  Submit application

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  ${newName}
