*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        statement_resource.robot
Resource        ../20_side_panel/notice_resource.robot
Variables       ../06_attachments/variables.py
Variables       ../../common_variables.py

*** Test Cases ***

Authority admin goes to admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Statement giver can be deleted - no questions asked
  Statement giver count is  1
  Wait and click  xpath=//button[@data-test-id='remove-statement-giver']
  Statement giver count is  0

Authorities from own municipality can be added as statement giver
  Create statement giver  ronja.sibbo@sipoo.fi  Pelastusviranomainen
  Statement giver is  0  ronja.sibbo@sipoo.fi  Pelastusviranomainen  Ronja Sibbo

Authorities from different municipality can be added as statement giver
  Create statement giver  veikko.viranomainen@tampere.fi  Tampereen luvat  Veikko the Man
  Statement giver is  1  veikko.viranomainen@tampere.fi  Tampereen luvat  Veikko Viranomainen

Authority can be a statement giver multiple times
  Create statement giver  luukas.lukija@sipoo.fi  Rakennuslausunto
  Create statement giver  sonja.sibbo@sipoo.fi  Erityislausunto

Invite non-existing user as a statement giver
  Create statement giver  bob@example.com  Builder  Bob
  Statement giver is  0  bob@example.com  Builder  Bob

Invite non-existing user as a statement giver, non name given
  Create statement giver  dot@example.com  Designer
  Statement giver is  1  dot@example.com  Designer  dot@example.com

Bad email results in an error message
  Click enabled by test id  create-statement-giver
  Wait until  Element should be visible  //label[@for='create-statement-giver-email']
  Element should not be visible  statement-giver-error
  Input text  create-statement-giver-name  Bad
  Input text  create-statement-giver-email  bademail
  Input text  create-statement-giver-text  Bad
  Click enabled by test id  create-statement-giver-save
  Wait until    Element should be visible  statement-giver-error
  [Teardown]  Logout

New applications do not have statements
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Salibandyhalli${secs}
  Set Suite Variable  ${appPropertyId}  753-416-25-22
  Create application with state  ${appname}  ${appPropertyId}  kerrostalo-rivitalo  open

  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Logout

Sonja sees indicators from pre-filled fields
  Sonja logs in
  # The unseen changes count includes changes in property information + "Rakennuksen kayttotarkoitus" and "Huoneistotiedot" documents.
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[contains(@class,'lupicon-star')]

Sonja adds five read-only statement givers to application
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Element should be visible  xpath=//div[@id='application-statement-tab']//*[@data-test-id='application-no-statements']
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  # We now have 6 statement givers and one empty row (for adding a new statement giver), so there is 7 rows visible
  Wait until  Page Should Contain Element  xpath=//*[@data-test-id='statement-giver-checkbox-6-label']

  Wait until  Input text  invite-statement-giver-saateText  Tama on saateteksti.
  Invite read-only statement giver  ronja.sibbo@sipoo.fi  1.6.
  Open statement  ronja.sibbo@sipoo.fi
  Wait until  Element should be visible  statement-cover-note
  Wait until  Element text should be  statement-cover-note  Tama on saateteksti.
  Return from statement

  # Checkbox selection and maaraaika are cleared, the saate text stays filled with value.
  Toggle not selected  statement-giver-checkbox-0
  Toggle not selected  statement-giver-checkbox-6
  Wait Until  Textfield Value Should Be  //input[contains(@id,'add-statement-giver-maaraaika')]  ${empty}
  Wait Until  Textarea Value Should Be  //*[@id='invite-statement-giver-saateText']  Tama on saateteksti.

  Invite read-only statement giver  veikko.viranomainen@tampere.fi  2.6.
  Invite read-only statement giver  veikko.viranomainen@tampere.fi  12.6.
  Invite read-only statement giver  luukas.lukija@sipoo.fi  3.6.
  Invite read-only statement giver  sonja.sibbo@sipoo.fi  4.6.

Sonja adds non-existing user as a statement giver
  # Invite a new statement giver that is not on the ready-populated list that authority admin has added in his admin view.
  No such test id  statement-giver-role-text-7
  Invite 'manual' statement giver  6  Erikoislausuja  Vainamoinen  vainamoinen@example.com  5.6.
  Wait test id visible  statement-giver-role-text-7
  Test id disabled  statement-giver-role-text-7


Sonja adds an existing applicant user as a statement giver
  # Invite existing non-authority as a statement giver
  Invite 'manual' statement giver  7  Pena  Pena  pena@example.com  8.6.
  Statement count is  7
  Positive indicator should not be visible

Sonja can delete statement
  Element should be visible by test id  delete-statement-5
  Wait test id visible  delete-statement-column
  Element should be visible  jquery:button[aria-label='Poista lausunto']
  Scroll and click test id  delete-statement-5
  Confirm yes no dialog
  Wait until  Statement count is  6
  Wait Until  Title Should Be  ${appname} - Lupapiste

Sonja can't give statement to Ronjas statement nor see the draft
  Open statement  ronja.sibbo@sipoo.fi
  Wait until  Element should contain  //div[contains(@class, 'statement-info')]//p  Lausunto tulee
  Element should not be visible  statement-type-select
  Element should not be visible  statement-text

Sonja can comment on Ronjas statement
  Wait until  Comment count is  0
  Input comment  kas kummaa.
  Wait until  Comment count is  1
  Return from statement

Sonja types in draft
  Open statement  sonja.sibbo@sipoo.fi
  Wait Until  Element should be enabled  statement-text
  Input text  statement-text  typed in statement text but not gonna submit the statement.
  Wait until  Select From List By Value  statement-type-select  puollettu
  Positive indicator icon should be visible
  Reload page and kill dev-box
  Wait Until  Text area should contain  statement-text  typed in statement text but not gonna submit the statement.

