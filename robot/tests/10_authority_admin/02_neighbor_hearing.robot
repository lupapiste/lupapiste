*** Settings ***

Documentation   Email is send to municipality about hearing neighbors
Resource       ../../common_resource.robot
*** Test Cases ***

Sipoo admin sets email address for neighbor hearing notification
  Set Suite Variable  ${email}  kirjaamo@sipoo.example.com
  Sipoo logs in
  Go to page  applications
  Input text by test id  neighborOrderEmails  ${email}
  Wait until  Positive indicator should be visible
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

Try to open application
  Click link  xpath=//a

Link is was for authority, need to continue to login
  Wait Until  Click by test id  login
  User logs in  mikko@example.com  mikko123  Mikko Intonen
  Wait Until  Application address should be  Naapurikatu
  [Teardown]  Logout
