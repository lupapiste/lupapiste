*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Authority admin goes to admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Statement giver can be deleted - no questions asked
  Statement giver count is  1
  Wait and click  xpath=//a[@data-test-id='remove-statement-giver']
  Statement giver count is  0

Authorities from own municipality can be added as statement giver
  Create statement giver  ronja.sibbo@sipoo.fi  Pelastusviranomainen

Authorities from different municipality can be added as statement giver
  Create statement giver  veikko.viranomainen@tampere.fi  Tampereen luvat

Authority can be a statement giver multiple times
  Create statement giver  luukas.lukija@sipoo.fi  Rakennuslausunto
  Create statement giver  sonja.sibbo@sipoo.fi  Erityislausunto
  Logout

New applications do not have statements
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Salibandyhalli${secs}
  Set Suite Variable  ${appPropertyId}  753-416-25-22
  Create application with state  ${appname}  ${appPropertyId}  kerrostalo-rivitalo  open

  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  [Teardown]  logout

Sonja sees indicators from pre-filled fields
  Sonja logs in
  # The unseen changes count includes changes in property information + "Rakennuksen kayttotarkoitus" and "Huoneistotiedot" documents.
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[@class='lupicon-star']

Sonja adds five statement givers to application
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  # We now have 4 statement givers and one empty row (for adding a new statement giver), so there is 5 rows visible
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-4']

  Wait until  Input text  invite-statement-giver-saateText  Tama on saateteksti.
  Invite read-only statement giver  ronja.sibbo@sipoo.fi  01.06.2018
  Open statement  ronja.sibbo@sipoo.fi
  Wait until  Element should be visible  statement-cover-note
  Wait until  Element text should be  statement-cover-note  Tama on saateteksti.
  Return from statement

  # Checkbox selection and maaraaika are cleared, the saate text stays filled with value.
  Wait Until  Checkbox Should Not Be Selected  statement-giver-checkbox-0
  Checkbox Should Not Be Selected  statement-giver-checkbox-4
  Wait Until  Textfield Value Should Be  //input[contains(@id,'add-statement-giver-maaraaika')]  ${empty}
  Wait Until  Textarea Value Should Be  //*[@id='invite-statement-giver-saateText']  Tama on saateteksti.

  Invite read-only statement giver  veikko.viranomainen@tampere.fi  02.06.2018
  Invite read-only statement giver  veikko.viranomainen@tampere.fi  12.06.2018
  Invite read-only statement giver  luukas.lukija@sipoo.fi  03.06.2018
  Invite read-only statement giver  sonja.sibbo@sipoo.fi  04.06.2018

  # Invite a new statement giver that is not on the ready-populated list that authority admin has added in his admin view.
  Invite 'manual' statement giver  4  Erikoislausuja  Vainamoinen  vainamoinen@example.com  05.06.2018

  Statement count is  6

Sonja can delete statement
  Scroll and click test id  delete-statement-5
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Statement count is  5
  Wait Until  Title Should Be  ${appname} - Lupapiste

Sonja can't give statement to Ronjas statement nor see the draft
  Open statement  ronja.sibbo@sipoo.fi
  Wait until  Element should contain  //div[@class="statement-info"]//p  Lausunto tulee
  Element should not be visible  statement-type-select
  Element should not be visible  statement-text

Sonja can comment on Ronjas statement
  Wait until  Comment count is  0
  Input comment  kas kummaa.
  Wait until  Comment count is  1
  [Teardown]  Return from statement

Sonja types in draft
  Open statement  sonja.sibbo@sipoo.fi
  Wait Until  Element should be enabled  statement-text
  Input text  statement-text  typed in statement text but not gonna submit the statement.
  Wait until  Select From List By Value  statement-type-select  puollettu
  Positive indicator icon should be visible
  Reload Page
  Wait Until  Text area should contain  statement-text  typed in statement text but not gonna submit the statement.

Sonja adds attachment to statement draft
  Wait test id visible  statement-attachments-no-attachments
  Scroll and click test id  add-statement-attachment
  Add attachment  statement  ${PDF_TESTFILE_PATH}  Important note

Attachment comment appears
  Wait Until  Element should contain  jquery=table[data-test-id=statement-attachments-table] span  Important note

