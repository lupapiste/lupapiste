*** Settings ***

Documentation  Organization handling times
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       handlers_resource.robot

*** Variables ***
${lateProp}     753-416-25-30
${laterProp}    753-416-25-31
${nonLateProp}  753-416-25-32
${otherProp}    753-416-25-33
${lateApp}      Listakatu 1
${laterApp}     Listakatu 2
${nonLateApp}   Listakatu 3
${otherApp}     Muukatu 1

*** Test Cases ***

# -------------------------
# Authority admin
# -------------------------
Sipoo logs in and sets the handling time
  Sipoo logs in
  Go to page  applications
  Click by test id  organization-handling-time-enabled
  Input text by test id  organization-handling-time-field  3
  Reload page
  Page should contain  Käsittelyaika päivissä
  Textfield value should be  xpath=//input[@data-test-id="organization-handling-time-field"]  3
  [Teardown]  Logout

# -------------------------
# Applicant
# -------------------------
Pena logs in and creates applications
  ${secs} =      Get time  epoch
  ${nowTs} =     Evaluate  ${secs} * 1000
  ${weekAgo} =   Evaluate  ${nowTs} - 1000 * 60 * 60 * 24 * 7
  ${monthAgo} =  Evaluate  ${nowTs} - 1000 * 60 * 60 * 24 * 30
  Pena logs in
  # Submitted today
  Create application with state  ${nonLateApp}  ${nonLateProp}  pientalo  submitted
  Page should not contain  Käsittelyaika
  # Submitted a week ago
  Create application with state  ${lateApp}  ${lateProp}  pientalo  submitted
  Set application timestamp  ${lateApp}  submitted  ${weekAgo}
  Page should not contain  Käsittelyaika
  # Submitted a month ago
  Create application with state  ${laterApp}  ${laterProp}  pientalo  submitted
  Set application timestamp  ${laterApp}  submitted  ${monthAgo}
  Page should not contain  Käsittelyaika
  # Sent app
  Create application with state  ${otherApp}  ${otherProp}  pientalo  sent
  Page should not contain  Käsittelyaika

Pena does not see the subheaders as an applicant
  Go to page  applications
  Click by test id  search-tab-application
  Page should not contain  Hakemukset joiden käsittelyaika on ylittynyt
  Page should not contain  Muut hankkeet
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja logs in and sees the subheaders as an authority
  Sonja logs in
  Go to page  applications
  # Subheaders not shown in "all apps" tab
  Page should not contain  Hakemukset joiden käsittelyaika on ylittynyt
  Click by test id  search-tab-application
  Page should contain  Hakemukset joiden käsittelyaika on ylittynyt
  Page should contain  Muut hankkeet

Sonja looks at the handling times in the app summaries
  # Submitted today
  Open application  ${nonLateApp}  ${nonLateProp}
  Page should contain  Käsittelyaika
  Page should contain  3 päivää jäljellä
  # Submitted a week ago
  Open application  ${lateApp}  ${lateProp}
  Page should contain  Käsittelyaika
  Page should contain  4 päivää yliaikaa
  # Submitted a month ago
  Open application  ${laterApp}  ${laterProp}
  Page should contain  Käsittelyaika
  Page should contain  27 päivää yliaikaa
  # Sent app
  Open application  ${otherApp}  ${otherProp}
  Page should not contain  Käsittelyaika
  [Teardown]  Logout

# -------------------------
# Authority admin
# -------------------------
Sipoo logs in and removes the handling time
  Sipoo logs in
  Go to page  applications
  Click by test id  organization-handling-time-enabled
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja logs in and no longer sees the subheaders
  Sonja logs in
  Go to page  applications
  Click by test id  search-tab-application
  Page should not contain  Hakemukset joiden käsittelyaika on ylittynyt
  Page should not contain  Muut hankkeet

Sonja no longer sees the handling times in the app summaries
  # Submitted today
  Open application  ${nonLateApp}  ${nonLateProp}
  Page should not contain  Käsittelyaika
  Page should not contain  3 päivää jäljellä
  # Submitted a week ago
  Open application  ${lateApp}  ${lateProp}
  Page should not contain  Käsittelyaika
  Page should not contain  4 päivää yliaikaa
  # Submitted a month ago
  Open application  ${laterApp}  ${laterProp}
  Page should not contain  Käsittelyaika
  Page should not contain  27 päivää yliaikaa
  # Sent app
  Wait Until  Open application  ${otherApp}  ${otherProp}
  Page should not contain  Käsittelyaika
  [Teardown]  Logout
