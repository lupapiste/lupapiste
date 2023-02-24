*** Settings ***

Documentation   Default digitalization coordinates
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Jarvenpaa authority admin logs in
  Jarvenpaa admin logs in
  Go to page  archiving

Input good coordinates
  Input X  395582.709  False
  Input Y  6702820.041

Bad X
  Input X  10  False
  Input X  395582.709

Bad Y
  Input Y  20  False
  Input Y  6702820.041

Commas and whitespace are OK
  Input X  3 95 7 94, 1 69 9375
  Input Y  67 0 2 701 , 1 6 6

Coordinates survive reload
  Reload page
  Wait until  Textfield value should be  default-digitalization-x  395794.1699375
  Wait until  Textfield value should be  default-digitalization-y  6702701.166
  [Teardown]  Logout


*** Keywords ***

Discard positive indicator
  Positive indicator should be visible
  Positive indicator should not be visible

Check result
  [Arguments]  ${good}
  Run keyword if  ${good}  Discard positive indicator
  Run keyword unless  ${good}  Close sticky indicator

Input X
  [Arguments]  ${x}  ${good}=True
  Input text with jQuery  input#default-digitalization-x  ${x}
  Check result  ${good}

Input Y
  [Arguments]  ${y}  ${good}=True
  Input text with jQuery  input#default-digitalization-y  ${y}
  Check result  ${good}
