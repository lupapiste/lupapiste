*** Settings ***

Documentation   YA sijoitussopimus
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
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

Sonja sets permit subtype
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  submitted
  Wait until  Permit subtype is  Sijoituslupa
  Element should be enabled  permitSubtypeSelect
  Select from list  permitSubtypeSelect  sijoitussopimus

Sonja prepares and publishes contract
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${TOMORROW} =     Add time to date  ${CURRENT_DATETIME}  1 days  %d.%m.%Y

  Give legacy contract  321  Sopija-Sonja  ${TODAY_DD_MM_YYYY}
  Wait until  Application state should be  agreementPrepared
  [Teardown]  Logout

Mikko signs agreement
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  agreementPrepared
  Wait until  Permit subtype is  Sijoitussopimus
  Element should be disabled  permitSubtypeSelect
  Open tab  verdict
  debug
  Sign verdict  mikko123
  Wait until  Application state should be  agreementSigned

Frontend errors check
  There are no frontend errors