Sonja cannot change draft's due date to past
  Wait Until  Element should be enabled  due-date-input
  Input Text by test id  due-date-input  18.8.2020
  Press Keys  due-date-input  RETURN
  Press Keys  due-date-input  RETURN
  Negative indicator should be visible
  Close sticky indicator

Sonja changes draft's due date to future
  ${year}=  Execute javascript  return new Date().getFullYear() + 1;
  Wait Until  Element should be enabled  due-date-input
  Input Text by test id  due-date-input  31.12.${year}
  Press Keys  due-date-input  RETURN
  Press Keys  due-date-input  RETURN
  Reload page and kill dev-box
  Wait Until  Element should contain  jquery:label[for=due-date-input]  Lausunnon määräaika (31.12.${year})
  Test id input is  due-date-input  31.12.${year}

Sonja adds attachment to statement draft
  Upload attachment with default type  ${PDF_TESTFILE_PATH}

Attachment list appears
  Wait Until  Element should contain  xpath=//table[@data-test-id="targetted-attachments-table"]//span  Erityislausunto, liite

Sonja removes attachment from statement draft
  Scroll to test id  targetted-attachments-table
  Click element  jquery=table[data-test-id=targetted-attachments-table] i.lupicon-remove
  Confirm yes no dialog
  Wait until  Element Should Not Be Visible  //table[@data-test-id="targetted-attachments-table"]
  Return from statement

Sonja can give statement to own request
  Open statement  sonja.sibbo@sipoo.fi
  Wait until  Element text should be  xpath=//div[@data-test-id='header-statement-edit']//span[@data-bind='text: person.text || person.name']  ERITYISLAUSUNTO
  Input text  statement-text  salibandy on the rocks.
  Select From List By Value  statement-type-select  puollettu
  Wait and click  statement-submit
  Confirm yes no dialog
  Wait Until  Element should be visible  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']

Comment is added
  Open statement  sonja.sibbo@sipoo.fi
  Wait until  Comment count is  1

Sonja cannot regive statement to own statement
  Statement is disabled
  Wait until  Element should not be visible  statement-submit
  Return from statement

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
  Wait test id visible  application-open-statement-tab
  Scroll to top
  Open tab  statement
  Test id text is  statement-status-4  Puollettu

...but not for draft
  No such test id  statement-status-3

Draft is removable
  Wait test id visible  delete-statement-3

...but given statement is not
  No such test id  delete-statement-4
  [Teardown]  Logout

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
  Statement count is  6

Veikko can edit notice
  Check status  normal
  Edit notice  ullakko  urgent  Hello world!

Veikko cannot delete statement requests
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-1']
  Element should not be visible  xpath=//div[@id='application-statement-tab']//span[@data-test-id='delete-statement-3']

Veikko from Tampere can give statement as attachment
  Open statement  veikko.viranomainen@tampere.fi
  Wait Until  element should be enabled  statement-text
  Click label by test id  statement-provided-as-attachment-label
  Select From List By Value  statement-type-select  ehdollinen
  Wait until  Element Should Be Disabled  statement-submit
  Upload attachment with default type  ${TXT_TESTFILE_PATH}
  Wait Until  Element should contain  xpath=//table[@data-test-id="targetted-attachments-table"]//span  Tampereen luvat
  Element should contain  xpath=//table[@data-test-id="targetted-attachments-table"]//td  Lausunto
  Wait until  Element Should Be Enabled  statement-submit
  Scroll to  \#statement-submit
  Click Element  statement-submit
  Confirm yes no dialog
  Statement status is  Ehdollinen  veikko.viranomainen@tampere.fi
  [Teardown]  Logout

Pena can't change due date in a statement where Sonja or Pena are statement givers
  Pena logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  sonja.sibbo@sipoo.fi
  Element should not be visible  due-date-input
  Return from statement
  Open statement  pena@example.com
  Element should not be visible  due-date-input
  [Teardown]  Logout

Sonja can see statement indicator
  Sonja logs in
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[contains(@class, 'lupicon-star')]

There is no possibility to delete the generated statement pdf attachment
  Open application  ${appname}  ${appPropertyId}
  Open tab  attachments
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto']
  Element should not be visible  jquery=div#application-attachments-tab tr[data-test-type='ennakkoluvat_ja_lausunnot.lausunto'] span[data-test-icon="delete-button"]
  [Teardown]  Logout

Pena logs in but does not see notice
  Pena logs in
  Open application  ${appname}  ${appPropertyId}
  Wait until  Element should not be visible  //div[@id='side-panel']//button[@id='open-notice-side-panel']
  [Teardown]  Logout

Mikko logs in and submits application
  Mikko logs in
  Open application  ${appname}  ${appPropertyId}
  Submit application
  Logout

Sonja logs in and approves application
  Sonja logs in
  Open application  ${appname}  ${appPropertyId}
  Approve application no dialogs

Sonja cannot invite any more statement givers
  Open tab  statement
  No such test id  add-statement
  [Teardown]  Logout

Luukas logs in and cannot edit his statement anymore
  Luukas logs in
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Open statement  luukas.lukija@sipoo.fi
  Wait until element is visible  statement-cover-note
  Wait until element is not visible  statement-type-select
  Wait until element is not visible  statement-submit
  Wait until element is not visible  statement-text
  Return from statement

Luukas cannot delete any statements.
  Wait until element is visible  jquery:div.application-statements
  No such test id  delete-statement-column
  Element should not be visible  jquery:button[aria-label='Poista lausunto']
  [Teardown]  Logout
