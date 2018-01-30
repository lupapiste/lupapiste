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
  Create application with state  ${appname}  ${propertyId}  mainoslaite  open

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

Pena selects himself as head designer
  Select person by index  paasuunnittelija  1
  Selected person is  paasuunnittelija  Panaani Pena
  Person fields are  paasuunnittelija  Pena  Panaani

Pena clears head designer selector, but names remain even after reload
  Select person by index  paasuunnittelija  0
  Selected person is  paasuunnittelija  - Valitse -
  Reload page
  Person fields are  paasuunnittelija  Pena  Panaani

Pena selects himself as applicant
  Select person by index  hakija-r  1
  Selected person is  hakija-r  Panaani Pena
  Person fields are  hakija-r  Pena  Panaani  henkilo.

Pena clears applicant selector, but names remain even after reload
  Select person by index  hakija-r  0
  Selected person is  hakija-r  - Valitse -
  Reload page
  Person fields are  hakija-r  Pena  Panaani  henkilo.

Pena executes test scenario
  Test scenario
  Logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  ${propertyId}

Sonja executes test scenario
  Test scenario
  Logout

Frontend errors check
  There are no frontend errors

*** Keywords ***

Test scenario
  Run Keyword And Ignore Error  Kill dev-box
  Open tab  parties
  No remove button
  Add hakija-r
  Two remove buttons
  Scroll to  ${remove-button}:first
  Sleep  0.5s
  Wait until  Element should be visible  xpath=(//button[@data-test-class='delete-schemas.hakija-r'])[1]
  Scroll to  button[data-test-class='delete-schemas.hakija-r']  0
  Click element  xpath=(//button[@data-test-class='delete-schemas.hakija-r'])[1]
  Wait until  Element should be visible  modal-dialog-content
  Deny yes no dialog
  Scroll to  ${remove-button}:last
  Sleep  0.5s
  Wait until  Element should be visible  xpath=(//button[@data-test-class='delete-schemas.hakija-r'])[2]
  Scroll to  button[data-test-class='delete-schemas.hakija-r']  1
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

Doc input is
  [Arguments]  ${doc}  ${path}  ${value}
  Wait until  Textfield value should be  jquery=[data-doc-type=${doc}] input[data-docgen-path='${path}']  ${value}

Person fields are
  [Arguments]  ${doc}  ${firstname}  ${lastname}  ${extra}=${EMPTY}
  Doc input is  ${doc}  ${extra}henkilotiedot.etunimi  ${firstname}
  Doc input is  ${doc}  ${extra}henkilotiedot.sukunimi  ${lastname}

# Index starts at zero
Select person by index
  [Arguments]  ${doc}  ${index}
  Select from list by index  jquery=[data-doc-type=${doc}] div.person-select-box select  ${index}

Selected person is
  [Arguments]  ${doc}  ${person}
  Wait until  List selection should be  jquery=[data-doc-type=${doc}] div.person-select-box select  ${person}
