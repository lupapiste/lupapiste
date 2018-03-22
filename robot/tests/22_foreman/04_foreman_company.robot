
*** Settings ***

Documentation   Foreman application with company (LPK-3420)
Resource        ../../common_resource.robot
Resource        ../common_keywords/approve_helpers.robot
Resource        keywords.robot
Resource        ../25_company/company_resource.robot
Suite Setup     Apply simple-company fixture now

*** Test Cases ***

Init test
  ${applicationIds} =  Create List
  Set Suite Variable  ${applicationIds}
  ${applications} =  Create List
  Set Suite Variable  ${applications}
  ${foremanApps} =  Create List
  Set Suite Variable  ${foremanApps}

Applicant creates new application
  Pena logs in
  Create project application  open

Applicant invites Solita
  Invite company to application  Solita Oy

Applicant sets Solita as hakija
  Open foreman accordions
  Scroll and click input  section[data-doc-type=hakija-r] input[value=yritys]
  Wait until  Select From List  xpath=//section[@data-doc-type="hakija-r"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="yritys.yritysnimi"]  Solita Oy
  Logout

Sonja invites foreman Teppo to application
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  parties
  Open foreman accordions

  Scroll and click test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Scroll and click test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  ${foremanAppId} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Set Suite Variable  ${foremanAppId}  ${foremanAppId}
  Logout

Sven does not see any invitations
  Sven logs in
  Applications page should be open
  No such test id  accept-invite-button
  Logout

Solita has two invites, project and foreman
  User logs in  kaino@solita.fi  kaino123  Kaino Solita
  Wait until  Xpath Should Match X Times  //div[@class='invitation']//button[@data-test-id='accept-invite-button']  2

Second invite has invite to foreman app
  Element Should Contain  xpath=//div[@class='invitation'][2]//h3  Yritysvaltuutus: ${appname}, Sipoo, Työnjohtajan nimeäminen
  Element Should Contain  xpath=//div[@class='invitation'][2]//span[1]  Kutsuja:
  Element Should Contain  xpath=//div[@class='invitation'][2]//span[2]  Sibbo Sonja

Solita opens the foreman application and dismisses invitation dialog
  Click element  xpath=//div[@class='invitation'][2]//a[@data-test-id='open-application-button']
  Wait test id visible  yes-no-dialog
  Deny yes no dialog

Solita accepts invitation via sidebar button
  Click visible test id  accept-invite-button
  No such test id  accept-invite-button
  Sleep  0.5s

Solita returns to applications and the foreman invitation is gone
  Click element  //div[contains(@class,"nav-top")]//div[contains(@class,"header-box")]//a[@title="Hankkeet"]
  Applications page should be open
  # Only one left
  Wait until  Xpath Should Match X Times  //div[@class='invitation']//button[@data-test-id='accept-invite-button']  1
  # It is kerrostalo-rivitalo invitation
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  Yritysvaltuutus: ${appname}, Sipoo, Asuinkerrostalon
  Logout

Sven can now open the foreman application
  Sven logs in
  Open application by id  ${foremanAppId}
  Logout

Frontend errors
  There are no frontend errors
