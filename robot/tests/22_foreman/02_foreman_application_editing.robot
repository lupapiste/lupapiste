*** Settings ***

Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Keywords ***
Mikko creates an application and invites foreman
  Create project application
  Mikko invites foreman to application

*** Test Cases ***
Mikko inits applications
  Mikko logs in
  Mikko creates an application and invites foreman
  Mikko creates an application and invites foreman
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Primary operation is  kerrostalo-rivitalo
  Submit application

Foreman sees his other foreman jobs
  Foreman logs in
  Foreman applies personal information to the foreman application  0
  Foreman applies personal information to the foreman application  1
  Foreman can see the first related construction info on the second foreman application

Foreman gets error message when trying to submit foreman notice before link permit has verdict
  Select from list by value  permitSubtypeSelect  ilmoitus
  Open tab  requiredFieldSummary
  Click enabled by test id  application-submit-btn
  Wait Until  Click element  xpath=//div[@id='dynamic-yes-no-confirm-dialog']//button[@data-test-id='confirm-yes']
  Wait Until  Element should be visible  xpath=//div[@id='modal-dialog-content']
  Element should contain  xpath=//div[@id='modal-dialog-content']/div[@class='header']/span[@class="title"]  Työnjohtajan ilmoitus
  Confirm notification dialog
  Wait Until  Application state should be  draft
