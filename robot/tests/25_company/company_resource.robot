*** Settings ***

Documentation  Keywords for company robots
Resource       ../../common_resource.robot

*** Keywords ***

Kaino logs in
  [Arguments]  ${password}=kaino123
  Go to login page
  User logs in  kaino@solita.fi  ${password}  Kaino Solita

Invite existing dummy user
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${admin}=False  ${submit}=True  ${names}=False
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Test id disabled  company-search-email
  Input text by test id  company-new-user-email  ${email}
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email
  ${initial-firstname}=  Set Variable If  ${names}  ${firstname}  ${EMPTY}
  ${initial-lastname}=  Set Variable If  ${names}  ${lastname}  ${EMPTY}


  Textfield value should be  jquery=[data-test-id=company-new-user-firstname]  ${initial-firstname}
  Test id enabled  company-new-user-firstname
  Textfield value should be  jquery=[data-test-id=company-new-user-lastname]  ${initial-lastname}
  Test id enabled  company-new-user-lastname
  Input text by test id  company-new-user-firstname  ${firstname}
  Input text by test id  company-new-user-lastname  ${lastname}
  Run keyword if  ${admin}  Click label  company-new-user-admin
  Run keyword unless  ${submit}  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait Test id visible  company-add-user-done
  Click by test id  company-new-user-invited-close-dialog

Invite existing user
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${admin}=False  ${submit}=True
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Test id disabled  company-search-email
  Input text by test id  company-new-user-email  ${email}
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email

  Wait until  Element should be visible  companyUserDetails
  Textfield should contain  jquery=[data-test-id=company-new-user-firstname]  ${firstname}
  Test id disabled  company-new-user-firstname
  Textfield should contain  jquery=[data-test-id=company-new-user-lastname]  ${lastname}
  Test id disabled  company-new-user-lastname

  Run keyword if  ${admin}  Click label  company-new-user-admin
  Run keyword unless  ${submit}  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait Test id visible  company-add-user-done
  Click by test id  company-new-user-invited-close-dialog

Accept invitation
  [Arguments]  ${email}
  Open last email
  Wait Until  Page Should Contain  ${email}
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]
  Wait until  Element should be visible  invite-company-user

Check invitation
  [Arguments]  ${index}  ${email}  ${lastname}  ${firstname}  ${role}  ${submit}
  Test id should contain  invitation-lastname-${index}  ${lastname}
  Test id should contain  invitation-firstname-${index}  ${firstname}
  Test id should contain  invitation-email-${index}  ${email}
  Test id should contain  invitation-invited-${index}  Kutsuttu
  Test id should contain  invitation-role-${index}  ${role}
  Test id should contain  invitation-submit-${index}  ${submit}

Check company user
  [Arguments]  ${index}  ${email}  ${lastname}  ${firstname}  ${role}  ${submit}
  Test id should contain  company-user-lastname-${index}  ${lastname}
  Test id should contain  company-user-firstname-${index}  ${firstname}
  Test id should contain  company-user-email-${index}  ${email}
  Test id should contain  company-user-enabled-${index}  Käytössä
  Test id should contain  company-user-role-${index}  ${role}
  Test id should contain  company-user-submit-${index}  ${submit}

Edit company user
  [Arguments]  ${index}  ${role}  ${submit}
  Click by test id  company-user-edit-${index}
  Select from test id  company-user-edit-role-${index}  ${role}
  Select from test id by text  company-user-edit-submit-${index}  ${submit}

Lock company
  [Arguments]  ${company}  ${date}
  Click by test id  lock-company-${company}
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Test id disabled  confirm-yes
  Input text by test id  edit-date  ${date}
  Test id enabled  confirm-yes
  Click by test id  confirm-yes
  Test id text is  lock-date-${company}  ${date}
  Wait test id visible  unlock-company-${company}
  No such test id  lock-company-${company}

Not company user
  Open My Page
  Wait until  Element should be visible  xpath=//div[@data-test-id="mypage-register-company-accordion"]
