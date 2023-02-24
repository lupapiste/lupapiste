*** Settings ***

Documentation   Price catalogue management
Suite Setup     Apply invoicing-enabled fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        catalogue_resource.robot

*** Test Cases ***

Sipoo logs in and creates the first price catalogue
  Sipoo logs in
  Go to page  price-catalogue
  No such test id  select-catalogue
  No such test id  view-by-rows
  No such test id  view-by-operations
  No such test id  view-no-billing-periods
  No such test id  catalogue-saved-info
  No such test id  delete-catalogue
  No such test id  publish-catalogue
  No such test id  catalogue-from
  No such test id  add-row
  No such row item  0  code
  Test id text is  new-catalogue  Uusi taksa
  Click by test id  new-catalogue
  Wait until  Page Should Contain  Muokkaa taksaa
  No such test id  new-catalogue
  Wait test id visible  view-by-rows
  Wait test id visible  view-by-operations
  No such test id  view-no-billing-periods
  Wait test id visible  catalogue-saved-info
  Wait test id visible  delete-catalogue
  Wait test id visible  publish-catalogue
  Wait test id visible  catalogue-valid-from
  Wait test id visible  add-row

There is initially one row
  Row item visible  0  code
  Row item visible  0  text
  Row item visible  0  price-per-unit
  Row item visible  0  discount-percent
  Row item visible  0  min-total-price
  Row item visible  0  max-total-price
  Row item visible  0  toggle-product-constants
  Wait test id visible  delete-row-0
  No such row item  0  product-constants-kustannuspaikka
  No such row item  0  move-up
  No such row item  0  move-down

Draft can be deleted
  Click by test id  delete-catalogue
  Deny yes no dialog
  Click by test id  delete-catalogue
  Confirm yes no dialog

Recreate draft
  Click by test id  new-catalogue

Publishing is possible when every required field has been filled
  Test id disabled  publish-catalogue
  Edit row item  0  code  One
  Edit row item  0  text  First item
  Edit row item  0  price-per-unit  10
  Edit row item  0  unit  kpl
  Fill test id  catalogue-valid-from  ${runeberg}
  Test id enabled  publish-catalogue

Add another row
  Click by test id  add-row
  Test id disabled  publish-catalogue
  Edit row item  0  code  Two
  Edit row item  0  text  Second item
  Edit row item  0  price-per-unit  20
  Edit row item  0  unit  pv
  Row item input is  1  code  One
  Row item input is  1  text  First item
  Row item input is  1  price-per-unit  10
  Row item input is  1  unit  kpl
  Test id enabled  publish-catalogue

Bad values are highlighted and prevent publishing
  Edit row item  0  discount-percent  bad
  Test id disabled  publish-catalogue
  Edit row item  0  discount-percent  0
  Edit row item  0  min-total-price  MIN
  Test id disabled  publish-catalogue
  Bad row item  0  min-total-price
  Edit row item  0  min-total-price  100
  Good row item  0  min-total-price
  Test id enabled  publish-catalogue
  Edit row item  0  max-total-price  MAX
  Test id disabled  publish-catalogue
  Bad row item  0  max-total-price
  Edit row item  0  max-total-price  900
  Good row item  0  max-total-price
  Test id enabled  publish-catalogue
  Edit row item  0  price-per-unit  bad
  Test id disabled  publish-catalogue
  Bad row item  0  price-per-unit

Rows can now be reordered
  Click by test id  add-row
  Wait until  Row item input is  0  code  ${EMPTY}
  Edit row item  0  code  Three
  Sleep  0.5s
  # Three, two, one
  Move row up  2
  # Three, one, two
  Wait until  Row item input is  0  code  Three
  Wait until  Row item input is  0  price-per-unit  ${EMPTY}
  Good row item  0  price-per-unit

  Row item input is  1  code  One
  Row item input is  1  price-per-unit  10
  Good row item  1  price-per-unit

  Row item input is  2  code  Two
  Row item input is  2  price-per-unit  bad
  Bad row item  2  price-per-unit

  Move row down  2
  # Two, three, one
  Wait until   Row item input is  0  code  Two
  Row item input is  0  price-per-unit  bad
  Bad row item  0  price-per-unit

  Row item input is  1  code  Three
  Row item input is  1  price-per-unit  ${EMPTY}
  Good row item  1  price-per-unit

  Row item input is  2  code  One
  Row item input is  2  price-per-unit  10
  Good row item  2  price-per-unit

