*** Settings ***

Documentation   YA sijoitussopimus
Resource        ../../common_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py
Library         DateTime

*** Test Cases ***

Mikko wants to put some cables, submits application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  HangingCables${secs}
  Create application  ${appname}  753  753-416-25-30  YA-sijoituslupa
  Wait until  Permit subtype is  Sijoituslupa
  # Applicant can't change permit subtype
  Element should be disabled  permitSubtypeSelect
  Submit application
  Logout

Sonja prepares agreement
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${TOMORROW} =     Add time to date  ${CURRENT_DATETIME}  1 days  %d.%m.%Y
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  submitted
  Wait until  Permit subtype is  Sijoituslupa
  Element should be enabled  permitSubtypeSelect

  Go to give new verdict
  Input verdict  321  1  ${TODAY_DD_MM_YYYY}  ${TOMORROW}  Sopija-Sonja
  Checkbox should be selected  verdict-agreement
  Upload verdict or task attachment  ${TXT_TESTFILE_PATH}  Päätösote  Sopimus  Yleisesti hankkeeseen
  Wait test id visible  targetted-attachments-table

Permit subtype has changed to sijoitussopimus
  # This happens in sijoituslupa when verdict-agreement checkbox is selected
  Scroll and click test id  return-from-verdict
  Wait until  Permit subtype is  Sijoitussopimus

Publish agreement
  Click enabled by test id  edit-verdict
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  agreementPrepared
  Logout

Mikko signs agreement
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  agreementPrepared
  Wait until  Permit subtype is  Sijoitussopimus
  Element should be disabled  permitSubtypeSelect
  Open tab  verdict
  Sign verdict  mikko123
  Wait until  Application state should be  agreementSigned

Frontend errors check
  There are no frontend errors

