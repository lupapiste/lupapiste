*** Settings ***

Documentation   Authority admin edits organization specific Kopiolaitos fields
Suite Teardown  Logout
Resource        ../../common_resource.robot


*** Test Cases ***

AuthAdmin logs in and sees Kopiolaitos fields are empty
  Naantali logs in
  Go to page  backends

  Wait until  Element should be visible  kopiolaitos-info
  Element text should be  kopiolaitos-email  ${EMPTY}
  Element text should be  kopiolaitos-orderer-address  ${EMPTY}
  Element text should be  kopiolaitos-orderer-phone  ${EMPTY}
  Element text should be  kopiolaitos-orderer-email  ${EMPTY}

Auth admin opens Kopiolaitos dialog, inputs values and submits
  Set Suite Variable  ${email}  naantali@example.com
  Set Suite Variable  ${orderer-address}  Testikatu 1, 12345 Naantali
  Set Suite Variable  ${orderer-phone}  0501231234
  Set Suite Variable  ${orderer-email}  tilaaja@example.com

  Scroll and click test id  authadmin-edit-button-kopiolaitos-info
  Element should be visible  dialog-edit-kopiolaitos-info
  Element should be enabled  kopiolaitos-info-submit

  # email validator
  Input text  edit-kopiolaitos-email  sipoo@sipoo
  Element should be disabled  kopiolaitos-info-submit

  Input text  edit-kopiolaitos-email  ${email}
  Element should be enabled  kopiolaitos-info-submit
  Input text  edit-kopiolaitos-orderer-address  ${orderer-address}
  Input text  edit-kopiolaitos-orderer-phone  ${orderer-phone}
  Input text  edit-kopiolaitos-orderer-email  ${orderer-email}

  Click element  kopiolaitos-info-submit

Auth sees that values are set
  Wait for jQuery
  Wait until  Textfield value should be  kopiolaitos-email  ${email}
  Textfield value should be  kopiolaitos-orderer-address  ${orderer-address}
  Textfield value should be  kopiolaitos-orderer-phone  ${orderer-phone}
  Textfield value should be  kopiolaitos-orderer-email  ${orderer-email}
