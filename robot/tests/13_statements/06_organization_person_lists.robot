*** Settings ***

Documentation   Statement givers and review officers can be added, edited and removed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

# Statement givers list
Authority admin goes to admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Statement givers can be deleted
  Statement giver count is  1
  Wait and click  xpath=//button[@data-test-id='remove-statement-giver']
  Statement giver count is  0

Authorities from own municipality can be added as statement giver
  Create statement giver  lassi.lausuja@sipoo.fi  Lausuja  Lassi Lausuja
  Statement giver is  0  lassi.lausuja@sipoo.fi  Lausuja  Lassi Lausuja

Multiple statement givers can be added and they are sorted into alphabetical order
  Create statement giver  sanna.lausuja@sipoo.fi  Lausuja  Sanna Lausuja
  Create statement giver  rakel.lausuja@sipoo.fi  Lausuja  Rakel Lausuja
  Statement giver is  1  rakel.lausuja@sipoo.fi  Lausuja  Rakel Lausuja

Statement giver can be edited with valid values
  Open modal statement dialog  edit-statement-giver
  Input text  edit-statement-giver-name  Arvi Arvo
  Input text  edit-statement-giver-text  Arvuuttelu
  Element should be disabled  xpath=//input[@id='edit-statement-giver-email']
  Save modal statement dialog  edit-statement-giver
  Statement giver is  0  lassi.lausuja@sipoo.fi  Arvuuttelu  Arvi Arvo

Statement giver cannot be edited with invalid values
  Open modal statement dialog  edit-statement-giver
  Input text  edit-statement-giver-name  Vaari Veteraani
  Input text  edit-statement-giver-text  ${EMPTY}
  Element should be disabled  xpath=//button[@data-test-id='edit-statement-giver-save']
  Close modal statement dialog  edit-statement-giver
  Statement giver is  0  lassi.lausuja@sipoo.fi  Arvuuttelu  Arvi Arvo

# Review officers list
Review officers can be added
  Navigate from sidebar to  reviews
  Click by test id  review-officer-toggle-label
  Review officer count is  0
  Create review officer  Kalle Katselmoija  kalle-koodi
  Review officer is  0  Kalle Katselmoija  kalle-koodi
  Review officer count is  1

Multiple review officers can be added and they are sorted into alphabetical order
  Create review officer  Tapani Raunio  tapani-koodi
  Create review officer  Mauno Mainio  mauno-koodi
  Create review officer  Pirita Parkko  pirita-koodi
  Review officer count is  4
  Review officer is  1  Mauno Mainio  mauno-koodi

A review officer cannot be added with the same code as an existing one
  Create review officer  Tauno Tupla  mauno-koodi
  Element should be visible  review-officer-error
  Close modal statement dialog  create-review-officer

Review officer can be edited with valid values
  Open modal statement dialog  edit-review-officer
  Input text  edit-review-officer-name  Kalju Katselmoija
  Element should be disabled  xpath=//input[@id='edit-review-officer-code']
  Save modal statement dialog  edit-review-officer
  Review officer is  0  Kalju Katselmoija  kalle-koodi

Review officer cannot be edited with invalid values
  Open modal statement dialog  edit-review-officer
  Input text  edit-review-officer-name  ${EMPTY}
  Element should be disabled  xpath=//button[@data-test-id='edit-review-officer-save']
  Close modal statement dialog  edit-review-officer
  Review officer is  0  Kalju Katselmoija  kalle-koodi

Review officer list can be toggled on and off
  Scroll to test id  review-officer-toggle-label
  Click by test id  review-officer-toggle-label
  Element should not be visible by test id  create-review-officer
  Element should not be visible by test id  remove-review-officer
  Click by test id  review-officer-toggle-label
  Element should be visible by test id  create-review-officer
  Element should be visible by test id  remove-review-officer

Review officers can be deleted
  Review officer count is  4
  Wait and click  xpath=//button[@data-test-id='remove-review-officer']
  Wait and click  xpath=//button[@data-test-id='remove-review-officer']
  Wait and click  xpath=//button[@data-test-id='remove-review-officer']
  Wait and click  xpath=//button[@data-test-id='remove-review-officer']
  Review officer count is  0
