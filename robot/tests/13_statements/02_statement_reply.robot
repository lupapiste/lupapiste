*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin goes to admin page and adds statement givers
  Oulu Ymp logs in
  Wait until page contains  Organisaation viranomaiset
  Create statement giver  olli.uleaborg@ouka.fi  Ymparistolausunto
  Create statement giver  veikko.viranomainen@tampere.fi  Tampereen luvat
  Logout

Mikko creates new ymp application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Vapautus viemarista ${secs}
  Create application with state  ${appname}  564-416-25-22  vvvl-viemarista  open
  [Teardown]  logout

Olli sees indicators from pre-filled fields
  Olli logs in

Olli requests for five statements
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  # We now have 3 statement givers and one empty row (for adding a new statement giver), so there is 4 rows visible
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-3']

  Input text  xpath=//*[@id='invite-statement-giver-saateText']  Tama on saateteksti.
  Invite read-only statement giver  0  01.06.2018

  # Checkbox selection and maaraaika are cleared, the saate text stays filled with value.
  Wait Until  Checkbox Should Not Be Selected  statement-giver-checkbox-0
  Checkbox Should Not Be Selected  statement-giver-checkbox-3
  Wait Until  Textfield Value Should Be  //input[contains(@id,'add-statement-giver-maaraaika')]  ${empty}
  Wait Until  Textarea Value Should Be  //*[@id='invite-statement-giver-saateText']  Tama on saateteksti.

  Invite read-only statement giver  1  02.06.2018
  Invite read-only statement giver  1  12.06.2018
  Invite read-only statement giver  1  22.06.2018
  Invite read-only statement giver  2  03.06.2018

  Statement count is  5
  [Teardown]  logout

Veikko can see statements as he is being requested a statement to the application
  Veikko logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Statement count is  5

Only statement tab is visible for Veikko
  Open statement  4
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply']

Veikko from Tampere can give statement
  Wait Until  element should be enabled  statement-text
  Input text  statement-text  veikko says its fine
  Select From List By Value  statement-type-select  ehdoilla
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-4']  Puoltaa ehdoilla
  [Teardown]  logout

Olli types in draft
  Olli logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Statement count is  5
  Open statement  1
  Wait Until  Element should be enabled  statement-text
  Input text  statement-text  typed in statement text but not gonna submit the statement.
  Wait until  Select From List By Value  statement-type-select  puoltaa
  Sleep  2.5

Olli peeks reply request tab
  Scroll to top
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until element contains  xpath=//div[@class='statement-info']//p  Kun lausunto on annettu
  Element should not be visible  statement-submit
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-statement']
  [Teardown]  Return from statement

Olli gives two statements
  Open statement  2
  Input text  statement-text  freedom!
  Wait until  Select From List By Value  statement-type-select  puoltaa
  Wait and click  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-2']  Puoltaa
  Open statement  3
  Input text  statement-text  freedom again!
  Wait until  Select From List By Value  statement-type-select  puoltaa
  Wait and click  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-3']  Puoltaa

Olli requests reply for statement
  Open statement  2
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should be visible  statement-submit
  Input text  statement-reply-covering-note  please reply this statement
  Wait and click  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine pyydetty

Olli requests reply for another statement
  Open statement  3
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should be visible  statement-submit
  Input text  statement-reply-covering-note  please reply also this statement
  Wait and click  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-3']  Vastine pyydetty

Olli goes back to statement and sees reply as draft
  Open statement  2
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Wait until  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Wait until element contains  statement-reply-draft-is-not-visible-info  Vastine tulee
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-statement']
  [Teardown]  logout

Mikko sees statements that are given
  Mikko logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Statement count is  5

Veikkos statement is not replyable
  Open statement  4
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply']
  [Teardown]  Return from statement

Mikko writes reply for Olli's statement
  Wait and click  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//a[@data-test-id='open-statement-reply-2']
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Element should be visible  statement-nothing-to-add
  Click element  statement-nothing-to-add
  Checkbox should be selected  statement-nothing-to-add
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine annettu

Mikko writes reply for another Olli's statement
  Open statement  3
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Wait until  Element should be visible  statement-nothing-to-add
  Click element  statement-nothing-to-add
  Checkbox should be selected  statement-nothing-to-add
  Click element  statement-nothing-to-add
  Checkbox should not be selected  statement-nothing-to-add
  Wait until  Input text  statement-reply-text  this is my reply
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-3']  Vastine annettu
  [Teardown]  logout

Olli sees statements are replied
  Olli logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine annettu
  Open statement  2
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Wait until  Checkbox should be selected  statement-nothing-to-add
  Wait Until  Element should be disabled  statement-reply-text
  Element should be disabled  statement-nothing-to-add
  Return from statement
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-3']  Vastine annettu
  Open statement  3
  Wait until  Textarea Value Should Be  statement-reply-text  this is my reply
  Checkbox should not be selected  statement-nothing-to-add
  Wait Until  Element should be disabled  statement-reply-text
  Element should be disabled  statement-nothing-to-add
  [Teardown]  logout

*** Keywords ***

Set maaraaika-datepicker field value
  [Arguments]  ${id}  ${date}
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text  ${id}  ${date}
  Execute JavaScript  $("#${id}").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'${id}')]  ${date}

Invite read-only statement giver
  [Arguments]  ${index}  ${date}
  Wait until  Select Checkbox  statement-giver-checkbox-${index}
  Set maaraaika-datepicker field value  add-statement-giver-maaraaika  ${date}
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait and click  xpath=//*[@data-test-id='add-statement-giver']
  Element should be visible  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']

Statement count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id='application-statement-tab']//tr[@class="statement-row"]  ${amount}

Return from statement
  Wait and click  xpath=//*[@data-test-id='statement-return']

Open statement
  [Arguments]  ${number}
  Scroll to top
  Wait and Click  xpath=//div[@id='application-statement-tab']//a[@data-test-id='open-statement-${number}']
  Wait until  element should be visible  xpath=//div[@class='statement-top']//div[@class='tabs-container']

Statement is disabled
  Wait until  Element should be disabled  statement-type-select
  Wait until  Element should not be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement is not disabled
  Wait until  Element should be enabled  statement-type-select
  Wait until  Element should be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

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
