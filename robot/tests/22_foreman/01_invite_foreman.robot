*** Settings ***

Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Test Cases ***

Pena creates new application
  Pena logs in
  Create project application

Pena invites Mikko
  Open tab  parties
  Open foreman accordions
  Invite Mikko

Pena invites Solita
  Open tab  parties
  Open foreman accordions
  Wait Until  Click Element  xpath=//div[@class='parties-list']//button[@data-test-id='company-invite']
  Wait Until  Element should be visible  xpath=//div[@data-test-id='modal-dialog-content']
  Element should not be visible  xpath=//div[@data-test-id='company-invite-confirm-help']
  Select From Autocomplete  div[@id="modal-dialog-content-component"]  Solita Oy
  Click enabled by test id  modal-dialog-submit-button
  Wait Until  Element should be visible  xpath=//div[@data-test-id='company-invite-confirm-help']
  Click enabled by test id  modal-dialog-submit-button
  Wait Until  Page should contain  1060155-5

Solita accepts invitation
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  kaino@solita.fi
  Click Element  xpath=(//a)[2]
  Wait until  Page should contain  Hakemus on liitetty onnistuneesti yrityksen tiliin.
  [Teardown]  Go to login page

Mikko accepts invitation
  Mikko logs in
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  ${appname}, Sipoo,
  Element Text Should Be  xpath=//div[@class='invitation']//p[@data-test-id='invitation-text-0']  Tervetuloa muokkaamaan hakemusta
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']
  [Teardown]  logout

# Pena sets Mikko as hakija
#   Pena logs in
#   Open application  ${appname}  753-416-25-22
#   Open tab  parties
#   Open foreman accordions
#   Select From List  xpath=//section[@data-doc-type="hakija-r"]//div[@data-select-one-of="henkilo"]//select[@name="henkilo.userId"]  Intonen Mikko
#   Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko

Pena sets Solita as hakija
  Pena logs in
  Open application  ${appname}  753-416-25-22
  Open tab  parties
  Open foreman accordions
  Wait until  Click Element  xpath=//section[@data-doc-type="hakija-r"]//input[@value="yritys"]
  Wait until  Select From List  xpath=//section[@data-doc-type="hakija-r"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="yritys.yritysnimi"]  Solita Oy

Pena invites foreman Teppo to application
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  ${foremanAppId} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Set Suite Variable  ${foremanAppId}  ${foremanAppId}

Pena sees sent invitation on the original application
  Go back to project application
  Open tab  parties
  Open foreman accordions
  Wait until  Element text should be  xpath=//ul[@data-test-id='invited-foremans']//span[@data-test-id='foreman-email']  (teppo@example.com)
  # Also in auth array
  Wait until  Element should be visible  xpath=//div[@class='parties-list']/ul//li/span[@class='person']/span[contains(., 'teppo@example.com')]

Pena sees sent invitations on the foreman application
  Open application by id  ${foremanAppId}
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-id']  ${foremanAppId}
  Open tab  parties
  Open foreman accordions
  Wait until  Xpath Should Match X Times  //ul/li[@class="party"]  3
  [Teardown]  logout

Foreman can see application
  Teppo logs in
  Go to page  applications
  # Should work always because latest application is always at the top
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][1]/td[@data-test-col-name='operation']  Työnjohtajan nimeäminen
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][2]/td[@data-test-col-name='operation']  Asuinkerrostalon tai rivitalon rakentaminen
  [Teardown]  logout

Foreman application can't be submitted before link permit application
  Pena logs in
  Open project application
  Wait until  Element should be visible  xpath=//a[@data-test-id='test-application-app-linking-to-us']
  Click by test id  test-application-app-linking-to-us
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  Open tab  requiredFieldSummary
  Element should be disabled  xpath=//button[@data-test-id='application-submit-btn']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//p[@data-test-id='foreman-not-submittable']

Application is submitted
  Open project application
  Wait Until  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen
  Submit application
  [Teardown]  logout

Authority can view draft foreman application, but can't use commands
  # LPK-289
  Sonja logs in
  Open project application
  Wait Until  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen
  Click by test id  test-application-app-linking-to-us
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  Element should be disabled  xpath=//section[@data-doc-type="hankkeen-kuvaus-minimum"]//textarea
  Open tab  parties
  Element should be disabled  xpath=//section[@data-doc-type="hakija-r"]//div[@data-select-one-of="henkilo"]//select[@name="henkilo.userId"]
  Element should be disabled  xpath=//section[@data-doc-type="hakija-r"]//div[@data-select-one-of="henkilo"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]
  Open tab  attachments
  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//select[@data-test-id="attachment-operations-select-lower"]/option  1
  Element text should be  xpath=//div[@id="application-attachments-tab"]//select[@data-test-id="attachment-operations-select-lower"]/option  Valitse…
  Open tab  requiredFieldSummary
  Element should not be visible  xpath=//div[@id="application-requiredFieldSummary-tab"]//button[@data-test-id="application-submit-btn"]
  # Application actions only exportPDF is visible
  Element should be visible  xpath=//div[@class="application_actions"]//button[@data-test-id="application-pdf-btn"]
  Element should not be visible  xpath=//div[@class="application_actions"]//button[@data-test-id="add-operation"]
  Element should not be visible  xpath=//div[@class="application_actions"]//button[@data-test-id="application-add-link-permit-btn"]
  Element should not be visible  xpath=//div[@class="application_actions"]//button[@data-test-id="application-cancel-btn"]
  Element should not be visible  xpath=//div[@class="application_actions"]//button[@data-test-id="application-cancel-authority-btn"]


Original application is approved and given a verdict
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait Until  Click enabled by test id  approve-application
  Open tab  verdict
  Submit empty verdict

Add työnjohtaja task to original application
  Add työnjohtaja task to current application  Ylitarkastaja
  Add työnjohtaja task to current application  Alitarkastaja
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  2
  [Teardown]  logout

Pena can link existing foreman application to foreman task
  Pena logs in
  Open project application
  Open tab  tasks
  Select From List By Value  xpath=//select[@data-test-id="foreman-selection"]  ${foremanAppId}

Pena can move to linked foreman application and back
  Click by test id  foreman-application-link-${foremanAppId}
  Wait until  Element text should be  xpath=//span[@data-test-id='application-id']  ${foremanAppId}
  Click by test id  test-application-link-permit-lupapistetunnus

Pena can start invite flow from tasks tab
  Open tab  tasks
  Click enabled by test id  invite-other-foreman-button
  Wait until  Element should be visible  //div[@id='dialog-invite-foreman']
  Click by test id  cancel-foreman-dialog
  Click enabled by test id  invite-substitute-foreman-button
  Wait until  Element should be visible  //div[@id='dialog-invite-foreman']
  Click by test id  cancel-foreman-dialog

Pena can invite additional foremans to application with verdict
  Wait and click   xpath=//div[@data-test-id='tasks-foreman']//tr[@data-test-name='Alitarkastaja']/td[@data-test-col-name='foreman-name-or-invite']/a
  Wait until  Element should be visible  invite-foreman-email
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  [Teardown]  logout

*** Keywords ***

Invite Mikko
  Invite count is  0
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
  Element should be disabled  xpath=//*[@data-test-id='application-invite-submit']
  Input Text  invite-email  mikko@example
  Element should be disabled  xpath=//*[@data-test-id='application-invite-submit']
  Input Text  invite-email  mikko@example.com
  Element should be enabled  xpath=//*[@data-test-id='application-invite-submit']
  Click by test id  application-invite-submit
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1

