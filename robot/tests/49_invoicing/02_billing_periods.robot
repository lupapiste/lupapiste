*** Settings ***

Documentation   No billing periods for YA price catalogues
Suite Setup     Apply invoicing-enabled fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        catalogue_resource.robot

*** Test Cases ***

Sipoo-YA logs in and creates price catalogue
  Sipoo-YA logs in
  Go to page  price-catalogue
  Click by test id  new-catalogue

No billing periods view is available
  Click by test id  view-no-billing-periods
  Test id input is  start-1  ${EMPTY}
  Test id input is  end-1  ${EMPTY}
  Test id disabled  delete-1
  No such test id  start-2
  Test id enabled  save-no-billing-periods

Periods are fully validated on save
  Fill test id  start-1  ${april-fools}
  Test id disabled  save-no-billing-periods
  No such test id  start-2
  Fill test id  end-1  <b>bad</b>
  Test id enabled  delete-1
  Wait test id visible  start-2
  Test id disabled  delete-2
  Click by test id  save-no-billing-periods
  Indicator check  ${april-fools}  <b>bad</b>

Start date cannot be before the end
  Fill test id  end-1  ${runeberg}
  Click by test id  save-no-billing-periods
  Indicator check  ${april-fools}  ${runeberg}

Delete row and fill a proper period
  Click by test id  delete-1
  Test id input is  start-1  ${EMPTY}
  Test id input is  end-1  ${EMPTY}
  Fill test id  start-1  ${runeberg}
  Fill test id  end-1  ${april-fools}
  Click by test id  save-no-billing-periods

Fill and publish catalogue
  Click by test id  view-by-rows
  Edit row item  0  text  Product
  Edit row item  0  price-per-unit  8
  Edit row item  0  unit  vk
  Fill test id  catalogue-valid-from  ${april-fools}
  Wait until  Element should be enabled  xpath=//button[@data-test-id='publish-catalogue']
  Click by test id  publish-catalogue
  Confirm yes no dialog

Reload page just in case
  Reload page

Select the published catalogue and edit periods
  Wait until  Click element  xpath=//tr[@data-test-id='catalogue-0']//a
  Click by test id  view-no-billing-periods
  Test id input is  start-1  ${runeberg}
  Test id input is  end-1  ${april-fools}
  Fill test id  start-2  ${valid-from-future}
  Fill test id  end-2  ${valid-from-future}
  Click by test id  save-no-billing-periods
  Positive indicator should be visible


*** Keywords ***

Indicator check
  [Arguments]  ${start}  ${end}
  Negative indicator should be visible
  Indicator should contain text  Virheellinen laskutusvapaa: ${start} - ${end}
  Close sticky indicator