Add all three rows to aita operation
  Click by test id  view-by-operations
  Wait test id visible  aita-select
  Select from list by test id and label  aita-select  One First item
  Select from list by test id and label  aita-select  Two Second item
  Select from list by test id and label  aita-select  Three
  Test id disabled  aita-select

Row fields are correct(ish)
  Op row field is  aita  Two  code-text  Two Second item
  Op row field is  aita  Two  price-per-unit  bad
  Op row field is  aita  Two  discount-percent  0
  Op row field is  aita  Two  min-total-price  100
  Op row field is  aita  Two  max-total-price  900
  Op row field is  aita  Two  unit  päivä

Removing op row enables the select again
  Click by test id  delete-Three
  No such test id  delete-Three
  Test id enabled  aita-select
  Select from list by test id and label  aita-select  Three
  Op row field is  aita  Three  code-text  Three
  Test id disabled  aita-select

# Two, three, one
Fix the second item and remove third
  Click by test id  view-by-rows
  Sleep  0.1s
  Edit row item  0  price-per-unit  20
  Test id disabled  publish-catalogue
  Delete row  1  False
  Delete row  1
  No such test id  delete-row-2
  Row item input is  1  code  One
  Test id enabled  publish-catalogue

Aita operation is updated, too
  Click by test id  view-by-operations
  No such test id  delete-Three
  Test id disabled  aita-select
  Op row field is  aita  Two  price-per-unit  20

# Two, one
Product constants
  Click by test id  view-by-rows
  No such row item  1  product-constants-kustannuspaikka
  Toggle product constants  1
  Row item visible  1  product-constants-kustannuspaikka
  Edit row item  1  product-constants-kustannuspaikka  Cost center
  Sleep  0.5s
  Toggle product constants  1
  No such row item  1  product-constants-kustannuspaikka

Publish catalogue
  Sleep  0.2s
  Click enabled by test id  publish-catalogue
  Deny yes no dialog
  Click enabled by test id  publish-catalogue
  Confirm yes no dialog
  Wait until  Element should be visible by test id  revert-catalogue

# Two, one
Published row contents
  Row item text is  0  code  Two
  Row item text is  0  text  Second item
  Row item text is  0  price-per-unit  20
  Row item text is  0  discount-percent  0
  Row item text is  0  min-total-price  100
  Row item text is  0  max-total-price  900
  Row item text is  0  unit  päivä
  No such row item  0  toggle-product-constants
  Row item text is  1  code  One
  Row item text is  1  text  First item
  Row item text is  1  price-per-unit  10
  Row item text is  1  discount-percent  0
  Row item text is  1  min-total-price  ${EMPTY}
  Row item text is  1  max-total-price  ${EMPTY}
  Row item text is  1  unit  kpl
  No such row item  1  product-constants-kustannuspaikka
  Toggle product constants  1
  Row item text is  1  product-constants-kustannuspaikka  Cost center
  No such row item  1  product-constants-alv
  Positive indicator should not be visible
  [Teardown]  Click by test id  back


Check taxa page
  Wait test id visible  new-catalogue
  No such test id  delete-catalogue
  No such test id  catalogue-saved-info
  No such test id  publish-catalogue
  No such test id  catalogue-from
  No such test id  add-row
  [Teardown]  Click link  partial link=Taksa


