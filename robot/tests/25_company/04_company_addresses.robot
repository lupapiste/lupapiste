*** Settings ***

Documentation   Companys addressess is used by default
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        company_resource.robot
Default Tags    company


*** Test Cases ***

Company admin opens company details
  Kaino logs in
  Open company details

There is no contact address for the comapny
  Test id input is  edit-company-contactAddress  ${EMPTY}
  Test id input is  edit-company-contactZip  ${EMPTY}
  Test id input is  edit-company-contactPo  ${EMPTY}
  Test id input is  edit-company-contactCountry  ${EMPTY}

But there is billing address
  Test id input is  edit-company-address1  \u00c5kerlundinkatu 11
  Test id input is  edit-company-zip  33100
  Test id input is  edit-company-po  Tampere

Create application
  Go to page  applications
  Create application the fast way  hakemus  753-416-45-3  kerrostalo-rivitalo

Building owners address is billing address
  Open tab  info
  Open accordions  info
  Execute Javascript  $("input[value='yritys']").click();
  Wait until  Element should be visible  xpath=//section[@data-doc-type="uusiRakennus"]//select[@name="company-select"]
  Select From List by label  //section[@data-doc-type="uusiRakennus"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Building owners street address is  \u00c5kerlundinkatu 11
  Building owners zip is  33100
  Building owners po is  Tampere

Applicant address is billing address
  Open tab  parties
  Open accordions  parties
  Execute Javascript  $("input[value='yritys']").click();
  Wait until  Element should be visible  xpath=//section[@data-doc-type="hakija-r"]//select[@name="company-select"]
  Select From List by label  //section[@data-doc-type="hakija-r"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Company street address is  hakija-r  \u00c5kerlundinkatu 11
  Company zip is  hakija-r  33100
  Company po is  hakija-r  Tampere

Payers address is billing address
  Open tab  parties
  Open accordions  parties
  Select From List by label  //section[@data-doc-type="maksaja"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Company street address is  maksaja  \u00c5kerlundinkatu 11
  Company po is  maksaja  Tampere
  Company zip is  maksaja  33100

Add contact address
  Open company details
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']
  Click enabled by test id  company-details-edit
  Input text by test id  edit-company-contactAddress  Contact Street 2
  Input text by test id  edit-company-contactZip  00100
  Input text by test id  edit-company-contactPo  City
  Input text by test id  edit-company-contactCountry  Finland
  Click enabled by test id  company-details-save
  Wait Until  Element should be disabled  //div[@id='company-content']//button[@data-test-id='company-details-save']

Building owners address is contact address
  Open application  hakemus  753-416-45-3
  Open tab  info
  Open accordions  info
  Execute Javascript  $("input[value='yritys']").click();
  Select From List by label  //section[@data-doc-type="uusiRakennus"]//select[@name="company-select"]  Valitse...
  Select From List by label  //section[@data-doc-type="uusiRakennus"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Building owners street address is  Contact Street 2
  Building owners zip is  00100
  Building owners po is  City

Applicant address is contact address
  Open tab  parties
  Open accordions  parties
  Execute Javascript  $("input[value='yritys']").click();
  Select From List by label  //section[@data-doc-type="hakija-r"]//select[@name="company-select"]  Valitse...
  Select From List by label  //section[@data-doc-type="hakija-r"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Company street address is  hakija-r  Contact Street 2
  Company zip is  hakija-r  00100
  Company po is  hakija-r  City

Payers address is still billing address
  Open tab  parties
  Open accordions  parties
  Select From List by label  //section[@data-doc-type="maksaja"]//select[@name="company-select"]  Valitse...
  Sleep  0.5s
  Select From List by label  //section[@data-doc-type="maksaja"]//select[@name="company-select"]  Solita Oy (1060155-5)
  Sleep  0.5s
  Company street address is  maksaja  \u00c5kerlundinkatu 11
  Company po is  maksaja  Tampere
  Company zip is  maksaja  33100


*** Keywords ***

Building owners street address is
  [Arguments]  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="uusiRakennus"]//input[@data-docgen-path="rakennuksenOmistajat.0.yritys.osoite.katu"]  ${value}

Building owners zip is
  [Arguments]  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="uusiRakennus"]//input[@data-docgen-path="rakennuksenOmistajat.0.yritys.osoite.postinumero"]  ${value}

Building owners po is
  [Arguments]  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="uusiRakennus"]//input[@data-docgen-path="rakennuksenOmistajat.0.yritys.osoite.postitoimipaikannimi"]  ${value}


Company street address is
  [Arguments]  ${docType}  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="${docType}"]//input[@data-docgen-path="yritys.osoite.katu"]  ${value}

Company zip is
  [Arguments]  ${docType}  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="${docType}"]//input[@data-docgen-path="yritys.osoite.postinumero"]  ${value}

Company po is
  [Arguments]  ${docType}  ${value}
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="${docType}"]//input[@data-docgen-path="yritys.osoite.postitoimipaikannimi"]  ${value}
