*** Settings ***

Documentation  Sonja browses the attachment of an application
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py

*** Test Cases ***

Sonja goes to empty attachments tab
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Sonja logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments

Sonja sees the attachments grouped into accordions
  [Tags]  attachments
  Wait until  Total attachments row count is  4
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  OSAPUOLET
  Wait until  Attachment subgroup should be visible  0  1  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment group should be visible  1  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  1  0  PÄÄPIIRUSTUKSET
  Wait until  Attachment subgroup should be visible  1  1  MUUT SUUNNITELMAT

Sonja removes Valtakirja attachment
  [Tags]  attachments
  Wait until  Delete attachment  hakija.valtakirja
  Wait until  Total attachments row count is  3

The Osapuolet accordion disappears
  [Tags]  attachments
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment group should be visible  1  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  1  0  PÄÄPIIRUSTUKSET
  Wait until  Attachment subgroup should be visible  1  1  MUUT SUUNNITELMAT
  Wait until  Attachment subgroup should not be visible  0  1

Sonja selects the 'Yleiset hankkeen liitteet' filter
  [Tags]  attachments
  Click by test id  general-filter-label
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment subgroup should not be visible  0  1
  Wait until  Attachment group should not be visible  1

Sonja selects the 'Muut suunnitelmat' filter
  [Tags]  attachments
  Click by test id  other-filter-label
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment group should be visible  1  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  1  0  MUUT SUUNNITELMAT

Sonja deselects the 'Yleiset hankkeen liitteet' filter
  [Tags]  attachments
  Click by test id  general-filter-label
  Wait until  Attachment group should be visible  0  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  0  0  MUUT SUUNNITELMAT

Sonja deselects all filters
  [Tags]  attachments
  Click by test id  toggle-all-filters-checkbox
  Click by test id  toggle-all-filters-checkbox
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment group should be visible  1  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  1  0  PÄÄPIIRUSTUKSET
  Wait until  Attachment subgroup should be visible  1  1  MUUT SUUNNITELMAT

Sonja adds a technical report, and it appears in the attachments listing
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Selvitys rakennuksen kunnosta  ${EMPTY}  Tekniset selvitykset
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  1  TEKNISET SELVITYKSET

Sonja selects the 'Tekniset selvitykset' filter, then deselects all
  [Tags]  attachments
  Click by test id  technical-reports-filter-label
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  TEKNISET SELVITYKSET
  Wait until  Attachment group should not be visible  1
  Click by test id  toggle-all-filters-checkbox
  Click by test id  toggle-all-filters-checkbox


Sonja selects the 'Pääpiirustukset' filter and Asemapiirros is visible
  [Tags]  attachments
  Click by test id  paapiirustus-filter-label
  Wait until  Attachment group should be visible  0  HAKEMUKSEN LIITTEET
  Wait until  Attachment subgroup should be visible  0  0  YLEISET HANKKEEN LIITTEET
  Wait until  Attachment group should be visible  1  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Wait until  Attachment subgroup should be visible  1  0  PÄÄPIIRUSTUKSET
  Wait until  Total attachments row count is  2
