#*** Settings ***
#
#Documentation   Campaigns for company registrations
#Suite Setup     Apply minimal fixture now
#Resource        ../../common_resource.robot
#
#
#*** Test Cases ***
#
## Note on test strategy: Since the campaigns are managed in the admin
## admin UI, we do not aim for extensive front test coverage. The data
## consistency is enforced in the backend and fully tested in unit and
## itests. The robot tests make sure that the campaign definitions are
## reflected during the company registration.
#
## ---------------------
## Admin admin
## ---------------------
#Admin admin logs in and goes to campaigns tab
#  SolitaAdmin logs in
#  Go to page  campaigns
#
#Initially, no campaigs
#  Wait until  Element should not be visible  jquery=lupicon-remove.primary
#
#Add campaign foo
#  Add campaign  foo  1.3.2017  2.3.2017  1  2  3  10.3.2017
#
#Delete campaign foo
#  Click by test id  foo-delete
#  No such test id  foo-delete
#
## Currently, huhtikuu2017 is a hardcoded campaign code.
#
#Add huhtikuu2017 campaign that has ended
#  Add huhtikuu2017  -10  -8  0  0  0
#  [Teardown]  Logout
#
## ---------------------
## Anonymous
## ---------------------
#Company registration page does not show campaign information
#  Set campaign code
#  Wait and click  register-button
#  Click by test id  register-company-start
#  No such test id  account5-last-discount-date
#  No such test id  account5-old-price
#  No such test id  account5-small-print
#  [Teardown]  Logout
#
## ---------------------
## Admin admin
## ---------------------
#Admin admin logs in again
#  SolitaAdmin logs in
#  Go to page  campaigns
#
#Delete old campaign create new into the future
#  Click by test id  huhtikuu2017-delete
#  Add huhtikuu2017  1  2  0  0  0
#  [Teardown]  Logout
#
## ---------------------
## Anonymous
## ---------------------
#Still no campaign info on the registration page
#  Set campaign code
#  Wait and click  register-button
#  Click by test id  register-company-start
#  No such test id  account5-last-discount-date
#  No such test id  account5-old-price
#  No such test id  account5-small-print
#  [Teardown]  Logout
#
## ---------------------
## Admin admin
## ---------------------
#Admin admin logs in once more
#  SolitaAdmin logs in
#  Go to page  campaigns
#
#Delete old campaign and create new for today
#  Click by test id  huhtikuu2017-delete
#  Add huhtikuu2017  0  0  1  2  3
#  [Teardown]  Logout
#
## ---------------------
## Anonymous
## ---------------------
#Campaign info shown on the registration page
#  Set campaign code
#  Wait and click  register-button
#  Click by test id  register-company-start
#  Test id should contain  register-company-title  maksutta
#  Test id should contain  register-company-subtitle  maksutta ${last-date} asti
#  Test id text is  account5-price  1 €/kk
#  Test id text is  account15-price  2 €/kk
#  Test id text is  account30-price  3 €/kk
#  Test id text is  account5-last-discount-date  ${last-date} asti*
#  Test id text is  account5-old-price  59 €/kk
#  Test id should contain  account5-small-print  hinta ${regular-date} alkaen 59
#  Test id should contain  account5-small-print  kirjallisesti ${last-date} mennessä
#
#Bob selects account type and fills company info
#  Click by test id  account-type-account15
#  Scroll and click test id  register-company-continue
#  Input text by test id  register-company-name        Bob Construction
#  Input text by test id  register-company-y           6000315-8
#  Input text by test id  register-company-firstName   Bob
#  Input text by test id  register-company-lastName    Bobson
#  Input text by test id  register-company-address1    Street
#  Input text by test id  register-company-zip         12345
#  Input text by test id  register-company-po          City
#  Input text by test id  register-company-email       bob@bobconstruction.com
#  Input text by test id  register-company-personId    151196-979W
#  Scroll and click test id  register-company-continue
#
#Bob signs the contract
#  Toggle toggle  register-company-agree
#  Click enabled by test id  register-company-sign
#  Wait until  Element should be visible  xpath=//span[@data-test-id='onnistuu-dummy-status']
#  Wait until  Element text should be  xpath=//span[@data-test-id='onnistuu-dummy-status']  ready
#  Page should contain  151196-979W
#  Click enabled by test id  onnistuu-dummy-success
#  Wait until  Element should be visible  xpath=//section[@id='register-company-success']
#
#Luapiste notification email has campaign information
#  Open last email  False
#  Element should contain  xpath=//dd[@data-test-id='subject']  KAMPANJA
#  Page should contain  Kampanjakoodi: huhtikuu2017
#
#Bob also receives email and can set his password
#  Open all latest emails
#  Wait until  Page should contain  new-company-user
#  Click element  xpath=(//a[contains(., 'new-company-user')])
#  Wait Until  Element should be visible  new-company-user
#  Fill in new company password  new-company-user  foobar789
#
#Bob logs in
#  Login  bob@bobconstruction.com  foobar789
#  User should be logged in  Bob Bobson
#  Confirm notification dialog
#  Click element  user-name
#  Open accordion by test id  mypage-company-accordion
#  Test id text is  my-company-name  Bob Construction
#  Test id text is  my-company-id  6000315-8
#  Page should contain  /dev/dummy-onnistuu/doc/
#
#Company info page has the registered information
#  Scroll and click test id  company-edit-info
#  Test id select is  company-account-select  account15
#  # Company info always shows regular price
#  Test id select text is  company-account-select  Yritystili 15 (79 €/kk)
#  Test id input is  edit-company-name        Bob Construction
#  Test id input is  edit-company-y           6000315-8
#  Test id input is  edit-company-address1    Street
#  Test id input is  edit-company-zip         12345
#  Test id input is  edit-company-po          City
#  [Teardown]  logout
#
#
#*** Keywords ***
#
#Add campaign
#  [Arguments]  ${code}  ${starts}  ${ends}  ${account5}  ${account15}  ${account30}  ${last-discount-date}
#  Wait test id visible  add-campaign
#  Click by test id  add-campaign
#  Input text by test id  edit-campaign-code  ${code}
#  Execute JavaScript  $(".hasDatepicker").unbind("focus");
#  Input text by test id  edit-campaign-starts  ${starts}
#  Input text by test id  edit-campaign-ends  ${ends}
#  Input text by test id  edit-campaign-account5  ${account5}
#  Input text by test id  edit-campaign-account15  ${account15}
#  Input text by test id  edit-campaign-account30  ${account30}
#  Input text by test id  edit-campaign-last-discount-date  ${last-discount-date}
#  Click by test id  bubble-dialog-ok
#  Wait until  Test id text is  ${code}-code  ${code}
#  No such test id  edit-campaign-code
#  Test id text is  ${code}-starts  ${starts}
#  Test id text is  ${code}-ends  ${ends}
#  Test id text is  ${code}-account5  ${account5}
#  Test id text is  ${code}-account15  ${account15}
#  Test id text is  ${code}-account30  ${account30}
#  Test id text is  ${code}-last-discount-date  ${last-discount-date}
#
#Add huhtikuu2017
#  [Arguments]  ${start-days}  ${end-days}  ${account5}  ${account15}  ${account30}
#  ${starts}=   Execute Javascript  return moment().add( ${start-days}, "days").format( "D.M.YYYY");
#  ${ends}=   Execute Javascript  return moment().add( ${end-days}, "days").format( "D.M.YYYY");
#  ${last}=  Execute Javascript  return moment().add( ${end-days} + 1, "days").format( "D.M.YYYY");
#  ${regular}=  Execute Javascript  return moment().add( ${end-days} + 2, "days").format( "D.M.YYYY");
#  Set Suite Variable  ${last-date}  ${last}
#  Set Suite Variable  ${regular-date}  ${regular}
#  Add campaign  huhtikuu2017  ${starts}  ${ends}  ${account5}  ${account15}  ${account30}  ${last}
#
#Set campaign code
#  Execute Javascript  lupapisteApp.services.campaignService.code( "huhtikuu2017")
