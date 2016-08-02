*** Settings ***

Documentation   Authority can resurrect canceled application
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Teppo creates and submits application
  As Teppo
  Create application the fast way  cancel-app  753-416-25-22  kerrostalo-rivitalo
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  //button[@data-test-id='application-submit-btn']
  Submit application

Teppo invites Pena and Pena accepts
  Invite pena@example.com to application
  Logout
  As Pena
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']
  Open application  cancel-app  753-416-25-22
  Application state should be  submitted
  Element should be visible by test id  application-cancel-btn
  Logout

Teppo decides to cancel application, could undo cancelllation
  As Teppo
  Open application  cancel-app  753-416-25-22
  Cancel current application
  Open canceled application  cancel-app  753-416-25-22
  Wait test id visible  application-undo-cancellation-btn
  Logout

Pena sees cancellation and can't undo it
  As Pena
  Open canceled application  cancel-app  753-416-25-22
  Element should not be visible by test id  application-undo-cancellation-btn
  Logout

# Pena calls Sonja for help

Sonja undos cancellation as requested by Teppo
  As Sonja
  Open canceled application  cancel-app  753-416-25-22
  Wait until  Application state should be  canceled
  Wait test id visible  application-undo-cancellation-btn
  Click by test id  application-undo-cancellation-btn
  Confirm yes no dialog
  Wait until  Application state should be  submitted
  Logout

Teppo can now continue editing from submitted state
  As Teppo
  Open application  cancel-app  753-416-25-22
  Application state should be  submitted
