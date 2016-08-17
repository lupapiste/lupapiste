*** Settings ***

Documentation   Statement utils
Resource        ../../common_resource.robot

*** Keywords ***

Open statement
  [Arguments]  ${email}  ${index}=1
  Wait Until  Positive indicator should not be visible
  Scroll to top
  Wait and click  xpath=(//div[@id='application-statement-tab']//a[@data-test-email="open-statement-${email}"])[${index}]
  Wait until  element should be visible  xpath=//div[@class='statement-top']//div[@class='tabs-container']

Invite read-only statement giver
  [Arguments]  ${email}  ${date}
  Scroll to test id  table-application-statements-givers
  Wait until  Select Checkbox  xpath=//table[@data-test-id='table-application-statements-givers']//tr[@data-test-email='${email}']//input[@type='checkbox']
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  ${date}
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait and click  xpath=//*[@data-test-id='add-statement-giver']
  Element should be visible  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Positive indicator should be visible
  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait Until  Positive indicator should not be visible

Return from statement
  Scroll to test id  statement-return
  Wait and click  xpath=//*[@data-test-id='statement-return']
  Tab should be visible  statement

Set maaraaika-datepicker field value
  [Arguments]  ${id}  ${date}
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text  ${id}  ${date}
  Execute JavaScript  $("#${id}").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'${id}')]  ${date}

Invite 'manual' statement giver
  [Arguments]  ${index}  ${roletext}  ${name}  ${email}  ${date}
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  ${date}
  Wait Until  Test id enabled  statement-giver-role-text-${index}
  Input text  xpath=//*[@data-test-id='statement-giver-role-text-${index}']  ${roletext}
  Input text  xpath=//*[@data-test-id='statement-giver-name-${index}']  ${name}
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Element should be disabled  xpath=//*[@data-test-id='statement-giver-checkbox-${index}']
  Input text  xpath=//*[@data-test-id='statement-giver-email-${index}']  something@
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Element should be disabled  xpath=//*[@data-test-id='statement-giver-checkbox-${index}']
  Input text  xpath=//*[@data-test-id='statement-giver-email-${index}']  ${email}
  # Statement giver's checkbox can be selected only when all his info fields have content and the email field has a valid email address.
  Wait until  Element should be enabled  xpath=//*[@data-test-id='statement-giver-checkbox-${index}']
  Select Checkbox  statement-giver-checkbox-${index}
  # Send button comes enabled only when all fields have content and some user is selected.
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait and click  xpath=//*[@data-test-id='add-statement-giver']
  Element should be visible  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']

Statement count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id='application-statement-tab']//tr[@class="statement-row"]  ${amount}

Statement is disabled
  Wait until  Element should be disabled  statement-type-select
  Wait until  Element should not be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement is not disabled
  Wait until  Element should be enabled  statement-type-select
  Wait until  Element should be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement status is
  [Arguments]  ${status}  ${email}  ${index}=1
  Wait Until  Element text should be  xpath=(//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//tr[@data-test-email='${email}'])[${index}]//span[@data-test-class='statement-status']  ${status}

Statement giver count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //tr[@data-test-type="statement-giver-row"]  ${amount}

Create statement giver
  [Arguments]  ${email}  ${text}
  ${count} =  Get Matching Xpath Count  //tr[@data-test-type="statement-giver-row"]
  Click enabled by test id  create-statement-giver
  Wait until  Element should be visible  //label[@for='statement-giver-email']
  Input text  statement-giver-email  ${email}
  Input text  statement-giver-email2  ${email}
  Input text  statement-giver-text  ${text}
  Click enabled by test id  create-statement-giver-save
  Wait Until  Element Should Not Be Visible  statement-giver-save
  Wait Until  Page Should Contain  ${email}
  ${countAfter} =  Evaluate  ${count} + 1
  Statement giver count is  ${countAfter}
