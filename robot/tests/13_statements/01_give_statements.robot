*** Settings ***

Documentation   Application statements are managed
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin goes to admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Statement person can be deleted - no questions asked
  Statement person count is  1
  Wait and click  xpath=//a[@data-test-id='remove-statement-person']
  Statement person count is  0

Authorities from own municipality can be added as statement persons
  Create statement person  ronja.sibbo@sipoo.fi  Pelastusviranomainen

Auhtorities from diferent municipality can be added as statement
  Create statement person  veikko.viranomainen@tampere.fi  Tampereen luvat

Auhtority can be a statement person multiple times
  Create statement person  sonja.sibbo@sipoo.fi  Rakennuslausunto
  Create statement person  sonja.sibbo@sipoo.fi  Erityslausunto
  Logout

New applications does not have statements
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Salibandyhalli${secs}
  Create application the fast way  ${appname}  753  753-416-25-22
  Add comment  Salibandyhalli FTW!

  Open tab  statement
  Element should be visible  xpath=//*[@data-test-id='application-no-statements']
  [Teardown]  logout

Sonja sees indicators from pre-filled fields
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-indicators']  2

Sonja adds four statement persons to application
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Element should be visible  xpath=//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-person']
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='check-statement-person-3']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-person-0']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-person-1']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-person-2']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-person-3']
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-person']
  Wait and click  xpath=//*[@data-test-id='add-statement-person']
  Statement count is  4

Sonja can delete statement
  Open statement  3
  Wait and click  xpath=//*[@data-test-id='delete-statement']
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Statement count is  3

Sonja can't give statement to Ronjas statement
  Open statement  0
  Statement is disabled

Sonja can comment on Ronjas statement
  Wait until  Comment count is  statement  0
  Input comment  statement  kas kummaa.
  Wait until  Comment count is  statement  1
  [Teardown]  Return from statement

Sonja can give statement to own request
  Open statement  2
  Sleep  1
  Select From List  statement-type-select  yes
  Input text  statement-text  salibandy on the rocks.
  Wait and click  statement-submit

Comment is added
  Open statement  2
  Sleep  1
  Wait until  Comment count is  statement  1

Sonja can regive statement to own statement
  Select From List  statement-type-select  yes
  Input text  statement-text  salibandy on the rocks.
  Wait and click  statement-submit

Another comment is added
  Open statement  2
  Sleep  1
  Wait until  Comment count is  statement  2

Veikko can see statements as he is beeing requested a statement to the application
  Logout
  Veikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement

Veikko from Tampere can give verdict to own statement
  Open statement  1
  Wait Until  element should be enabled  statement-text
  Input text  statement-text  uittotunnelin vieressa on tilaa.
  Select From List  statement-type-select  condition
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//td[@data-test-name='Veikko Viranomainen']  Puoltaa ehdoilla
  [Teardown]  logout

Sonja can see statement indicator
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-indicators']  3

# add attachment

*** Keywords ***

Statement count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //tr[@class="statement-row"]  ${amount}

Return from statement
  Wait and click  xpath=//*[@data-test-id='statement-return']

Open statement
  [Arguments]  ${number}
  # FIXME: for some weird reason xpath query gets stuck
  Sleep  1
  Wait and Click  xpath=//a[@data-test-id='open-statement-${number}']
  Wait until  element should be visible  statement-type-select

Statement is disabled
  Wait until  Element should be disabled  statement-type-select
  Wait until  Element should not be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement is not disabled
  Wait until  Element should be enabled  statement-type-select
  Wait until  Element should be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement person count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //tr[@class="statement-person-row"]  ${amount}

Create statement person
  [Arguments]  ${email}  ${text}
  ${count} =  Get Matching Xpath Count  //tr[@class="statement-person-row"]
  Wait and click  xpath=//a[@data-test-id='create-statement-person']
  Wait until  Element should be visible  statement-person-email
  Input text       statement-person-email  ${email}
  Input text       statement-person-text  ${text}
  Click element    statement-person-save
  Wait Until  Element Should Not Be Visible  statement-person-save
  Wait Until  Page Should Contain  ${email}
  ${countAfter} =  Evaluate  ${count} + 1
  Statement person count is  ${countAfter}
