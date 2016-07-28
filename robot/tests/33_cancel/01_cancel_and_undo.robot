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

Teppo decides to cancel application
  Cancel current application
  Wait until  Applications page should be open
  Logout

# Teppo calls Sonja for help

Sonja undos cancellation as requested by Teppo
  As Sonja
  Open canceled application  cancel-app  753-416-25-22
  Application state should be  canceled
  Wait test id visible  application-undo-cancellation-btn
  Click by test id  application-undo-cancellation-btn
  Confirm yes no dialog
  Wait until  Application state should be  submitted
  Logout

Teppo can now continue editing from submitted state
  As Teppo
  Open application  cancel-app  753-416-25-22
  Application state should be  submitted
