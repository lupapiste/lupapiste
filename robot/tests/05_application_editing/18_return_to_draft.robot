*** Settings ***

Documentation   Sonja can return a submitted application to draft state
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Init
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41

Pena creates an application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open application  ${appname}  ${propertyId}
  Open to authorities  Mellon!
  Log out

Sonja visits the applications and can not return it to draft yet
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Element should not be visible  application-return-to-draft-btn
  Log out

Pena submits the application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Submit application
  Log out

Sonja sees that important information is missing and returns the application to draft state
  Sonja logs in
  Return to draft  ${appname}  ${propertyId}
  Wait until  Application state should be  draft
  Open tab  requiredFieldSummary
  Wait until  Element should not be visible  application-return-to-draft-btn
  Log out

Pena sees Sonja's comments in the application comments
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Check comment  palautuskommentti

Pena receives an email with Sonja's comments
  Open last email
  Wait until  Page should contain  Pena
  Wait until  Page should contain  palautuskommentti
  Go to login page

*** Keywords ***

Return to draft
  [Arguments]  ${appname}  ${propertyId}
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Click enabled by test id  application-return-to-draft-btn
  Input text by test id  modal-dialog-textarea  palautuskommentti
  Click enabled by test id  confirm-yes
