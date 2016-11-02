*** Settings ***

Documentation   User browses bulletins from Julkipano.fi bulletin list
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Library         Screenshot
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Bulletins should be paginable
  Create bulletins  11
  Go to bulletins page

  Bulletin list should have rows  10
  Bulletin button should have bulletins left to fetch  1

  Load more bulletins

  Bulletin list should have rows  11

Bulletins should be searchable
  As Sonja
  Create application and publish bulletin  Mixintie 15  753-416-25-22
  Go to bulletins page

  Search bulletins by text  Mixintie 15
  Wait Until  Bulletin list should have rows and text  1  Mixintie 15

Velho adds some bulletins
  As Olli
  Create application and publish bulletin  Vaalantie 540  564-404-26-102
  Go to bulletins page

Bulletins should be filterable by municipality
  Autocomplete option list should contain by test id  municipalities-filter-component  Koko Suomi  Oulu  Sipoo

  Select From Autocomplete By Test Id  municipalities-filter-component  Oulu
  Wait for jQuery
  Wait Until  Bulletin list should have rows and text  10  Vaalantie 540
  # Two bulletins left for Oulu filter, three bulletins left without filter (Sipoo has 1)
  Bulletin button should have bulletins left to fetch  2

  Select From Autocomplete By Test Id  municipalities-filter-component  Sipoo
  Wait Until  Bulletin list should have rows and text  1  Mixintie 15

Velho gives a verdict
  As Olli
  Create application and publish bulletin  Raitotie 2  564-403-4-17
  Go to give new verdict
  Title Should Be  Raitotie 2 - Lupapiste
  Input verdict  123567890  6  01.05.2018  01.06.2018  Kaarina Krysp III
  Add attachment  verdict  ${TXT_TESTFILE_PATH}  ${EMPTY}  ${EMPTY}
  Wait test id visible  targetted-attachments-table

Velho publishes the verdict and creates a new verdict bulletin
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  verdictGiven
  Bulletin shows as proclaimed and can be moved to verdict given
  Move bulletin to verdict given with appeal period starting today
  Go to bulletins page

Bulletins should be filterable by state
  Reload Page
  Autocomplete option list should contain by test id  states-filter-component  Kaikki ilmoitustilat  Kuulutettavana

  Select From Autocomplete By Test Id  states-filter-component  Kuulutettavana
  Wait Until  Bulletin list should have rows  10
  Bulletin button should have bulletins left to fetch  3

  Reload Page
  Autocomplete option list should contain by test id  states-filter-component  Kaikki ilmoitustilat  Päätös annettu

  Select From Autocomplete By Test Id  states-filter-component  Päätös annettu
  Wait Until  Bulletin list should have rows  1

Verdict attachment should be visible on the bulletin page
  Open bulletin by index  1
  Open bulletin tab  verdicts
  Bulletin verdict detail list should have rows  1

*** Keywords ***

Move bulletin to verdict given with appeal period starting today
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${WEEK_FROM_NOW} =     Add time to date  ${CURRENT_DATETIME}  7 days  %d.%m.%Y
  ${MONTH_FROM_NOW} =     Add time to date  ${CURRENT_DATETIME}  30 days  %d.%m.%Y
  Input text with jQuery  input[name="verdictGivenAt"]  ${TODAY_DD_MM_YYYY}
  Input text with jQuery  input[name="appealPeriodStartsAt"]  ${TODAY_DD_MM_YYYY}
  Input text with jQuery  input[name="appealPeriodEndsAt"]  ${MONTH_FROM_NOW}
  Input text with jQuery  textarea[name="verdictGivenText"]  foobar
  Wait until  Element should be enabled  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin
