*** Settings ***

Documentation   Authority admin view utils
Resource        ../../common_resource.robot

*** Keywords ***

# --------------------------------
# Authority admin view
# --------------------------------

Add guest authority start
  [Arguments]  ${email}
  Scroll to test id  guest-authority-add
  Scroll And Click  [data-test-id=guest-authority-add]
  Test id empty  guest-dialog-email
  Test id empty  guest-dialog-firstname
  Test id empty  guest-dialog-lastname
  Test id empty  guest-dialog-description
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-email  ${email}
  Focus  jquery=[data-test-id=guest-dialog-description]

Add existing user as authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Add guest authority start  ${email}
  Wait Until  Textfield Should Contain  jquery=[data-test-id=guest-dialog-firstname]  ${firstname}
  Textfield Should Contain  jquery=[data-test-id=guest-dialog-lastname]  ${lastname}
  Test id disabled  guest-dialog-firstname
  Test id disabled  guest-dialog-lastname

User table row contains
  [Arguments]  ${email}  ${data}
  Wait Until  Element Should Contain  xpath=//section[@id='users']//tr[@data-user-email='${email}']  ${data}

Guest authority table row contains
  [Arguments]  ${description}  ${data}
  Wait Until  Element Should Contain  jquery=tr[data-test-guest-description=${description}]  ${data}

Guest authority added
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  # In org user table
  # It seems to be impossible create a jquery selector or an attribute that contains @
  User table row contains  ${email}  ${lastname} ${firstname}
  User table row contains  ${email}  Hankekohtainen lukuoikeus

  Xpath Should Match X Times  //section[@id='users']//tr[@data-user-email='${email}']  1
  Guest authority table row contains  ${description}  ${description}
  Guest authority table row contains  ${description}  ${firstname} ${lastname}
  Guest authority table row contains  ${description}  ${email}

Add guest authority finish
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Fill test id  guest-dialog-description  ${description}
  Scroll And Click  [data-test-id=guest-dialog-ok]
  Guest authority added  ${email}  ${firstname}  ${lastname}  ${description}

Add existing authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Add existing user as authority  ${email}  ${firstname}  ${lastname}  ${description}
  Add guest authority finish  ${email}  ${firstname}  ${lastname}  ${description}

Add new authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Add guest authority start  ${email}
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-firstname  ${firstname}
  Test id disabled  guest-dialog-ok
  Fill test id  guest-dialog-lastname  ${lastname}
  Add guest authority finish  ${email}  ${firstname}  ${lastname}  ${description}

Add bad authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Add existing user as authority  ${email}  ${firstname}  ${lastname}  ${description}
  Wait test id visible  guest-dialog-error
  Test id disabled  guest-dialog-ok
  Click Element  jquery=#dialog-add-guest-authority p.dialog-close.close

Delete guest authority
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${description}
  Click Link  jquery=tr[data-test-guest-description=${description}] a
  User table row contains  ${email}  ${lastname} ${firstname}
  User table row contains  ${email}  Hankekohtainen lukuoikeus

  Wait Until Page Does Not Contain Element  jquery=tr[data-test-guest-description=${description}]

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
  #Scroll to test id  bubble-dialog-ok
  Scroll and click  .application-guests [data-test-id=bubble-dialog-ok]

Guest bubble cancel
  Wait test id visible  bubble-dialog-cancel
  #Scroll to test id  bubble-dialog-cancel
  Scroll and click  .application-guests [data-test-id=bubble-dialog-cancel]
  Wait test id hidden  bubble-dialog-cancel

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

Guest row description
  [Arguments]  ${username}  ${description}
  Guest row cell  ${username}  guest-description  ${description}

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
  Wait test id hidden  bubble-dialog-ok
  Scroll to test id  application-guest-add
  Wait test id visible  application-guest-add
  Scroll and click  [data-test-id=application-guest-add]
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
  [Arguments]  ${name}  ${email}  ${description}  ${message}
  Wait test id visible  application-guest-add
  Scroll and click  [data-test-id=application-guest-add]
  Wait test id visible  application-guest-authorities
  Textarea Value Should be  jquery=[data-test-id=application-guest-message]  Hei! Sinulle on annettu lukuoikeus hakemukselle Lupapisteessä.
  Element Should Be Disabled  jquery=.application-guests [data-test-id=bubble-dialog-ok]
  Fill test id  application-guest-message  ${message}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${description}]  ${name}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${description}]  ${email}
  Element Should Contain  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${description}]  ${description}
  Scroll to  table[data-test-id=application-guest-authorities] tr[data-test-id=${description}] input
  Select Checkbox  jquery=table[data-test-id=application-guest-authorities] tr[data-test-id=${description}] input
  Guest bubble ok

No more guest authorities
  Wait test id visible  application-guest-add
  Scroll and click  [data-test-id=application-guest-add]
  Wait test id visible  application-guest-error
  Guest bubble cancel