Rows cannot be removed but can be moved
  Wait until  Row item text is  0  code  Two
  No such test id  delete-row-0
  No such test id  delete-row-1
  Move row up  0
  # One, two
  Row item text is  1  code  Two
  Row item text is  1  text  Second item
  Row item text is  1  price-per-unit  20
  Row item text is  1  discount-percent  0
  Row item text is  1  min-total-price  100
  Row item text is  1  max-total-price  900
  Row item text is  1  unit  päivä
  No such row item  1  toggle-product-constants
  Row item text is  0  code  One
  Row item text is  0  text  First item
  Row item text is  0  price-per-unit  10
  Row item text is  0  discount-percent  0
  Row item text is  0  min-total-price  ${EMPTY}
  Row item text is  0  max-total-price  ${EMPTY}
  Row item text is  0  unit  kpl
  Row item text is  0  product-constants-kustannuspaikka  Cost center

Published operations
  Click by test id  view-by-operations
  jQuery should match X times  div.price-catalogue-operations  1
  Op row field is  aita  Two  code-text  Two Second item
  Op row field is  aita  Two  price-per-unit  20
  Op row field is  aita  Two  discount-percent  0
  Op row field is  aita  Two  min-total-price  100
  Op row field is  aita  Two  max-total-price  900
  Op row field is  aita  Two  unit  päivä
  Op row field is  aita  One  code-text  One First item
  Op row field is  aita  One  price-per-unit  10
  Op row field is  aita  One  discount-percent  0
  Op row field is  aita  One  min-total-price  ${EMPTY}
  Op row field is  aita  One  max-total-price  ${EMPTY}
  Op row field is  aita  One  unit  kpl
  No such test id  aita-select
  Positive indicator should not be visible
  [Teardown]  Click by test id  back

Create new draft based on the published catalogue
  # old is not anymore the base
  # Test id text is  new-catalogue  Uusi taksa (pohjana viimeisin julkaistu taksa)
  Test id text is  new-catalogue  Uusi taksa
  Click by test id  new-catalogue
  No such test id  new-catalogue
  Test id enabled  aita-select
  Test id enabled  pientalo-select
  # old is not anymore the base
  # Op row field is  aita  Two  code-text  Two Second item

Valid from is empty and only future is OK
  [Tags]  fail
  Click by test id  view-by-rows
  Test id input is  catalogue-valid-from  ${EMPTY}
  Fill test id  catalogue-valid-from  ${april-fools}
  # FIXME no warning anymore?
  # Wait until element is visible  jquery=input[data-test-id=catalogue-valid-from].warning
  Test id disabled  publish-catalogue
  Fill test id  catalogue-valid-from  ${valid-from-future}
  Wait until  Element should not be visible  jquery=input[data-test-id=catalogue-valid-from].warning
  Test id enabled  publish-catalogue

Publish future catalogue
  [Tags]  fail
  # FIXME future catalogue seems to be different now, check these fail tags below
  Click by test id  publish-catalogue
  Confirm yes no dialog
  Wait until  Test id select text is  select-catalogue  ${valid-from-future} -

Past catalogue valid-until has been updated
  [Tags]  fail
  # FIXME future catalogue seems to be different now
  Select from test id by text  select-catalogue  ${runeberg} - ${valid-until-future}
  Wait until  Test id select text is  select-catalogue  ${runeberg} - ${valid-until-future}
  No such test id  delete-catalogue

Future catalogue can be deleted
  [Tags]  fail
  # FIXME future catalogue seems to be different now
  Select from test id by text  select-catalogue  ${valid-from-future} -
  Test id enabled  delete-catalogue
  Click by test id  delete-catalogue
  Confirm yes no dialog
  No such test id  delete-catalogue

Past catalogue valid-until is unchanged
  [Tags]  fail
  # FIXME future catalogue seems to be different now
  Select from test id by text  select-catalogue  ${runeberg} - ${valid-until-future}
  Wait until  Test id select text is  select-catalogue  ${runeberg} - ${valid-until-future}
  [Teardown]  Logout
