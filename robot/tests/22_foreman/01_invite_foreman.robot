*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot
Resource        keywords.robot

*** Test Cases ***

Mikko creates new application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  foreman-app${secs}
  Create application the fast way  ${appname}  753-416-25-22  kerrostalo-rivitalo
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${newApplicationid}  ${newApplicationid}

Mikko invites foreman to application
  Open tab  parties
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  ${foremanAppId} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Set Suite Variable  ${foremanAppId}  ${foremanAppId}

Mikko sees sent invitation on the original application
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Element text should be  xpath=//span[@data-test-id='application-id']  ${newApplicationid}
  Open tab  parties
  Wait until  Element text should be  xpath=//ul[@data-test-id='invited-foremans']//span[@data-test-id='foreman-email']  (teppo@example.com)
  [Teardown]  logout

Foreman can see application
  Teppo logs in
  Go to page  applications
  # Should work always because latest application is always at the top
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][1]/td[@data-test-col-name='operation']  Työnjohtajan nimeäminen
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}'][2]/td[@data-test-col-name='operation']  Asuinkerrostalon tai rivitalon rakentaminen
  [Teardown]  logout

Foreman application can't be submitted before link permit application
  Mikko logs in
  Open application at index  ${appname}  753-416-25-22  2
  Wait until  Element should be visible  xpath=//a[@data-test-id='test-application-app-linking-to-us']
  Click by test id  test-application-app-linking-to-us
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  Open tab  requiredFieldSummary
  Element should be disabled  xpath=//button[@data-test-id='application-submit-btn']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//p[@data-test-id='foreman-not-submittable']

Application is submitted
  Open application at index  ${appname}  753-416-25-22  2
  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen
  Submit application
  [Teardown]  logout

Authority can view draft foreman application, but can't use commands
  # LPK-289
  Sonja logs in
  Open application at index  ${appname}  753-416-25-22  1
  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen
  Click by test id  test-application-app-linking-to-us
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  Element should be disabled  xpath=//section[@data-doc-type="hankkeen-kuvaus-minimum"]//textarea
  Open tab  parties
  Element should be disabled  xpath=//section[@data-doc-type="hakija-r"]//div[@data-select-one-of="henkilo"]//select[@name="henkilo.userId"]
  Element should be disabled  xpath=//section[@data-doc-type="hakija-r"]//div[@data-select-one-of="henkilo"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]
  Open tab  attachments
  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//select[@data-test-id="attachment-operations-select-lower"]/option  1
  Element text should be  xpath=//div[@id="application-attachments-tab"]//select[@data-test-id="attachment-operations-select-lower"]/option  Valitse...
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
  Wait until  Xpath Should Match X Times  //table[@data-test-id="tasks-foreman"]/tbody/tr  2
  [Teardown]  logout

Mikko can link existing foreman application to foreman task
  Mikko logs in
  Open application at index  ${appname}  753-416-25-22  1
  Open tab  tasks
  Select From List By Value  foreman-selection  ${foremanAppId}

Mikko can move to linked foreman application and back
  Wait until  Click element  xpath=//a[@data-test-id='foreman-application-link-${foremanAppId}']
  Wait until  Element should contain  xpath=//span[@data-test-id='application-id']  ${foremanAppId}
  Click by test id  test-application-link-permit-lupapistetunnus

Mikko can start invite flow from tasks tab
  Open tab  tasks
  Click enabled by test id  invite-other-foreman-button
  Wait until  Element should be visible  //div[@id='dialog-invite-foreman']
  Click by test id  cancel-foreman-dialog
  Click enabled by test id  invite-substitute-foreman-button
  Wait until  Element should be visible  //div[@id='dialog-invite-foreman']
  Click by test id  cancel-foreman-dialog

Mikko can invite additional foremans to application with verdict
  Wait and click   xpath=//table[@data-test-id='tasks-foreman']//tr[@data-test-name='Alitarkastaja']/td[@data-test-col-name='foreman-name-or-invite']/a
  Wait until  Element should be visible  invite-foreman-email
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  [Teardown]  logout
