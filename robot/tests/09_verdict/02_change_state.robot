*** Settings ***

Documentation   Authority can switch between post-verdict states
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Mikko wants to build Pytinki
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Pytinki${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted
  [Teardown]  logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

State change select is not visible
  Page Should Not Contain  jquery=[data-test-id=change-state-select]

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  No such test id  verdict-requirements-0
  Wait test id visible  verdict-requirements-1

State change select is now visible
  Application state should be  verdictGiven

Change state to constructionStarted
  Change application state  constructionStarted

Select no longer contains verdictGiven
  Page Should Not Contain Element  jquery=[data-test-id=change-state-select] option[value=verdictGiven]
  Page Should Contain Element  jquery=[data-test-id=change-state-select] option[value=appealed]

Change state to appealed
  Change application state  appealed
  [Teardown]  Logout

State select is not visible to Mikko
  Mikko Logs In
  Open application  ${appname}  753-416-25-30
  Page Should Not Contain  jquery=[data-test-id=change-state-select]
  [Teardown]  Logout



*** Keywords ***

State select check
  [Arguments]  ${state}
  Wait Until  Application state should be  ${state}
  Wait test id visible  change-state-select
  ${select} =  Get Selected List Value  jquery=[data-test-id=change-state-select]
  Wait Until  Should Be Equal  ${select}  ${state}
  Wait Until  Element Should Be Visible  jquery=.state-indication[data-test-state=${state}]

Change application state
  [Arguments]  ${state}
  Select From List By Value  jquery=[data-test-id=change-state-select]  ${state}
  State select check  ${state}
