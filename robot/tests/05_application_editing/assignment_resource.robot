*** Settings ***

Documentation   Assignment utils
Resource        ../../common_resource.robot

*** Keywords ***

#
# Authority assigned to the application
#

Application assignee select empty
  Wait test id visible  assignee-select
  ${value} =  Get Selected List Value  jquery=[data-test-id=assignee-select]
  Wait Until  Should Be Equal  ${value}  ${EMPTY}

Assign application to
  [Arguments]  ${to}
  Wait Until  Element Should Be Visible  //section[@id='application']//select[@data-test-id='assignee-select']
  Select From List By Label  //section[@id='application']//select[@data-test-id='assignee-select']  ${to}
  Positive indicator should be visible

Assign inforequest to
  [Arguments]  ${to}
  Wait Until  Element Should Be Visible  //section[@id='inforequest']//select[@data-test-id='assignee-select']
  Select From List By Label  //section[@id='inforequest']//select[@data-test-id='assignee-select']  ${to}
  Positive indicator should be visible

Assign application to nobody
  Wait Until  Element Should Be Visible  //section[@id='application']//select[@data-test-id='assignee-select']
  Select From List By Index  //section[@id='application']//select[@data-test-id='assignee-select']  0

Application assignee select is
  [Arguments]  ${authority}
  Wait test id visible  assignee-select
  ${text} =  Get Selected List Label  //section[@id='application']//select[@data-test-id='assignee-select']
  Wait Until  Should Be Equal  ${text}  ${authority}

Application assignee span is
  [Arguments]  ${authority}
  Wait Until  Element should be visible  jquery=[data-test-id=assignee-span]:visible
  Wait Until  Element Text Should Be  jquery=[data-test-id=assignee-span]:visible  ${authority}
