*** Settings ***

Documentation   Users are added to company
Resource        ../../common_resource.robot
Suite teardown  Logout

*** Test Cases ***

Company admin opens company details
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
  Open company details

Form is in read only mode
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']
  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-cancel']
  Element should be disabled  xpath=//div[@data-test-id="company-pop"]//select

Start editing
  Click enabled by test id  company-details-edit
  Wait Until  Element should be enabled  //div[@id='company-content']//button[@data-test-id='company-details-cancel']
  Element should be enabled  xpath=//div[@data-test-id="company-pop"]//select
  Click enabled by test id  company-details-cancel
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-cancel']
  Click enabled by test id  company-details-edit
  Wait Until  Element should be enabled  xpath=//div[@data-test-id="company-pop"]//select

Change eInvoice operator
  List Selection Should Be  xpath=//div[@data-test-id="company-pop"]//select  Basware Oyj (BAWCFI22)
  Select From List  xpath=//div[@data-test-id="company-pop"]//select  Nordea (NDEAFIHH)
  Click enabled by test id  company-details-save
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']


*** Keywords ***
Open company details
  Click Element  user-name
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Element should be visible  //div[@data-test-id='my-company']
  Click button  Muokkaa yrityksen tietoja
  Wait until  Element should be visible  company
