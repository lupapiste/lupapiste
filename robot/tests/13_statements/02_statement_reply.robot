*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        statement_resource.robot

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

Olli requests for five statements
  Olli logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  # We now have 3 statement givers and one empty row (for adding a new statement giver), so there is 4 rows visible
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-3-label']

  Input text  xpath=//*[@id='invite-statement-giver-saateText']  Tama on saateteksti.
  Invite read-only statement giver  olli.uleaborg@ouka.fi  1.6.

  # Checkbox selection and maaraaika are cleared, the saate text stays filled with value.
  Toggle not selected  statement-giver-checkbox-0
  Toggle not selected  statement-giver-checkbox-3
  Wait Until  Textfield Value Should Be  //input[contains(@id,'add-statement-giver-maaraaika')]  ${empty}
  Wait Until  Textarea Value Should Be  //*[@id='invite-statement-giver-saateText']  Tama on saateteksti.

  Invite read-only statement giver  olli.uleaborg@ouka.fi   2.6.
  Invite read-only statement giver  olli.uleaborg@ouka.fi  12.6.
  Invite read-only statement giver  olli.uleaborg@ouka.fi  22.6.
  Invite read-only statement giver  veikko.viranomainen@tampere.fi  3.6.

  Statement count is  5
  [Teardown]  logout

Veikko can see statements as he is being requested a statement to the application
  Veikko logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Statement count is  5

No tabs for Veikko, only the statement view
  Open statement  veikko.viranomainen@tampere.fi
  Wait until element is visible  jquery:div.tabs-container
  Wait until element is not visible  jquery:div.tabs-container ul.tabs

Veikko from Tampere can give statement
  Wait Until  element should be enabled  statement-text
  Input text  statement-text  veikko says its fine
  Select From List By Value  statement-type-select  ehdollinen
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Wait until  Confirm yes no dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-4']  Ehdollinen
  [Teardown]  logout

Olli types in draft
  Olli logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Statement count is  5
  Open statement  olli.uleaborg@ouka.fi  1
  Wait Until  Element should be enabled  statement-text
  Input text  statement-text  typed in statement text but not gonna submit the statement.
  Wait until  Select From List By Value  statement-type-select  puollettu
  Sleep  2.5

Olli peeks reply request tab
  Scroll to top
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should contain  jquery:div.statement-info p  Kun lausunto on annettu
  Element should not be visible  statement-submit
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-statement']
  [Teardown]  Return from statement

Olli gives two statements
  Open statement  olli.uleaborg@ouka.fi  2
  Input text  statement-text  freedom!
  Wait until  Select From List By Value  statement-type-select  puollettu
  Wait and click  statement-submit
  Wait until  Confirm yes no dialog
  Statement status is  Puollettu  olli.uleaborg@ouka.fi  2
  Open statement  olli.uleaborg@ouka.fi  3
  Input text  statement-text  freedom again!
  Wait until  Select From List By Value  statement-type-select  puollettu
  Wait and click  statement-submit
  Wait until  Confirm yes no dialog
  Statement status is  Puollettu  olli.uleaborg@ouka.fi  3

Olli requests reply for statement
  Open statement  olli.uleaborg@ouka.fi  2
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should be visible  statement-submit
  Input text  statement-reply-covering-note  please reply this statement
  Wait and click  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-1']  Vastine pyydetty

Olli requests reply for another statement
  Open statement  olli.uleaborg@ouka.fi  3
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait until  Element should be visible  statement-submit
  Input text  statement-reply-covering-note  please reply also this statement
  Wait and click  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine pyydetty

Olli goes back to statement and sees reply as draft
  Open statement  olli.uleaborg@ouka.fi  2
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

Veikko's statement is not replyable
  Open statement  veikko.viranomainen@tampere.fi
  Wait until element is visible  jquery:div.tabs-container
  Wait until element is not visible  jquery:div.tabs-container ul.tabs
  [Teardown]  Return from statement

Mikko writes reply for Olli's statement
  Wait and click  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//a[@data-test-id='open-statement-reply-1']
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-statement']
  Wait until  Element should be visible  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Element should not be visible  xpath=//li[@data-test-id='statement-tab-selector-reply-request']
  Wait test id visible  statement-nothing-to-add
  Scroll and click test id  statement-nothing-to-add
  Checkbox should be selected  statement-nothing-to-add
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm yes no dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-1']  Vastine annettu

Mikko writes reply for another Olli's statement
  # Mikko sees 2 statemens from Olli
  Open statement  olli.uleaborg@ouka.fi  2
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Wait test id visible  statement-nothing-to-add
  Scroll and click test id  statement-nothing-to-add
  Checkbox should be selected  statement-nothing-to-add
  Scroll and click test id  statement-nothing-to-add
  Checkbox should not be selected  statement-nothing-to-add
  Wait until  Input text  statement-reply-text  this is my reply
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm yes no dialog
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine annettu
  [Teardown]  logout

Olli sees statements are replied
  Olli logs in
  Open application  ${appname}  564-416-25-22
  Open tab  statement
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-1']  Vastine annettu
  Open statement  olli.uleaborg@ouka.fi  2
  Wait and click  xpath=//li[@data-test-id='statement-tab-selector-reply']
  Wait until  Checkbox should be selected  statement-nothing-to-add
  Wait Until  Element should be disabled  statement-reply-text
  Element should be disabled  statement-nothing-to-add
  Return from statement
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-reply-state-2']  Vastine annettu
  Open statement  olli.uleaborg@ouka.fi  3
  Wait until  Textarea Value Should Be  statement-reply-text  this is my reply
  Checkbox should not be selected  statement-nothing-to-add
  Wait Until  Element should be disabled  statement-reply-text
  Element should be disabled  statement-nothing-to-add
