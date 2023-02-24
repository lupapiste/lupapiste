*** Settings ***

Documentation   Stamping functionality for authority
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        stamping_resource.robot
Variables       variables.py

*** Variables ***

${STAMP_TEXT}              Hyvaksyn
${STAMP_DATE}              12.12.2012
${STAMP_ORGANIZATION}      LupaRobot
${STAMP_XMARGIN}           -12
${STAMP_YMARGIN}           88
${STAMP_TRANSPARENCY_IDX}  2
${STAMP_EXTRATEXT}         Lisateksti
${DEF_STAMP_ORGANIZATION}  Sipoon rakennusvalvonta
${DEF_STAMP_USER}          Sonja Sibbo
${appname1}                Stamping One
${appname2}                Stamping Two
${stamp-info-fields-path}  //div[@id="stamping-container"]//form[@id="stamp-info"]

*** Test Cases ***

Mikko logs in, creates and submits first application
  Mikko logs in
  Create application the fast way  ${appname1}  753-416-25-30  asuinrakennus
  Submit application

Add PDF attachment
  Open tab  attachments
  Upload attachment  ${PDF_TESTFILE_PATH1}  Muu liite  Muu  Uusi asuinrakennus

Mikko creates & submits a second application and goes to empty attachments tab
  Create application the fast way  ${appname2}  753-416-25-30  asuinrakennus
  Submit application
  Open tab  attachments

Mikko adds PDF attachment without comment
  Upload attachment  ${PDF_TESTFILE_PATH1}  Muu liite  Muu  Uusi asuinrakennus
  Upload attachment  ${PDF_TESTFILE_PATH2}  IV-suunnitelma  IV  Uusi asuinrakennus
  Upload attachment  ${PDF_TESTFILE_PATH3}  Muu liite  Muu  Yleisesti hankkeeseen
  Upload attachment  ${PDF_TESTFILE_PATH4}  Muu liite  Koe  Yleisesti hankkeeseen

Mikko does not see stamping button
  Wait until  Element should not be visible  jquery=button[data-test-id=stamp-attachments]

Mikko previews file
  [Tags]  attachments
  Open attachment details  muut.muu
  Scroll and click test id  file-preview
  Wait Until  Element should be visible  file-preview-iframe

Version number is 1.0
  Wait until  Element text should be  test-attachment-version  1.0

Mikko adds new version
  Add attachment version  ${PDF_TESTFILE_PATH3}

Version number is 2.0
  Wait until  Element text should be  test-attachment-version  2.0

Sonja logs in
  Logout
  Sonja logs in

Sonja goes to attachments tab
  Open application  ${appname2}  753-416-25-30
  Open tab  attachments

Sonja sees that attachment is not stamped
  Attachment should not be stamped  muut.muu

Sonja marks attachment as verdict attachment
  Mark attachment as verdict attachment

Sonja opens the first application
  Open application  ${appname1}  753-416-25-30
  Open tab  attachments

Sonja prepares for stamping
  Mark attachment as verdict attachment
  Open stamping page  ${appname1}
  One attachment is selected
  Default Stamping Info Fields status

Sonja changes stamp info
  Select From List by test id and index  stamp-selected  1
  KV stamp fields status
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]  ${DEF_STAMP_ORGANIZATION}
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]  ${DEF_STAMP_USER}
  Input text by test id  stamp-info-organization  Changed organization
  Input text by test id  stamp-info-user  Changed user
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]  Changed organization
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]  Changed user

Stamp values should be saved when changing stamp
  Select From List by test id and index  stamp-selected  0
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]  ${DEF_STAMP_ORGANIZATION}
  Select From List by test id and index  stamp-selected  1
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]  Changed organization
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]  Changed user

Sonja revert changes
  Scroll and click test id  stamp-reload
  Wait until  List selection should be  xpath=//select[@data-test-id="stamp-selected"]  Oletusleima
  Select From List by test id and index  stamp-selected  1
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]  ${DEF_STAMP_ORGANIZATION}
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]  ${DEF_STAMP_USER}
  Select From List by test id and index  stamp-selected  0
  Input stamping info values

Sonja goes back to the second application
  Open application  ${appname2}  753-416-25-30
  Open tab  attachments

Sonja opens stamping page and sees info fields initialized
  Open stamping page  ${appname2}
  One attachment is selected
  Default Stamping Info Fields status
  Test id input is  stamp-info-organization  Sipoon rakennusvalvonta

