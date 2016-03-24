*** Settings ***

Documentation   Appeals management
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Mikko wants to build Skyscraper
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Skyscraper${secs}
  Create application with state  ${appname}  753-416-25-30  kerrostalo-rivitalo  submitted
  [Teardown]  logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  Page Should Contain Element  //div[@data-test-id="given-verdict-id-0-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]
  Page Should Not Contain Element  //div[@data-test-id="given-verdict-id-1-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]

There are no appeals yet
  Page should not contain  jquery=table.appeals-table

Sonja adds appeal to the first verdict
  Add to verdict  0  appeal  Veijo  1.4.2016  Hello world


*** Keywords ***

Add to verdict
  [Arguments]  ${verdict}  ${appealType}  ${authors}  ${date}  ${extra}=${EMPTY}
  Log to console  ${verdict} - ${appealType} - ${authors} - ${date} - ${extra}
  Scroll and click test id  add-appeal-${verdict}
  Test id disabled  appeal-${verdict}-bubble-dialog-ok
  Select From List By Value  jquery=select[data-test-id=appeal-type-${verdict}]  ${appealType}
  Test id disabled  appeal-${verdict}-bubble-dialog-ok
  Fill test id  appeal-authors-${verdict}  ${authors}
  Test id disabled  appeal-${verdict}-bubble-dialog-ok
  Fill test id  appeal-date-${verdict}  ${date}
  Test id disabled  appeal-${verdict}-bubble-dialog-ok
  Fill test id  appeal-date-${verdict}  ${extra}
  Sleep  1m
