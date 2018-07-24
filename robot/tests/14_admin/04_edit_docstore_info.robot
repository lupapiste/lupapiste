*** Settings ***

Documentation  Admin edits organization
Suite Teardown  Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       ./keywords.robot

*** Test Cases ***

Solita admin goes to organizations page
  SolitaAdmin logs in
  Go to page  organizations
  Wait test id visible  organization-search-term
  Test id text is  organization-result-count  ${EMPTY}
  Admin shows all organizations

Admin searchs just 753-R
  Fill test id  organization-search-term  753-R
  Test id text is  organization-result-count  1 organisaatio.

Admin edits organization with id 753-R
  Scroll and Click test id  edit-organization-753-R
  Wait until  Element should be visible  xpath=//section[@id="organization"]//input[@id="docstore-enabled"]

Admin enables docstore, and new options appear
  Select Checkbox  xpath=//section[@id="organization"]//input[@id="docstore-enabled"]
  Wait until  Element should be visible  xpath=//section[@id="organization"]//div[@data-test-id="docstore-enabled-settings"]

Admin attempts to enter a negative price
  Input text  docstore-price  -10.5
  Wait until  Element should be visible  xpath=//section[@id="organization"]//input[@id="docstore-price" and contains(@class, 'warn')]

Admin enters a positive price
  Input text  docstore-price  5,99
  Wait until  Element should be visible  xpath=//section[@id="organization"]//input[@id="docstore-price" and not(contains(@class, 'warn'))]

Admin saves the changes
  Wait until  Element should be enabled  xpath=//section[@id="organization"]//button[@data-test-id="save-docstore-info"]
  Click by test id  save-docstore-info
  Wait Until  Positive indicator should be visible

Admin enters a description of the organization and saves the changes
  Input text by test id  docstore-desc-fi  Organisaatiokuvausteksi
  Input text by test id  docstore-desc-en  Organization description
  Click by test id  save-docstore-info
  Wait Until  Positive indicator should be visible

Admin logs out
  Logout
