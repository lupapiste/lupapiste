*** Settings ***

Documentation   Authority admin view utils
Resource        ../../common_resource.robot

*** Variables ***



*** Keywords ***

Wait test id visible
  [Arguments]  ${id}
  Wait Until Element Is Visible  jquery=[data-test-id=${id}]

Wait test id hidden
  [Arguments]  ${id}
  Wait Until Element Is Not Visible  jquery=[data-test-id=${id}]

Test id empty
  [Arguments]  ${id}
  Wait test id visible  ${id}
  Textfield Value Should Be  jquery=[data-test-id=${id}]  ${EMPTY}

Test id disabled
  [Arguments]  ${id}
  Element should be disabled  jquery=[data-test-id=${id}]

Fill test id
  [Arguments]  ${id}  ${text}
  Wait test id visible  ${id}
  Element Should Be Enabled  jquery=[data-test-id=${id}]
  Input text by test id  ${id}  ${text}

# --------------------------------
# Authority admin view
# --------------------------------

Add guest authority start
  [Arguments]  ${email}
  Click Button  jquery=[data-test-id=guest-authority-add]
  Test id empty  guest-dialog-email
  Test id empty  guest-dialog-firstname
  Test id empty  guest-dialog-lastname
  Test id empty  guest-dialog-role
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-email  ${email}
  Focus  jquery=[data-test-id=guest-dialog-role]

Add existing user as authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Add guest authority start  ${email}
  Wait Until  Textfield Should Contain  jquery=[data-test-id=guest-dialog-firstname]  ${firstname}
  Textfield Should Contain  jquery=[data-test-id=guest-dialog-lastname]  ${lastname}
  Test id disabled  guest-dialog-firstname
  Test id disabled  guest-dialog-lastname

User table row contains
  [Arguments]  ${email}  ${data}
  Wait Until  Element Should Contain  xpath=//section[@id='users']//tr[@data-user-email='${email}']  ${data}

Guest authority table row contains
  [Arguments]  ${role}  ${data}
  Wait Until  Element Should Contain  jquery=tr[data-test-guest-role=${role}]  ${data}

Guest authority added
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  # In org user table
  # It seems to be impossible create a jquery selector or an attribute that contains @
  User table row contains  ${email}  ${lastname} ${firstname}
  User table row contains  ${email}  Hankekohtainen lukuoikeus

  Xpath Should Match X Times  //section[@id='users']//tr[@data-user-email='${email}']  1
  Guest authority table row contains  ${role}  ${role}
  Guest authority table row contains  ${role}  ${firstname} ${lastname}
  Guest authority table row contains  ${role}  ${email}

Add guest authority finish
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Fill test id  guest-dialog-role  ${role}
  Click Button  jquery=[data-test-id=guest-dialog-ok]
  Guest authority added  ${email}  ${firstname}  ${lastname}  ${role}

Add existing authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Add existing user as authority  ${email}  ${firstname}  ${lastname}  ${role}
  Add guest authority finish  ${email}  ${firstname}  ${lastname}  ${role}

Add new authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Add guest authority start  ${email}
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-firstname  ${firstname}
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-lastname  ${lastname}
  Add guest authority finish  ${email}  ${firstname}  ${lastname}  ${role}

Add bad authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Add existing user as authority  ${email}  ${firstname}  ${lastname}  ${role}
  Wait test id visible  guest-dialog-error
  Test id disabled  guest-dialog-ok
  Click Element  jquery=#dialog-add-guest-authority p.dialog-close.close

Delete guest authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${role}
  Click Link  jquery=tr[data-test-guest-role=${role}] a
  User table row contains  ${email}  ${lastname} ${firstname}
  User table row contains  ${email}  Hankekohtainen lukuoikeus

  Wait Until Page Does Not Contain Element  jquery=tr[data-test-guest-role=${role}]

Bad email address
  [Arguments]  ${email}
  Add guest authority start  ${email}
  Wait Until Element Is Visible  jquery=#dialog-add-guest-authority .form-input--error
  Click Element  jquery=#dialog-add-guest-authority p.dialog-close.close

# --------------------------------
# Application view
# --------------------------------

