*** Settings ***

Documentation   Sonja can't submit application
Suite Setup     Apply submit-restriction fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../06_attachments/variables.py

*** Keywords ***

Open test application
  Open application  Latokuja 3  753-416-55-7

Open test application in required field summary tab
  Open test application
  Open tab  requiredFieldSummary

Submit button is enabled
  Element should be visible  xpath=//*[@data-test-id='application-submit-btn']
  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']

Submit button is disabled
  Element should be visible  xpath=//*[@data-test-id='application-submit-btn']
  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']

*** Test Cases ***

Pena could submit application (when submit restriction is not applied)
  Pena logs in
  Open test application in required field summary tab
  Wait Until  Submit button is enabled

Pena invites Esimerkki and Solita
  Invite company to application  Esimerkki Oy
  Invite company to application  Solita Oy
  Logout

Erkki approves Esimerkki Oy invite and applies submit restriction
  User logs in  erkki@example.com  esimerkki  Erkki Esimerkki
  Open test application
  Wait test id visible  company-approve-invite-dialog
  Element should be visible  jquery=label[for='apply-submit-restriction']
  Click label  apply-submit-restriction
  Click by test id  confirm-yes
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Applied submit restriction is visible on required field summary tab
  Open tab  requiredFieldSummary
  Wait until  Checkbox wrapper selected  optionSubmitRestrictionForOtherAuths

Erkki disables submit restriction
  Click label  optionSubmitRestrictionForOtherAuths
  Wait until  Checkbox wrapper not selected  optionSubmitRestrictionForOtherAuths
  Reload page
  Wait until  Checkbox wrapper not selected  optionSubmitRestrictionForOtherAuths

And enables submit restriction again
  Click label  optionSubmitRestrictionForOtherAuths
  Wait until  Checkbox wrapper selected  optionSubmitRestrictionForOtherAuths
  Reload page
  Wait until  Checkbox wrapper selected  optionSubmitRestrictionForOtherAuths

Erkki logs out
  Logout

Pena cannot submit application
  Pena logs in
  Open test application in required field summary tab
  Wait until  Submit button is disabled

Pena cannot toggle submit restriction
  Element should not be visible  jquery=label[for='optionSubmitRestrictionForOtherAuths']

Correct submit error message is visible
  Element should be visible  //*[@data-test-id='submit-error-0' and @data-submit-error='error.permissions-restricted-by-another-user']
  Element should be visible  //*[@data-test-id='submit-error-user-0' and @data-submit-error-user='esimerkki']

Pena logs out
  Logout

Kaino approves Solita invite, submit restriction checkbox is not visible for solita
  User logs in  kaino@solita.fi  kaino123  Kaino Solita
  Open test application
  Wait test id visible  company-approve-invite-dialog
  Element should not be visible  jquery=label[for='apply-submit-restriction']
  Click by test id  confirm-yes

Submit button is disabled for Solita
  Open tab  requiredFieldSummary
  Wait until  Submit button is disabled

Correct submit error message is visible for Kaino
  Element should be visible  //*[@data-test-id='submit-error-0' and @data-submit-error='error.permissions-restricted-by-another-user']
  Element should be visible  //*[@data-test-id='submit-error-user-0' and @data-submit-error-user='esimerkki']

Kaino cannot toggle submit restriction from required fields summary tab
  Element should not be visible  jquery=label[for='optionSubmitRestrictionForOtherAuths']
  Logout
