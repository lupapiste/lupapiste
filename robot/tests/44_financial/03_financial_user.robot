*** Settings ***

Documentation   Financial users action
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja creates bunch of application with ARA funding
  Sonja logs in  False
  Create application the fast way  sipoo-funding-app-1  753-416-25-31  kerrostalo-rivitalo
  Create application the fast way  sipoo-funding-app-2  753-416-25-32  kerrostalo-rivitalo
  Create application the fast way  sipoo-funding-app-3  753-416-25-33  kerrostalo-rivitalo
  Create application the fast way  sipoo-funding-app-4  753-416-25-34  kerrostalo-rivitalo
  Create application the fast way  sipoo-funding-app-5  753-416-25-35  kerrostalo-rivitalo
  Go to page  applications
  Add ARA funding  sipoo-funding-app-1  753-416-25-31
  Add ARA funding  sipoo-funding-app-2  753-416-25-32
  Add ARA funding  sipoo-funding-app-3  753-416-25-33
  Add ARA funding  sipoo-funding-app-4  753-416-25-34
  Open application  sipoo-funding-app-2  753-416-25-32
  Submit application
  Open application  sipoo-funding-app-3  753-416-25-33
  Submit application
  Wait until  Element should be visible by test id  h1-afterSubmitted
  Approve application no dialogs
  Open application  sipoo-funding-app-4  753-416-25-34
  Submit application
  Wait until  Element should be visible by test id  h1-afterSubmitted
  Approve application no dialogs
  Give verdict
  Logout

Olli creates applications with ARA funding
  Olli logs in  False
  Create application the fast way  oulu-funding-app-1  564-1-9901-0  kerrostalo-rivitalo
  Create application the fast way  oulu-funding-app-2  564-1-9901-1  kerrostalo-rivitalo
  Add ARA funding  oulu-funding-app-1  564-1-9901-0
  Logout

Financial authority logs in and see all applications with funding from all organizations
  Financial logs in
  Show all applications
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="sipoo-funding-app-1"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="sipoo-funding-app-2"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="sipoo-funding-app-3"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="sipoo-funding-app-4"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="oulu-funding-app-1"]

Financial authority cant see applications without funding
  Wait until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="sipoo-funding-app-5"]
  Wait until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="oulu-funding-app-2"]

Financial authority can see applications within search tabs
  Open search tab  all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[contains(@class, 'application-row')]  5
  Open search tab  application
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[contains(@class, 'application-row')]  2
  Open search tab  construction
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[contains(@class, 'application-row')]  1

Financial authority cant see advanced search filters
  Wait until  Element should not be visible  toggle-advanced-filters

Financial authority can open application
  Open search tab  all
  Open application  sipoo-funding-app-4  753-416-25-34

Application summary tab is visible but everything is disabled
  Open tab  applicationSummary
  Visible accordion count is  8
  Element is visible and disabled by id  select-funding-checkbox
  Element is visible and disabled by test id  hallintaperuste
  Element is visible and disabled by test id  rakennuksenOmistajat.0.henkilo.userId
  Element is visible and disabled by test id  rakennuksenOmistajat.0.omistajalaji
  Element is visible and disabled by id  kaavatilanne
  Element is visible and disabled by id  rakenne.rakentamistapa
  Element is visible and disabled by id  rakenne.kantavaRakennusaine
  Element is visible and disabled by id  lammitys.lammitystapa
  Element is visible and disabled by id  lammitys.lammonlahde
  Element is visible and disabled by id  luokitus.energiatehokkuusluvunYksikko

Parties tab is visible but everything is disabled
  Open tab  parties
  Wait until  Xpath Should Match X Times  //table//tr[contains(@class, 'party')]  2
  Is authorized party  ARA-käsittelijä
  Visible accordion count is  5
  Element is visible and disabled by test id  henkilo.userId
  Element is visible and disabled by test id  henkilo.osoite.maa
  Element is visible and disabled by test id  patevyys.koulutusvalinta
  Element is visible and disabled by id  suunnittelutehtavanVaativuusluokka
  Element is visible and disabled by id  kuntaRoolikoodi


Tasks tab is visible but everything is disabled
  Open tab  tasks
  Element is visible and disabled by test id  invite-other-foreman-button
  Element is visible and disabled by test id  invite-substitute-foreman-button

Verdict tab is visible but everything is disabled
  Open tab  verdict
  Element should not be visible  add-appeal-0-0
  Element should not be visible  give-verdict
  Element should not be visible  fetch-verdict

Attachments tab is visible and attachments can be downloaded
  Open tab  attachments
  Element id enabled by test id  download-accordion-attachments-button-undefined
  Element id enabled by test id  download-all-attachments-button
  Element id enabled by test id  download-all

Financial authority can add comment
  Add comment  Comment from financial authority
  Check comment  Comment from financial authority

Financial authority can go to my page and change password
  Go to  ${SERVER}/app/fi/financial-authority
  Go to page  mypage
  Wait Until  Element Should be visible  //*[@data-test-id='change-my-password']
  Input Text  oldPassword  admin
  Input Text  newPassword  admin1234
  Input Text  newPassword2  admin1234
  Click enabled by test id  change-my-password
  Wait until  Page should contain  Tallennettu
  Logout
  User logs in  financial  admin1234  ARA-käsittelijä

Financial authority can go to my page and change email
  Go to  ${SERVER}/app/fi/financial-authority
  Go to page  mypage
  Input Text  newEmail  financial@ara.com
  Focus  xpath=//*[@data-test-id='change-email']
  Click enabled by test id  change-email
  Wait Until  Page Should Contain  Uuteen sähköpostiosoitteeseen on lähetetty viesti osoitteen vaihdosta
  Open last email
  Click link  xpath=//a
  Go to login page
  User logs in  financial@ara.com  admin1234  ARA-käsittelijä


*** Keywords ***

Add ARA funding
  [Arguments]  ${address}  ${propertyId}
  Open application  ${address}  ${propertyId}
  Open tab  info
  Open accordions  info
  Wait until  Element should be visible  select-funding-checkbox
  Select checkbox  select-funding-checkbox
  Confirm  dynamic-yes-no-confirm-dialog
  Open accordions  info
  Go to page  applications

Give verdict
  Go to give new verdict
  Input verdict  321  1   01.05.2020  01.06.2020  Sopija-Sonja  False
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  verdictGiven

Visible accordion count is
  [Arguments]  ${amount}
  ${accordions}  Visible accordion count
  Should Be True  ${amount} == ${accordions}

Element is visible and disabled by test id
  [Arguments]  ${id}
  Element should be visible by test id  ${id}
  Wait Until  Element Should Be Disabled  xpath=//*[@data-test-id="${id}"]

Element is visible and disabled by id
  [Arguments]  ${id}
  Element should be visible  ${id}
  Element should be disabled  ${id}

Element id enabled by test id
  [Arguments]  ${id}
  Element should be visible by test id  ${id}
  Wait Until  Element Should Be Enabled  xpath=//*[@data-test-id="${id}"]
