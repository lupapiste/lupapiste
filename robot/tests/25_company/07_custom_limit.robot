*** Settings ***

Documentation   Custom limit is changed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

*** Test Cases ***
# Custom account

Solita admin sets custom account for company 'Solita Oy', max users 2
  SolitaAdmin logs in
  Go to page  companies
  Wait until  Click element  xpath=//table[@data-test-id="corporations-table"]//tr[@data-test-id="company-row-solita"]//a[@data-test-id="company-edit"]
  Wait until  Element text should be  xpath=//div[@data-test-id="modal-dialog-content"]/div[contains(@class, 'header')]/span[contains(@class, 'title')]  Muokkaa yritystä
  Select from list by value  xpath=//select[@name="account-type"]  custom
  Input text with jQuery   input[name="customAccountLimit"]  2
  Focus  xpath=//button[@data-test-id="modal-dialog-submit-button"]
  Click by test id  modal-dialog-submit-button
  Wait Until  Element should not be visible  xpath=//div[@data-test-id="modal-dialog-content"]
  Logout

Kaino logs in and invites user
  Kaino logs in
  Open company user listing
  Invite existing dummy user  dummy3@example.com  Duff3  Dummy3
  Check invitation  0  dummy3@example.com  Dummy3  Duff3  Käyttäjä  Kyllä

Kaino sees account is custom, and it can't be changed by Kaino
  Open company details
  Wait until  Element should be visible  xpath=//span[@data-test-id="company-custom-account"]
  Element should not be visible  xpath=//select[@data-test-id="company-account-select"]

Kaino wants to invite new users, but can't because account limit is reached
  Open company user listing
  Element should be disabled  xpath=//button[@data-test-id="company-add-user"]
  Element should be visible  xpath=//span[contains(@class, 'user-limit-reached')]

No frontend errors
  There are no frontend errors