Sonja removes attachment from statement draft
  Scroll to test id  add-statement-attachment
  Click element  jquery=table[data-test-id=statement-attachments-table] i.lupicon-remove
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element Should Not Be Visible  jquery=table[data-test-id=statement-attachments-table]
  [Teardown]  Return from statement

Sonja can give statement to own request
  Open statement  sonja.sibbo@sipoo.fi
  Wait until  Element text should be  xpath=//div[@data-test-id='header-statement-edit']//span[@data-bind='text: person.text']  ERITYISLAUSUNTO
  Input text  statement-text  salibandy on the rocks.
  Select From List By Value  statement-type-select  puollettu
  Wait and click  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should be visible  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']

Comment is added
  Open statement  sonja.sibbo@sipoo.fi
  Wait until  Comment count is  1

Sonja cannot regive statement to own statement
  Statement is disabled
  Wait until  Element should not be visible  statement-submit
  [Teardown]  Return from statement

Attachment is generated
  Open tab  attachments
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto']

Attachment is not removable from list view
  Wait until  Element should be disabled  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto'] button[data-test-icon=delete-button]

Attachement is not removable from attachment view
  Open attachment details  ennakkoluvat_ja_lausunnot.lausunto
  Wait until  Element should contain  //span[@id='test-attachment-file-name']/a  Lausunto.pdf
  Element should not be visible  //button[@data-test-id='delete-attachment']

Attachment version is not removable
  Wait and click  show-attachment-versions
  Wait until  Element should be visible  //tr[@data-test-id='version-row-0.1']
  Element should not be visible  //tr[@data-test-id='version-row-0.1']//a[@data-test-id='delete-version']

Return to application
  Scroll and click test id  back-to-application-from-attachment

Statement status is visible for given statement in summary table
  Open tab  statement
  Wait Until  Element text should be  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-4']  Puollettu

...but not for draft
  Element should not be visible  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']//span[@data-test-id='statement-status-3']

Draft is removable
  Element should be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-3']

...but given statement is not
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-4']
  [Teardown]  logout

Veikko can see statements as he is being requested a statement to the application
  Veikko logs in
  Open application  ${appname}  ${appPropertyId}

Statement giver sees comments
  # 1+1 statement comments, 1 auto generated attachment, 1 added (and later removed) attachment.
  Comment count is  4

Statement can export application as PDF
  Element Should Be Visible  xpath=//button[@data-test-id="application-pdf-btn"]

Statements are visible for Veikko
  Open tab  statement
  Statement count is  5

Veikko can delete his own statement but no others
  Element should be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-1']
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-3']

Veikko from Tampere can give statement (and attach something to it as well)
  Open statement  veikko.viranomainen@tampere.fi
  Wait Until  element should be enabled  statement-text
  Input text  statement-text  uittotunnelin vieressa on tilaa.
  Add attachment  statement  ${TXT_TESTFILE_PATH}  ${EMPTY}  ${EMPTY}  ennakkoluvat_ja_lausunnot.lausunto
  Select From List By Value  statement-type-select  ehdollinen
  Wait until  Element Should Be Enabled  statement-submit
  Click Element  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Statement status is  Ehdollinen  veikko.viranomainen@tampere.fi
  Logout

Sonja can see statement indicator
  Sonja logs in
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[@class='lupicon-star']

There is no possibility to delete the generated statement pdf attachment
  Open application  ${appname}  ${appPropertyId}
  Open tab  attachments
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto']
  Element should not be visible  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto'] span[data-test-icon="delete-button"]
  [Teardown]  Logout

Mikko logs in and submits application
  Mikko logs in
  Open application  ${appname}  ${appPropertyId}
  Submit application
  [Teardown]  Logout

Sonja logs in and approves application
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Approve application yes

Sonja cannot invite any more statement givers
  Open tab  statement
  No such test id  add-statement
  [Teardown]  Logout

Luukas logs in but cannot edit statement
  Luukas logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  luukas.lukija@sipoo.fi
  Wait until  Element should not be visible  statement-submit
  Element should not be visible  jquery=#statement-submit
  [Teardown]  Return from statement

Luukas can still delete (empty) statement
  Scroll and click test id  delete-statement-3
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  jquery=tr.statement-row i.lupicon-remove
  [Teardown]  Logout
