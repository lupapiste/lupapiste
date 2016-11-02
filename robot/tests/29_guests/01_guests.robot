*** Settings ***

Documentation   Guests and guest authorities
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource       ../13_statements/statement_resource.robot
Resource        guest_resource.robot

*** Test Cases ***

# -------------------------------------
# Authority admin
# -------------------------------------

Authority admin goes to the authority admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Add Veikko as guest authority Saunamajuri to Sipoo
  Set Suite Variable  ${veikko}  veikko.viranomainen@tampere.fi
  Set Suite Variable  ${veikko-name}  Veikko Viranomainen (veikko)
  Wait Until   Element should contain  xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']  Sibbo Ronja
  Add existing authority  ${veikko}  Veikko  Viranomainen  Talonvahti
  User table does not contain  ${veikko}

Change Veikko's guest authority description
  Add existing authority  ${veikko}  Veikko  Viranomainen  Saunamajuri

Add new user Richard Guest
  Add new authority  richard.guest@example.com  Richard  Guest  Random

Delete guest authority Richard Guest
  Delete guest authority  richard.guest@example.com  Richard  Guest  Random

Luukas cannot be guest authority
  Add bad authority  luukas.lukija@sipoo.fi  Luukas  Lukija  -

Create new statement giver and add it as guest authority
  Create statement giver  statement@giver.net  Statue
  Wait test id visible  guest-authority-add
  Add new statement giver as authority  statement@giver.net  Statement  Giver  Geiwoba

Delete new statement giver from authority table
  Delete new statement giver guest authority  statement@giver.net  Geiwoba

Bad authority email
  Bad email address  foobar
  [Teardown]  Logout

# -------------------------------------
# Applicant
# -------------------------------------

Pena creates an application
  Set Suite Variable  ${appname}  app-with-guests
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

No guests yet
  Open tab  parties
  Wait test id visible  application-guest-add

Add bubble sanity check
  Bad guest email and cancel check

Pena invites some random guest
  Set Suite Variable  ${random}  some.random@example.com
  Invite application guest  ${random}  Hello!
  Guest table contains  ${random}

Pena invites Mikko as guest
  Set Suite Variable  ${mikko}  mikko@example.com
  Set Suite Variable  ${mikko-message}  I want to add you to my professional network on Lupapiste.
  Set Suite Variable  ${mikko-name}  Mikko Intonen (mikko@example.com)
  Invite application guest  ${mikko}  ${mikko-message}
  Guest table contains  ${mikko-name}
  Guest row inviter  ${mikko}  Pena Panaani
  Open last email
  Wait Until  Page Should Contain  ${mikko-message}
  Go back

Mikko cannot be invited again
  Redundant invitation  ${mikko}

Pena submits application
  Submit application

Pena enters a comment to the application
  Add comment  This be not visible to Mikko :)
  [Teardown]  Logout

# -------------------------------------
# Guest
# -------------------------------------

Mikko logs in and can see the application
  Mikko logs in
  Applications page should be open

  Element Should Contain  jquery=tr.application-row td[data-test-col-name=location]  ${appname}, Sipoo
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Wait test id hidden  application-guest-add
  Wait test id hidden  application-invite-person
  Wait test id hidden  application-invite-company
  Wait Until Element Is Not Visible  jquery=[id=applicationUnseenComments]
  Wait Until Element Is Not Visible  jquery=[id=open-conversation-side-panel]
  Guest row name  ${mikko}  ${mikko-name}
  Guest row inviter  ${mikko}  Pena Panaani
  Guest row subscribed  ${mikko}
  No delete column

Mikko unsubscribes and subscribes to notifications
  Guest unsubscribe  ${mikko}
  Guest subscribe  ${mikko}
  [Teardown]  Logout

# -------------------------------------
# Authority
# -------------------------------------

Sonja logs in and can invite guests
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Wait test id visible  application-guest-add
  Guest row name  ${mikko}  ${mikko-name}
  Guest row subscribed  ${mikko}
  Guest row can delete  ${mikko}
  Guest row subscribed  ${random}
  Guest row can delete  ${random}

Sonja can unsubscribe and delete guests
  Guest unsubscribe  ${random}
  Guest delete  ${random}

Sonja invites Veikko as guest authority
  Set Suite Variable  ${veikko-message}  Respect my guest authoritah!
  Invite application guest authority  Veikko Viranomainen  ${veikko}  Saunamajuri  ${veikko-message}
  Guest table contains  ${veikko-name}
  Guest row inviter  veikko  Sonja Sibbo
  Guest table contains  Saunamajuri

