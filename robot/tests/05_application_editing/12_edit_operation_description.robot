*** Settings ***

Documentation  Operation description and document identifiers
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot

*** Variables ***

${address}     application-papplication
${propertyId}  753-416-25-30

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  ${address}  ${propertyId}  kerrostalo-rivitalo

Mikko edits operation description and building tag
  Open application  ${address}  ${propertyId}
  Edit identifiers  Hei  Talo A

Mikko submits application
  Submit application
  [Teardown]  Logout

Sonja logs and opens application
  Sonja logs in
  Open application  ${address}  ${propertyId}

Sonja checks identifiers description
  Check identifiers  Hei  Talo A

Sonja edits identifiers
  Edit identifiers  Moi  Kartano A

Sonja fetches verdict
  Open tab  verdict
  Fetch verdict

Sonja can still edit identifiers
  Open tab  applicationSummary
  Edit identifiers  Yes  Sonja can do!
  [Teardown]  Logout

Mikko cannot edit identifiers anymore
  Mikko logs in
  Open application  ${address}  ${propertyId}
  Open tab  applicationSummary
  Check identifiers  Yes  Sonja can do!
  Building identifier disabled  uusiRakennus
  Operation description disabled  uusiRakennus
  [Teardown]  Logout

Frontend errors check
  There are no frontend errors

*** Keywords ***

Edit identifiers
  [Arguments]  ${tag}  ${description}
  Input building identifier  uusiRakennus  ${tag}
  Edit operation description  uusiRakennus  ${description}

Check identifiers
  [Arguments]  ${tag}  ${description}
  Building identifier is  uusiRakennus  ${tag}
  Operation description is  uusiRakennus  ${description}
