*** Settings ***

Documentation   Digging permits can obly be created from sijoituslupa or sijoitussopimus applications in post verdict states
Resource        ../../common_resource.robot
Resource        ../25_company/company_resource.robot
Resource        ../39_pate/pate_resource.robot
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Test Cases ***

Mikko creates and submits R application
  Mikko logs in
  Create application the fast way  not-sijoitus-app  753-416-25-30  kerrostalo-rivitalo
  Submit application

Mikko does not see the 'Create digging permit' button
  Element should not be visible by test id  create-digging-permit-button
  Logout

Sonja cannot create digging app even when verdict is given
  Sonja logs in
  Open application  not-sijoitus-app  753-416-25-30
  Element should not be visible by test id  create-digging-permit-button
  Open tab  verdict
  Fetch verdict
  Element should not be visible by test id  create-digging-permit-button
  Logout

Mikko creates and submits a sijoitus application
  Mikko logs in
  Create application the fast way  sijoitus-app  753-416-25-30  ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
  Submit application
  Logout

Sonja can create digging app when verdict is given
  Sonja logs in
  Open application  sijoitus-app  753-416-25-30
  Element should not be visible by test id  create-digging-permit-button
  Open tab  verdict
  Fetch YA verdict
  Wait until  Element should be visible by test id  create-digging-permit-button
  Logout

So can Mikko
  Mikko logs in
  Open application  sijoitus-app  753-416-25-30
  Wait until  Element should be visible by test id  create-digging-permit-button

*** Keywords ***
