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
  Wait Until  Element Should Be Visible  jquery=[data-test-id=assignee-select]:visible
  Select From List By Label  jquery=[data-test-id=assignee-select]:visible  ${to}

Assign application to nobody
  Wait Until  Element Should Be Visible  jquery=[data-test-id=assignee-select]:visible
  Select From List By Index  jquery=[data-test-id=assignee-select]:visible  0

Application assignee select is
  [Arguments]  ${authority}
  Wait test id visible  assignee-select
  ${text} =  Get Selected List Label  jquery=[data-test-id=assignee-select]
  Wait Until  Should Be Equal  ${text}  ${authority}

Application assignee span is
  [Arguments]  ${authority}
  Wait Until  Element should be visible  jquery=[data-test-id=assignee-span]:visible
  Wait Until  Element Text Should Be  jquery=[data-test-id=assignee-span]:visible  ${authority}
