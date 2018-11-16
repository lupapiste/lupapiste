*** Settings ***

Documentation   Users are added to company
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        company_resource.robot
Default Tags    company

*** Test Cases ***

Company admin opens company details
  Kaino logs in
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
  Select From List by label  xpath=//div[@data-test-id="company-pop"]//select  Nordea (NDEAFIHH)
  Click enabled by test id  company-details-save
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']

Frontend errors
  There are no frontend errors
