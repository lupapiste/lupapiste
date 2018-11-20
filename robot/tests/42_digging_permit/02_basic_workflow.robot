*** Settings ***

Documentation   Mikko creates a digging permit from a sijoituslupa application
Resource        ../../common_resource.robot
Resource        ../25_company/company_resource.robot
Resource        ../39_pate/pate_resource.robot
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Test Cases ***

Mikko creates a sijoitus application
  Mikko logs in
  Create application the fast way  sijoitus-app  753-416-25-30  ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen

Mikko invites Solita Oy to the application, Solita Oy accepts the invitation
  Invite company to application  Solita Oy

Mikko selects Solita Oy as applicant for the application
  Open tab  parties
  Open accordions  parties
  Scroll and click input  section[data-doc-type=hakija-ya] input[value=yritys]
  Select From List by label  //section[@data-doc-type="hakija-ya"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Wait Until  Javascript?  $("section[data-doc-type=hakija-ya] input[data-docgen-path='yritys.yritysnimi']").val() == "Solita Oy"

Mikko selects himself as payer for the application
  Execute Javascript  $("section[data-doc-type='yleiset-alueet-maksaja'] input[value='henkilo']").click();
  Select From List by label  //section[@data-doc-type="yleiset-alueet-maksaja"]//select[@data-test-id="henkilo.userId"]  Intonen Mikko
  Wait Until  Javascript?  $("section[data-doc-type=yleiset-alueet-maksaja] input[data-docgen-path='henkilo.henkilotiedot.etunimi']").val() == "Mikko"

Mikko submits the application
  Submit application
  Logout

Sonja gives verdict
  Sonja logs in
  Open application  sijoitus-app  753-416-25-30
  Open tab  verdict
  Fetch YA verdict
  Logout

Mikko starts creating a digging application
  Mikko logs in
  Open application  sijoitus-app  753-416-25-30
  Wait until  Element should be visible by test id  create-digging-permit-button
  Click by test id  create-digging-permit-button

Mikko selects the operation
  Sleep  1s
  Select digging operations path YA kayttolupa kaivu

Mikko creates the application
  Click by test id  create-digging-permit

Mikko is directed to the digging permit
  Wait until  Element Should Be Visible  application

Mikko inspects that the application has correct information
  Wait until  Application state should be  draft
  Application address should be  sijoitus-app

Mikko and Solita Oy are authorized
  Open tab  parties
  Is authorized party  mikko@example.com
  Is authorized party  Solita Oy

The contact information of Mikko and Solita Oy is present in payer and applicant documents
  Check accordion text  hakija-ya  HAKIJA  - Solita Oy
  Check accordion text  yleiset-alueet-maksaja  LUVAN MAKSAJA (HAKEMUSTEN JA ILMOITUSTEN MAKSULLISUUS MÄÄRÄYTYY KUNNAN OMAN TAKSAN MUKAAN.)  - Mikko Intonen
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-ya"]//input[@data-docgen-path="yritys.yritysnimi"]  Solita Oy
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="yleiset-alueet-maksaja"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="yleiset-alueet-maksaja"]//input[@data-docgen-path="henkilo.henkilotiedot.sukunimi"]  Intonen

Mikko creates another digging application from the same sijoitus application
  Open the request at index  sijoitus-app  2
  Wait until  Element should be visible by test id  create-digging-permit-button
  Click by test id  create-digging-permit-button
  Sleep  1s
  Wait until  Element should be visible  xpath=//section[@id="create-digging-permit"]//div[contains(@class,"tree-link")]
  Select digging operations path YA kayttolupa kaivu
  Click by test id  create-digging-permit
  Wait until  Element Should Be Visible  application

Sijoitus application now has two links
  Open the request at index  sijoitus-app  3
  Wait until  Xpath Should Match X Times  //span[contains(@class, 'link-permit')]  2

*** Keywords ***

Click digging tree item by text
  [Arguments]  ${itemName}
  Wait and click  //section[@id="create-digging-permit"]//div[contains(@class, 'tree-content')]//*[text()=${itemName}]

Select digging operations path YA kayttolupa kaivu
  Set animations off
  Click digging tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click digging tree item by text  "Työskentely yleisellä alueella (Katulupa)"
  Click digging tree item by text  "Kaivaminen yleisellä alueella"
  Click digging tree item by text  "Vesi- ja viemäritöiden tekeminen"
  Set animations on