Sonja inputs info values
  Input stamping info values
  Test id input is  stamp-info-organization  LupaRobot

Sonja can go to attachments tab. When she returns, only extra text field is persistent
  Select From List by test id and index  stamp-selected  1
  Input text by test id  stamp-info-extratext  ${STAMP_EXTRATEXT}
  Wait until  Scroll and click test id  back-to-application-from-stamping
  Wait until  Element should be visible  application-attachments-tab
  Scroll and click test id  stamp-attachments
  Wait Until  Element should be visible  stamp-info
  List selection should be  xpath=//select[@data-test-id="stamp-selected"]  KV-leima
  One attachment is selected
  # Note: stamp-organization has changed from LupaRobot:
  Test id input is  stamp-info-organization  Sipoon rakennusvalvonta
  Textfield value should be  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-extratext"]  ${STAMP_EXTRATEXT}

Sonja can toggle selection of attachments by group/all/none
  Scroll and click  div#stamping-container a[data-test-id=select-all]
  Scroll and click  div#stamping-container a[data-test-id=select-none]
  Click group selection link  HAKEMUKSEN LIITTEET  select
  Number of selected attachments should be  2
  Click group selection link  HAKEMUKSEN LIITTEET  deselect
  Number of selected attachments should be  0
  Click group selection link  HAKEMUKSEN LIITTEET  select
  Click group selection link  IV-SUUNNITELMAT  select
  Number of selected attachments should be  3
  Scroll and click  div#stamping-container a[data-test-id=select-all]
  Number of selected attachments should be  4
  Scroll and click  div#stamping-container a[data-test-id=select-none]
  Number of selected attachments should be  0

Status of stamping is ready
  Element text should be  xpath=//div[@id="stamping-container"]//span[@data-test-id="stamp-status-text"]  Valmiina leimaamaan liitteet

Select all files and start stamping
  Click element  xpath=//div[@id="stamping-container"]//a[@data-test-id="select-all"]
  Scroll to bottom
  Click element  xpath=//div[@id="stamping-container"]//button[@data-test-id="start-stamping"]
  Xpath should match x times  //div[@id="stamping-container"]//span[@data-test-id="attachment-status-text"]  4
  Sleep  8s
  Wait Until  Element text should be  xpath=//div[@id="stamping-container"]//span[@data-test-id="stamp-status-text"]  Leimaus valmis

There were no errors
  Element Should not contain  //span[@data-test-id="attachment-status-text"]  Virhe

Reset stamping, stamping page should be refreshed
  Scroll and click test id  stamp-reset
  Wait until  Element should be visible  stamping-container
  Wait Until  Element text should be  xpath=//div[@id="stamping-container"]//span[@data-test-id="stamp-status-text"]  Valmiina leimaamaan liitteet
  Xpath should match x times  //div[@id="stamping-container"]//tr[contains(@class,'selected')]  0

Return from stamping to attachments tab
  Scroll to bottom
  Click element  xpath=//div[@id="stamping-container"]//button[@data-test-id="cancel-stamping"]
  Element should be visible  application-attachments-tab

Attachment has stamped icon
  Wait until  Attachment should be stamped  muut.muu

Sonja starts stamping again
  Open stamping page  ${appname2}

No attachments are selected
  Xpath should match x times  //div[@id="stamping-container"]//tr[contains(@class,'selected')]  0

Selection and stamping shows confirmation dialog
  Click group selection link  HAKEMUKSEN LIITTEET  select
  Scroll to bottom
  Click element  xpath=//div[@id="stamping-container"]//button[@data-test-id="start-stamping"]
  # Prompt about re-stamping
  Deny yes no dialog
  Click element  xpath=//div[@id="stamping-container"]//button[@data-test-id="start-stamping"]
  Confirm yes no dialog
  Wait Until  Element text should be  xpath=//div[@id="stamping-container"]//span[@data-test-id="stamp-status-text"]  Leimaus valmis
  Logout

Mikko checks status after stamping
  Mikko logs in
  Open application  ${appname2}  753-416-25-30
  Open tab  attachments
  # Four stamped icons, as uploaded
  Xpath should match X times  //div[@id='application-attachments-tab']//i[@data-test-icon='stamped-icon']  4
  # Not able to delete attachments LPK-3335
  Xpath should match X times  //div[@id='application-attachments-tab']//button[@data-test-icon='delete-button' and @disabled]  4
  Logout

Frontend errors check
  There are no frontend errors
