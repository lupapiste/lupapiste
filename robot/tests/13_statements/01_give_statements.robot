*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin goes to admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Statement person can be deleted - no questions asked
  Statement person count is  1
  Wait and click  xpath=//a[@data-test-id='remove-statement-giver']
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
  Create application with state  ${appname}  753-416-25-22  kerrostalo-rivitalo  open

  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  [Teardown]  logout

Sonja sees indicators from pre-filled fields
  Sonja logs in
  # The unseen changes count includes changes in property information + "Rakennuksen kayttotarkoitus" and "Huoneistotiedot" documents.
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[@class='lupicon-star']

Sonja adds four statement persons to application
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='check-statement-giver-3']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-giver-0']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-giver-1']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-giver-2']
  Select Checkbox  xpath=//*[@data-test-id='check-statement-giver-3']
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait and click  xpath=//*[@data-test-id='add-statement-giver']
  Statement count is  4

Sonja can delete statement
  Open statement  3
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  xpath=//*[@data-test-id='delete-statement']
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Statement count is  3
  Wait Until  Title Should Be  ${appname} - Lupapiste

Sonja can't give statement to Ronjas statement
  Open statement  0
  Statement is disabled

Sonja can comment on Ronjas statement
  Wait until  Comment count is  0
  Input comment  kas kummaa.
  Wait until  Comment count is  1
  [Teardown]  Return from statement

Sonja can give statement to own request
  Open statement  2
  Wait until  Select From List  statement-type-select  puoltaa
  Input text  statement-text  salibandy on the rocks.
  Wait and click  statement-submit

Comment is added
  Open statement  2
  Wait until  Comment count is  1

Sonja can regive statement to own statement
  Select From List  statement-type-select  puoltaa
  Input text  statement-text  salibandy on the rocks.
  Wait and click  statement-submit

Another comment is added
  Open statement  2
  Wait until  Comment count is  2
  [Teardown]  logout

Veikko can see statements as he is beeing requested a statement to the application
  Veikko logs in
  Open application  ${appname}  753-416-25-22

Statement giver sees comments
  # 1+2 statement comments, 2 auto generated attachments
  Comment count is  5

Statement can export application as PDF
  Element Should Be Visible  xpath=//button[@data-test-id="application-pdf-btn"]

Veikko from Tampere can give verdict to own statement
  Open tab  statement
  Open statement  1
  Wait Until  element should be enabled  statement-text
  Input text  statement-text  uittotunnelin vieressa on tilaa.
  Select From List  statement-type-select  ehdoilla
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//td[@data-test-name='Veikko Viranomainen']  Puoltaa ehdoilla
  [Teardown]  logout

Sonja can see statement indicator
  Sonja logs in
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[@class='lupicon-star']

# add attachment

*** Keywords ***

Statement count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id='application-statement-tab']//tr[@class="statement-row"]  ${amount}

Return from statement
  Wait and click  xpath=//*[@data-test-id='statement-return']

Open statement
  [Arguments]  ${number}
  Wait and Click  xpath=//div[@id='application-statement-tab']//a[@data-test-id='open-statement-${number}']
  Wait until  element should be visible  statement-type-select

Statement is disabled
  Wait until  Element should be disabled  statement-type-select
  Wait until  Element should not be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement is not disabled
  Wait until  Element should be enabled  statement-type-select
  Wait until  Element should be visible  xpath=//section[@id="statement"]//button[@data-test-id="add-statement-attachment"]

Statement person count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //tr[@class="statement-giver-row"]  ${amount}

Create statement person
  [Arguments]  ${email}  ${text}
  ${count} =  Get Matching Xpath Count  //tr[@class="statement-giver-row"]
  Click enabled by test id  create-statement-giver
  Wait until  Element should be visible  //label[@for='statement-giver-email']
  Input text  statement-giver-email  ${email}
  Input text  statement-giver-email2  ${email}
  Input text  statement-giver-text  ${text}
  Click enabled by test id  create-statement-giver-save
  Wait Until  Element Should Not Be Visible  statement-giver-save
  Wait Until  Page Should Contain  ${email}
  ${countAfter} =  Evaluate  ${count} + 1
  Statement person count is  ${countAfter}
