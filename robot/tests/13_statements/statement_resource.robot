*** Settings ***

Documentation   Statement utils
Resource        ../../common_resource.robot

*** Keywords ***

Open statement
  [Arguments]  ${email}  ${index}=1
  Wait Until  Positive indicator should not be visible
  Scroll to top
  Wait and click  xpath=(//div[@id='application-statement-tab']//a[@data-test-email="open-statement-${email}"])[${index}]
  Wait until  element should be visible  xpath=//div[contains(@class, 'statement-top')]//div[contains(@class, 'tabs-container')]

Select statement giver
  [Arguments]  ${email}
  Wait until  Click element  jquery=tr[data-test-email='${email}'] div.blockbox-wrapper label
  Javascript?  $("tr[data-test-email='${email}'] div.blockbox-wrapper input:checked").length === 1

Invite read-only statement giver
  [Arguments]  ${email}  ${date}
  ${year}=  Execute javascript  return new Date().getFullYear() + 1;
  Scroll to test id  table-application-statements-givers
  # Wait until  Select Checkbox  xpath=//table[@data-test-id='table-application-statements-givers']//tr[@data-test-email='${email}']//input[@type='checkbox']
  Select statement giver  ${email}
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  ${date}${year}
  Wait until  Element should be enabled  xpath=//button[@data-test-id='add-statement-giver']
  Click element  xpath=//button[@data-test-id='add-statement-giver']
  Wait until  Positive indicator should be visible
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Positive indicator should not be visible

Return from statement
  Scroll to test id  statement-return
  Wait and click  xpath=//*[@data-test-id='statement-return']
  Tab should be visible  statement

Set maaraaika-datepicker field value
  [Arguments]  ${tid}  ${date}
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  ${tid}  ${date}
  Test id input is  ${tid}  ${date}

Invite 'manual' statement giver
  [Arguments]  ${index}  ${roletext}  ${name}  ${email}  ${date}
  ${year}=  Execute javascript  return new Date().getFullYear() + 1;
  Test id disabled  statement-giver-role-text-${index}
  Test id disabled  statement-giver-name-${index}
  Test id disabled  statement-giver-email-${index}
  Test id disabled  add-statement-giver
  Toggle not selected  statement-giver-checkbox-${index}
  Toggle toggle  statement-giver-checkbox-${index}
  Test id enabled  statement-giver-role-text-${index}
  Test id required  statement-giver-role-text-${index}
  Test id enabled  statement-giver-name-${index}
  Test id required  statement-giver-name-${index}
  Test id enabled  statement-giver-email-${index}
  Test id required  statement-giver-email-${index}
  Test id disabled  add-statement-giver

  Input text by test id  statement-giver-role-text-${index}  ${roletext}
  Input text by test id  statement-giver-name-${index}  ${name}
  Test id disabled  add-statement-giver
  Input text by test id  statement-giver-email-${index}  ${email}
  Test id enabled  add-statement-giver
  Test id OK  statement-giver-role-text-${index}
  Test id OK  statement-giver-name-${index}
  Test id OK  statement-giver-email-${index}

  # Email must be valid
  Input text by test id  statement-giver-email-${index}  Bad email
  Test id invalid  statement-giver-email-${index}
  Test id disabled  add-statement-giver

  # Non-selected statement givers are ignored
  Toggle toggle  statement-giver-checkbox-${index}
  Toggle toggle  statement-giver-checkbox-0
  Test id enabled  add-statement-giver

  # Good email
  Toggle toggle  statement-giver-checkbox-0
  Toggle toggle  statement-giver-checkbox-${index}
  Test id disabled  add-statement-giver
  Input text by test id  statement-giver-email-${index}  ${email}
  Test id enabled  add-statement-giver

  # Due date must be in the future, if given
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  6.7.2021
  Test id invalid  add-statement-giver-maaraaika
  Test id disabled  add-statement-giver
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  ${date}${year}
  Test id OK  add-statement-giver-maaraaika
  Test id enabled  add-statement-giver
  Click by test id  add-statement-giver

Statement count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id='application-statement-tab']//tr[contains(@class, 'statement-row')]  ${amount}

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
  [Arguments]  ${email}  ${text}  ${name}=${EMPTY}
  ${count} =  Get Matching Xpath Count  //tr[@data-test-type="statement-giver-row"]
  Click enabled by test id  create-statement-giver
  Wait until  Element should be visible  //label[@for='create-statement-giver-email']
  Input text  create-statement-giver-name  ${name}
  Input text  create-statement-giver-email  ${email}
  Input text  create-statement-giver-text  ${text}
  Save modal statement dialog  create-statement-giver
  Wait Until  Page Should Contain  ${email}
  ${countAfter} =  Evaluate  ${count} + 1
  Statement giver count is  ${countAfter}

Statement giver is
  [Arguments]  ${index}  ${email}  ${text}  ${name}
  Scroll to bottom
  Test id text is  'statement-giver-${index}-email'  ${email}
  Test id text is  'statement-giver-${index}-text'  ${text}
  Test id text is  'statement-giver-${index}-name'  ${name}

Review officer count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //tr[@data-test-type="review-officer-row"]  ${amount}

Create review officer
  [Arguments]  ${name}  ${code}
  Click enabled by test id  create-review-officer
  Wait until  Element should be visible  //label[@for='create-review-officer-code']
  Input text  create-review-officer-name  ${name}
  Input text  create-review-officer-code  ${code}
  Save modal statement dialog  create-review-officer

Review officer is
  [Arguments]  ${index}  ${name}  ${code}
  Test id text is  'review-officer-${index}-name'  ${name}
  Test id text is  'review-officer-${index}-code'  ${code}

Open modal statement dialog
  [Arguments]  ${dialog}
  Click enabled by test id  ${dialog}
  Wait until  Element should be visible  //label[@for='${dialog}-name']

Save modal statement dialog
  [Arguments]  ${dialog}
  Click enabled by test id  ${dialog}-save
  Wait until  Element should not be visible  ${dialog}-save

Close modal statement dialog
  [Arguments]  ${dialog}
  Click enabled by test id  ${dialog}-close
