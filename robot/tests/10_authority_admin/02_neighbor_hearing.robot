*** Settings ***

Documentation   Email is send to municipality about hearing neighbors
Resource       ../../common_resource.robot
*** Test Cases ***

Sipoo admin sets email address for neighbor hearing notification
  ${save_indicator} =  Set Variable  xpath=//section[@id="applications"]//span[contains(@class, "save-indicator")]
  Set Suite Variable  ${email}  kirjaamo@sipoo.example.com
  Sipoo logs in
  Go to page  applications
  Element should not be visible  ${save_indicator}
  Input text by test id  neighborOrderEmails  ${email}
  Wait Until  Element should be visible  ${save_indicator}
  Logout

Mikko asks municipality to hear neighbors
  Mikko logs in
  Create application the fast way  Naapurikatu  753-1-1-2  teollisuusrakennus
  Open tab  requiredFieldSummary
  Select Checkbox  optionMunicipalityHearsNeighbors
  Positive indicator should be visible
  Submit application

Kirjaamo got email
  Open last email
  Wait Until  Page Should Contain  ${email}
  [Teardown]  Go to login page