Guest bubble ok
  Element Should Be Enabled  jquery=.application-guests [data-test-id=bubble-dialog-ok]
  Click Button  jquery=.application-guests [data-test-id=bubble-dialog-ok]

Guest bubble cancel
  Click Button  jquery=.application-guests [data-test-id=bubble-dialog-cancel]

Guest table contains
  [Arguments]  ${data}
    Wait Until Element Contains  jquery=[data-test-id=application-guest-table]  ${data}

Guest row cell
  [Arguments]  ${username}  ${id}  ${data}
  Wait Until  Element Should Contain  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']/td[@data-test-id='${id}']  ${data}

Guest row name
  [Arguments]  ${username}  ${name}
  Guest row cell  ${username}  guest-name  ${name}

Guest row inviter
  [Arguments]  ${username}  ${inviter}
  Guest row cell  ${username}  guest-inviter  ${inviter}

Guest row role
  [Arguments]  ${username}  ${role}
  Guest row cell  ${username}  guest-role  ${role}

Guest row subscribed
  [Arguments]  ${username}
  Guest row cell  ${username}  guest-subscribe  Peruuta ilmoitukset

Guest row unsubscribed
  [Arguments]  ${username}
  Guest row cell  ${username}  guest-subscribe  Tilaa ilmoitukset

Guest row can delete
  [Arguments]  ${username}
  Element Should Be Visible  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']//i[contains(@class, "lupicon-remove")]

Guest row cannot delete
  [Arguments]  ${username}
  Element Should Not Be Visible  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']//i[contains(@class, "lupicon-remove")]

No delete column
  Page Should Not Contain  jquery=table[data-test-id=application-guest-table] .lupicon-remove

Guest delete
  [Arguments]  ${username}
  Click Element  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']//i[contains(@class, "lupicon-remove")]
  Page Should Not Contain  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']


Guest subscribe
  [Arguments]  ${username}
  Click Link  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']/td[@data-test-id='guest-subscribe']/a
  Guest row subscribed  ${username}

Guest unsubscribe
  [Arguments]  ${username}
  Click Link  xpath=//table[@data-test-id='application-guest-table']//tr[@data-test-id='${username}']/td[@data-test-id='guest-subscribe']/a
  Guest row unsubscribed  ${username}

No subscribe column
  Page Should Not Contain  jquery=table[data-test-id=application-guest-table] td[data-test-id=guest-subscribe]

Invite application guest start
  [Arguments]  ${email}  ${message}
  Wait test id visible  application-guest-add
  Click Button  jquery=[data-test-id=application-guest-add]
  Test id empty  application-guest-email
  Textarea Value Should be  jquery=[data-test-id=application-guest-message]  Hei! Sinulle on annettu lukuoikeus hakemukselle Lupapisteessä.
  Element Should Be Disabled  jquery=.application-guests [data-test-id=bubble-dialog-ok]
  Fill test id  application-guest-email  ${email}
  Fill test id  application-guest-message  ${message}

Invite application guest
  [Arguments]  ${email}  ${message}
  Invite application guest start  ${email}  ${message}
  Guest bubble ok

Bad guest email and cancel check
  Invite application guest start  bad.email  foo
  Element Should Be Disabled  jquery=.application-guests [data-test-id=bubble-dialog-ok]
  Guest bubble cancel
  # Check that the bubble is properly initialized after cancel as well.
  Invite application guest start  hii  hoo
  Guest bubble cancel

Invite application guest authority
  [Arguments]  ${name}  ${email}  ${role}  ${message}
  Wait test id visible  application-guest-add
  Click Button  jquery=[data-test-id=application-guest-add]
  Wait test id visible  application-guest-authorities
  Textarea Value Should be  jquery=[data-test-id=application-guest-message]  Hei! Sinulle on annettu lukuoikeus hakemukselle Lupapisteessä.
  Element Should Be Disabled  jquery=.application-guests [data-test-id=bubble-dialog-ok]
  Fill test id  application-guest-message  ${message}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${role}]  ${name}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${role}]  ${email}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${role}]  ${role}
  Select Checkbox  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${role}] input
  Guest bubble ok

No more guest authorities
  Wait test id visible  application-guest-add
  Click Button  jquery=[data-test-id=application-guest-add]
  Wait test id visible  application-guest-error
  Guest bubble cancel
