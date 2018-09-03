*** Settings ***

Documentation  Company locking
Suite Setup    Apply minimal fixture now
Default Tags   company
Resource       ../../common_resource.robot
Resource       company_resource.robot

*** Test Cases ***

# -------------------
# Company admin
# -------------------
Kaino logs in
  Kaino logs in

Kaino toggles between tabs but back button still works as expected
  Open company details
  Wait test id visible  company-info-tab
  Company tab  users
  No such test id  company-info-tab
  Company tab  info
  Wait test id visible  company-info-tab
  Company tab  users
  No such test id  company-info-tab
  Scroll and click test id  company-back
  Wait until  Element should be visible  own-info-form
  Scroll and click test id  back-button
  Wait until  Applications page should be open

Kaino invites Pena to Solita
  Open company user listing
  Invite existing user  pena@example.com  Pena  Panaani
  [Teardown]  Logout

Accept invitation for Pena
  Accept invitation  pena@example.com
  Wait until  Element should be visible  xpath=//section[@id='invite-company-user']//p[@data-test-id='invite-company-user-success']

Kaino invites Teppo to Solita
  Kaino logs in
  Open company user listing
  Invite existing user  teppo@example.com  Teppo  Nieminen
  [Teardown]  Logout

# -------------------
# Admin admin
# -------------------
Admin admin locks Solita
  SolitaAdmin logs in
  Go to page  companies
  ${today}=  Execute Javascript  return moment().format("D.M.YYYY");
  Lock company  solita  ${today}
  [Teardown]  Logout

# -------------------
# Company admin
# -------------------
Kaino logs in and sees Solita locked
  Kaino logs in
  Company is locked for admin
  [Teardown]  Logout

# -------------------
# Company user
# -------------------
Pena locks in but locking status is not visible to him
  Pena logs in
  Company is locked for user
  [Teardown]  Logout

# -------------------
# Admin admin
# -------------------
Admin admin locks Solita in the future
  SolitaAdmin logs in
  Go to page  companies
  Scroll and click test id  unlock-company-solita
  ${tomorrow}=  Execute Javascript  return moment().add( 1, "days").format("D.M.YYYY");
  Lock company  solita  ${tomorrow}
  [Teardown]  Logout

# -------------------
# Company admin
# -------------------
Kaino logs in and sees Solita unlocked
  Kaino logs in
  Company is not locked
  [Teardown]  Logout

# -------------------
# Admin admin
# -------------------
Admin admin locks Solita in the past
  SolitaAdmin logs in
  Go to page  companies
  Scroll and click test id  unlock-company-solita
  ${yesterday}=  Execute Javascript  return moment().subtract( 1, "days").format("D.M.YYYY");
  Lock company  solita  ${yesterday}
  [Teardown]  Logout

# -------------------
# Company admin
# -------------------
Kaino logs in and sees Solita again locked
  Kaino logs in
  Company is locked for admin

Kaino nukes company
  Scroll and click test id  company-nuke-all
  Scroll and click test id  confirm-no
  Company is locked for admin
  Scroll and click test id  company-nuke-all
  Scroll and click test id  confirm-yes
  User should not be logged in

# -------------------
# Random
# -------------------
Teppo's invitation has been canceled
  Accept invitation  teppo@example.com
  Wait until  Page should contain  Tilin liittäminen yrityksen tiliin epäonnistui.

# -------------------
# (former) Company admin
# -------------------
Kaino cannot login
  Go to login page
  Login fails  kaino@solita.fi  kaino123

Kaino cannot reset password
  Cannot reset password  kaino@solita.fi

# -------------------
# (former) Company user
# -------------------
Pena can login but is no longer company user
  Go to login page
  Pena logs in
  Not company user
  [Teardown]  Logout


*** Keywords ***

Company tab
  [Arguments]  ${tab}
  Click element  jquery=a[data-tab-id=${tab}]

Company is locked for admin
  Open company details
  Wait test id visible  company-is-locked
  Test id disabled  company-details-edit
  Open company user listing
  Wait test id visible  company-user-uninvite-0
  Wait test id visible  company-user-delete-0
  No such test id  company-user-edit-0
  Wait test id visible  company-nuke-all
  No such test id  company-add-user

Company is locked for user
  Open company details
  Wait test id visible  company-is-locked
  Open company user listing
  No such test id  company-nuke-all

Company is not locked
  Open company details
  No such test id  company-is-locked
  Test id enabled  company-details-edit
  Open company user listing
  Wait test id visible  company-user-uninvite-0
  Wait test id visible  company-user-delete-0
  Wait test id visible  company-user-edit-0
  No such test id  company-nuke-all
  Wait test id visible  company-add-user

Reset password
  [Arguments]  ${email}  ${password}
  Click link  jquery=div.passwd-reset a
  Input text with jQuery  input.form-input:visible  ${email}
  Scroll and click test id  reset-send
  Wait until  Test id disabled  reset-send
  Open last email
  Page Should Contain  /app/fi/welcome#!/setpw
  Click link  xpath=//a[contains(@href,'setpw')]
  Input text with jQuery  input.form-input:visible:first  ${password}
  Input text with jQuery  input.form-input:visible:last  ${password}
  Click button  jquery=button.btn-primary:visible
  Wait until  Element should be visible  jquery=button.btn-dialog
  Click button  jquery=button.btn-dialog

Cannot reset password
  [Arguments]  ${email}
  Click Element  //section[@id='login']
  Click link  jquery=div.passwd-reset a
  Input text with jQuery  input.form-input:visible  ${email}
  Scroll and click test id  reset-send
  Wait until  Page should contain  Antamaasi sähköpostiosoitetta ei löydy järjestelmästä.
