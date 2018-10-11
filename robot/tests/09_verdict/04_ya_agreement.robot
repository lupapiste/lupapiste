*** Settings ***

Documentation   YA sijoitussopimus
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py

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
  Give legacy contract  321  Sopija-Sonja  ${CURRENT_DATE}
  Wait until  Application state should be  agreementPrepared
  [Teardown]  Logout

Mikko is back
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  agreementPrepared
  Wait until  Permit subtype is  Sijoitussopimus
  Element should be disabled  permitSubtypeSelect
  Open tab  verdict

Mikko signs the contract with bad password
  Sign contract  bad  False
  Click by test id  cancel-signing

Mikko signs the contract successfully
  Sign contract  mikko123
  Check signature  Mikko Intonen
  Wait until  Application state should be  agreementSigned

Frontend errors check
  There are no frontend errors