Sonja can unsubscribe guest authorities
  Guest unsubscribe  veikko

No more guest authorities defined
  No more guest authorities

Check Veikko's invitation email
  Open last email
  Wait Until  Page Should Contain  ${veikko-message}
  Go to  ${LOGOUT URL}
  [Teardown]  Logout


# -------------------------------------
# Guest authority
# -------------------------------------

Veikko logs in and can see the application
  Veikko logs in
  Authority applications page should be open

  Element Should Contain  jquery=tr.application-row td[data-test-col-name=location]  ${appname}, Sipoo
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Wait test id hidden  application-guest-add
  Wait test id hidden  application-invite-person
  Wait test id hidden  application-invite-company
  Wait Until Element Is Not Visible  jquery=[id=applicationUnseenComments]
  Wait Until Element Is Not Visible  jquery=[id=open-conversation-side-panel]
  Guest row name  veikko  ${veikko-name}
  Guest row inviter  veikko  Sonja Sibbo
  Guest row unsubscribed  veikko
  Guest row description  veikko  Saunamajuri
  No delete column

Veikko subscribes and unsubscribes to notifications
  Guest subscribe  veikko
  Guest unsubscribe  veikko
  [Teardown]  Logout

# -------------------------------------
# Reader
# -------------------------------------

Luukas logs in and can see the application
  Luukas logs in
  Authority applications page should be open

  Element Should Contain  jquery=tr.application-row td[data-test-col-name=location]  ${appname}, Sipoo
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Wait test id hidden  application-guest-add
  Wait test id hidden  application-invite-person
  Wait test id hidden  application-invite-company
  Wait Until Element Is Not Visible  jquery=[id=applicationUnseenComments]
  Wait Until Element Is Not Visible  jquery=[id=open-conversation-side-panel]
  Guest row name  veikko  ${veikko-name}
  Guest row inviter  veikko  Sonja Sibbo
  Guest row description  veikko  Saunamajuri
  Guest row name  ${mikko}  ${mikko-name}
  No subscribe column
  No delete column
  [Teardown]  Logout

# -------------------------------------
# Applicant again
# -------------------------------------

Pena is back and rescinds Mikko's invitation
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Guest row cannot delete  veikko
  Guest row can delete  ${mikko}
  Guest delete  ${mikko}
  [Teardown]  Logout

# -------------------------------------
# Authority again
# -------------------------------------

Sonja returns and deletes the last guest Veikko
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Guest row can delete  veikko
  Guest delete  veikko
  Wait test id hidden  application-guest-table
  [Teardown]  Logout

# -------------------------------------
# Guest again
# -------------------------------------

Mikko logs in but cannot access application
  Mikko logs in
  Applications page should be open
  Page Should Not Contain  ${appname}
  [Teardown]  Logout

# -------------------------------------
# Guest authority again
# -------------------------------------

Veikko logs in but cannot access application
  Veikko logs in
  Authority applications page should be open
  Page Should Not Contain  ${appname}
  [Teardown]  Logout


# -------------------------------------
# Authority one more time
# -------------------------------------

Sonja logs in and invites Veikko again
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-add
  Invite application guest authority  Veikko Viranomainen  ${veikko}  Saunamajuri  ${veikko-message}
  Guest table contains  ${veikko-name}
  Guest row inviter  veikko  Sonja Sibbo
  Guest table contains  Saunamajuri
  [Teardown]  Logout

# -------------------------------------
# Guest authority once more
# -------------------------------------

Veikko logs in again and can see the application
  Veikko logs in
  Authority applications page should be open

  Element Should Contain  jquery=tr.application-row td[data-test-col-name=location]  ${appname}, Sipoo
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-table
  Wait test id hidden  application-guest-add
  Wait test id hidden  application-invite-person
  Wait test id hidden  application-invite-company
  Guest row name  veikko  ${veikko-name}
  Guest row inviter  veikko  Sonja Sibbo
  [Teardown]  Logout


# -------------------------------------
# Authority admin again
# -------------------------------------

Authority admin removes Veikko from the guest authorities
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset
  Delete guest authority  ${veikko}  Veikko  Viranomainen  Saunamajuri
  [Teardown]  Logout


# -------------------------------------
# Guest authority again
# -------------------------------------

Veikko logs in but cannot access application again
  Veikko logs in
  Authority applications page should be open
  Page Should Not Contain  ${appname}
  [Teardown]  Logout

# -------------------------------------
# Authority one last time
# -------------------------------------

Sonja logs in and sees no guests
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  parties
  Wait test id visible  application-guest-add
  Wait test id hidden  application-guest-table
  [Teardown]  Logout
