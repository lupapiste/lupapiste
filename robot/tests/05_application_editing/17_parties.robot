*** Settings ***

Documentation   Editing application parties
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Variables ***

${remove-button}  button[data-test-class='delete-schemas.hakija-r']

*** Test Cases ***

Pena logs in and creates application
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Party-on-${secs}
  Set Suite Variable  ${propertyId}  753-423-2-22
  Create application with state  ${appname}  ${propertyId}  aita  open

Pena edits head designer information
  Open tab  parties
  Check accordion text  paasuunnittelija  PÄÄSUUNNITTELIJA  ${empty}
  Edit party name  paasuunnittelija  Sanna  Suunnittelija
  Check accordion text  paasuunnittelija  PÄÄSUUNNITTELIJA  - Sanna Suunnittelija
  Select level  paasuunnittelija  AA
  Check accordion text  paasuunnittelija  PÄÄSUUNNITTELIJA  - Sanna Suunnittelija (Poikkeuksellisen vaativa)
  Edit party name  paasuunnittelija  ${EMPTY}  ${EMPTY}
  Check accordion text  paasuunnittelija  PÄÄSUUNNITTELIJA  (Poikkeuksellisen vaativa)

Pena edits designer information
  Check accordion text  suunnittelija  SUUNNITTELIJA  ${empty}
  Edit party name  suunnittelija  Piia  Piirtäjä
  Check accordion text  suunnittelija  SUUNNITTELIJA  - Piia Piirtäjä
  Select level  suunnittelija  B
  Check accordion text  suunnittelija  SUUNNITTELIJA  - Piia Piirtäjä (Tavanomainen)
  Select role  suunnittelija  IV-suunnittelija
  Check accordion text  suunnittelija  SUUNNITTELIJA  - Piia Piirtäjä - IV-suunnittelija (Tavanomainen)
  Edit party name  suunnittelija  ${EMPTY}  ${EMPTY}
  Check accordion text  suunnittelija  SUUNNITTELIJA  - IV-suunnittelija (Tavanomainen)
  Edit party name  suunnittelija  Piia  Piirtäjä
  Select level  suunnittelija  ${EMPTY}
  Check accordion text  suunnittelija  SUUNNITTELIJA  - Piia Piirtäjä - IV-suunnittelija
  Select level  suunnittelija  B

Roles and levels are localized
  Language To  SV
  Check accordion text  paasuunnittelija  HUVUDPROJEKTERARE  (Exceptionellt krävande)
  Check accordion text  suunnittelija  PLANERARE  - Piia Piirtäjä - VVS-projekterare, ventilation (Sedvanlig)
  Language To  FI

Pena executes test scenario
  Test scenario
  [Teardown]  Logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  ${propertyId}

Sonja executes test scenario
  Test scenario
  [Teardown]  Logout

*** Keywords ***

Test scenario
  Open tab  parties
  No remove button
  Add hakija-r
  Two remove buttons
  Scroll to  ${remove-button}:first
  Click element  xpath=(//button[@data-test-class='delete-schemas.hakija-r'])[1]
  Wait until  Element should be visible  modal-dialog-content
  Deny yes no dialog
  Scroll to  ${remove-button}:last
  Click element  xpath=(//button[@data-test-class='delete-schemas.hakija-r'])[2]
  Confirm yes no dialog
  No remove button

No remove button
  Wait Until  Element should not be visible  jquery=${remove-button}

Add hakija-r
  Scroll and click test id  hakija-r_append_btn

Two remove buttons
  jQuery should match X times  ${remove-button}  2

Select level
  [Arguments]  ${party}  ${level}
  Select from list by value  jquery=section[data-doc-type='${party}'] select[data-docgen-path='suunnittelutehtavanVaativuusluokka']  ${level}

Select role
  [Arguments]  ${party}  ${role}
  Select from list by value  jquery=section[data-doc-type='${party}'] select[data-docgen-path='kuntaRoolikoodi']  ${role}
