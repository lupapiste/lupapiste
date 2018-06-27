*** Settings ***

Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize foreman

*** Keywords ***
Sonja invites foreman and goes back to application
  Sonja invites foreman to application
  Go back to project application

*** Test Cases ***
Sonja creates applications
  Sonja logs in  False
  Create project application  submitted

Create 5x foreman invites
  Repeat Keyword  5  Sonja invites foreman and goes back to application

Foreman sets his information to several applications
  Foreman logs in
  Foreman sets role and difficulty to foreman application  0  KVV-työnjohtaja  B

Foreman sees his information reflected by the accordion
  Check accordion text  tyonjohtaja-v2  TYÖNJOHTAJAN NIMEÄMINEN  - KVV-työnjohtaja Teppo Nieminen

Accordion information is localized
  Language to  SV
  Check accordion text  tyonjohtaja-v2  UTNÄMNING AV ARBETSLEDARE  - Fastighets vatten- och avloppsarbetsledare Teppo Nieminen
  Language to  FI

Foreman continues filling his info
  Foreman sets role and difficulty to foreman application  1  KVV-työnjohtaja  B
  Foreman sets role and difficulty to foreman application  2  KVV-työnjohtaja  A
  Foreman sets role and difficulty to foreman application  3  IV-työnjohtaja   B

  Open foreman application  4
  Deny yes no dialog
  Open tab  parties
  Open accordions  parties
  Foreman accepts invitation and fills info

Foreman history is not visible to applicant
  Page Should Not Contain Element  xpath=//foreman-history
  Page Should Not Contain Element  jquery=foreman-history div.foreman-history-container
  Logout

Switch to authority
  Sonja logs in

Authority sees base application on foreman search
  ${appId} =  Get From List  ${applicationIds}  0
  Wait until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-id='${appId}']

Authority does not see foreman applications on default search
  ${foremanAppId0} =  Get From List  ${foremanApps}  0
  ${foremanAppId1} =  Get From List  ${foremanApps}  1
  ${foremanAppId4} =  Get From List  ${foremanApps}  4
  Page Should Not Contain  xpath=//table[@id='applications-list']//tr[@data-id='${foremanAppId0}']
  Page Should Not Contain  xpath=//table[@id='applications-list']//tr[@data-id='${foremanAppId1}']
  Page Should Not Contain  xpath=//table[@id='applications-list']//tr[@data-id='${foremanAppId4}']

Authority not see base application on foreman search
  Click element  xpath=//label[@for='searchTypeForeman']
  ${appId} =  Get From List  ${applicationIds}  0
  Wait until  Page Should Not Contain  xpath=//table[@id='applications-list']//tr[@data-id='${appId}']

Authority sees foreman applications on foreman search
  ${foremanAppId4} =  Get From List  ${foremanApps}  4
  Wait Until  Element Should Be Visible  xpath=//table[@id='applications-list']//tr[@data-id='${foremanAppId4}']

History is empty due to foreman apps' state
  Open foreman application  4
  Open tab  parties
  Open accordions  parties
  Wait until  Foreman history should have text X times  Ei hankkeita  1

Foreman submits applications
  Foreman logs in
  Foreman submit application  0
  Foreman submit application  1
  Foreman submit application  2
  Foreman submit application  3
  Logout

Sonja gives verdicts to foreman applications
  Sonja logs in
  Verdict for foreman application  0
  Verdict for foreman application  1
  Verdict for foreman application  2
  Verdict for foreman application  3

Authority sees reduced foreman history
  Open foreman application  4
  Open tab  parties
  Open accordions  parties
  Wait until  Element should be visible  xpath=//foreman-history
  Foreman history should have text X times  Sipoo  3
  Foreman history should have text X times  Tavanomainen  2
  Element should be visible by test id  tyonjohtaja-historia-otsikko

Sonja toggles show all of the history
  Toggle not selected  foreman-history-show-all
  Toggle toggle  foreman-history-show-all
  Element should be visible by test id  tyonjohtaja-historia-otsikko-kaikki
  Wait until  Foreman history should have text X times  Sipoo  4

Sonja opens foreman application 1
  Open foreman application  1
  Open tab  parties
  Open accordions  parties

There is no reduced history for this application
  Element should be visible by test id  tyonjohtaja-historia-otsikko-kaikki
  Wait until  Foreman history should have text X times  Sipoo  3
  No such test id  foreman-history-show-all-label

Frontend errors check
  There are no frontend errors
