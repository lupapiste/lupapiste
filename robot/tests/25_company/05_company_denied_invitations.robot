*** Settings ***

Documentation   Companys can denied user to be invited to applications without company
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../29_guests/guest_resource.robot
Resource        ../22_foreman/keywords.robot
Resource        company_resource.robot
Default Tags    company

*** Variables ***

${error_message}    Kyseinen henkil\u00f6 kuuluu yritykseen, jolla on Lupapisteess\u00e4 Yritystili. Valtuuta ensin yritys hankkeelle.
${dummy}            dummy3@example.com

*** Test Cases ***

Company admin denies company user invitations
  Kaino logs in
  Open company user listing
  Click enabled by test id  checkbox-invitations-denied

Company admin invites user to company
  Invite existing dummy user  ${dummy}  Duff3  Dummy3
  Check invitation  0  ${dummy}  Dummy3  Duff3  Käyttäjä  Kyllä
  Logout

User accepts invite
  Accept invitation  dummy3@example.com
  Wait Until  Page Should Contain  Tilisi on liitetty onnistuneesti yrityksen tiliin.
  Click link  Siirry Lupapiste-palveluun
  Open last email
  Wait Until  Page Should Contain  ${dummy}
  Page Should Contain  /app/fi/welcome#!/setpw/
  Go to login page

Oragnization admin adds guest authority
  Sipoo logs in
  Add existing authority  ${dummy}  Duff3  Dummy3  Talonvahti
  Logout

Creates application
  Sonja logs in  False
  Create application the fast way  hakemus  753-416-45-1  kerrostalo-rivitalo

Company user cant be invited to application
  Open tab  parties
  Scroll and click test id  application-invite-person
  Scroll to test id  person-invite-bubble-dialog-ok
  Wait until  Element should be visible  person-invite-email-2
  Input Text  person-invite-email-2  ${dummy}
  Element should be enabled  xpath=//*[@data-test-id='person-invite-bubble-dialog-ok']
  Click by test id  person-invite-bubble-dialog-ok
  Wait until  Page should contain  ${error_message}
  Click by test id  person-invite-bubble-dialog-cancel

Company user cannot be invited as guest authority
  Invite application guest authority  Duff3 Dummy3  ${dummy}  Talonvahti  Hello
  Wait until  Page should contain  ${error_message}
  Click by test id  guest-bubble-dialog-cancel

Company user cannot be invited as foreman
  Open foreman accordions
  Wait until  Click by test id  invite-foreman-button
  Sleep  1s
  Wait until  Input Text  invite-foreman-email  ${dummy}
  Click by test id  application-invite-foreman
  Page should contain  ${error_message}
  Click by test id  cancel-foreman-dialog

Invites company
  Invite company to application  Solita Oy

Company user can be invited to application
  Open tab  parties
  Click by test id  application-invite-person
  Scroll to test id  person-invite-bubble-dialog-ok
  Wait until  Element should be visible  person-invite-email-2
  Input Text  person-invite-email-2  ${dummy}
  Element should be enabled  xpath=//*[@data-test-id='person-invite-bubble-dialog-ok']
  Click by test id  person-invite-bubble-dialog-ok
  Wait until  Element should not be visible  xpath=//div[@id='modal-dialog-content']
  Wait until  Invite count is  2

Company cannot be removed since company user is authorized
  Click by test id  remove-auth-1060155-5
  Confirm yes no dialog
  Wait test id visible  remove-invitation-denied-company-error-dialog
  Element text should be  jquery=[data-test-id=remove-invitation-denied-company-error-dialog] ul  Duff3 Dummy3
  Confirm ok dialog

Remove dummy authorization
  Click by test id  remove-auth-dummy3
  Confirm yes no dialog
  Wait until  Invite count is  1

Company user can be invited as guest
  Invite application guest authority  Duff3 Dummy3  ${dummy}  Talonvahti  Hello
  Guest table contains  Duff3 Dummy3

Company user can be invited as foreman
  Open foreman accordions
  Wait until  Click by test id  invite-foreman-button
  Sleep  1s
  Wait until  Input Text  invite-foreman-email  ${dummy}
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
