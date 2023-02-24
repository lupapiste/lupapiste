*** Settings ***

Documentation   Authority admin edits automatic email templates
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Admin enables automatic emails for Sipoo
  SolitaAdmin logs in
  Go to page  organizations
  Fill test id  organization-search-term  753-r
  Scroll and click test id  edit-organization-753-R
  Wait until  Select checkbox  automaticEmailsEnabled
  [Teardown]  Logout

Sipoo goes to the automatic email page
  Sipoo logs in
  Go to page  automatic-emails

Sipoo creates two new templates
  Click by test id  automatic-email-template-add
  Click by test id  automatic-email-template-add

Two templates are now visible
  Element should be visible by test id  automatic-email-template-0-remove
  Element should be visible by test id  automatic-email-template-1-remove
  Element should not be visible by test id  automatic-email-template-2-remove

Sipoo enters text data into the first template
  Input text by test id  automatic-email-template-0-title  This is title
  Input text by test id  automatic-email-template-0-contents  Yes, this is contents

Sipoo selects some options from the autocompletes in the second template using the mouse
  Click by test id  automatic-email-template-1-operations
  Wait and click  jquery=.ac__items li:nth-of-type(3)
  Click by test id  automatic-email-template-1-states
  Wait and click  jquery=.ac__items li:nth-of-type(2)
  Click by test id  automatic-email-template-1-parties
  Wait and click  jquery=.ac__items li:nth-of-type(3)

Sipoo selects the same element again (or tries to anyway)
  Click by test id  automatic-email-template-1-operations
  Wait and click  jquery=.ac__items li:nth-of-type(3)

Sipoo selects a different element
  Click by test id  automatic-email-template-1-states
  Wait and click  jquery=.ac__items li:nth-of-type(5)

Sipoo reloads the page
  Reload page

The entered text has been saved
  Wait until  Textfield value should be  xpath=//input[@data-test-id="automatic-email-template-0-title"]  This is title
  Test id text is  automatic-email-template-0-contents  Yes, this is contents

The selected options have been saved
  Test id text is  automatic-email-template-1-operations  Rasitetoimitus
  Test id text is  automatic-email-template-1-states  KeskeytynytLuonnos
  Test id text is  automatic-email-template-1-parties  Erityissuunnittelijat

Sipoo deletes the first template
  Kill dev-box
  Click by test id  automatic-email-template-0-remove
  Click by test id  confirm-yes

Sipoo reloads the page again
  Reload page

Only the one template is now visible and it is the second one
  Wait until  Element should not be visible by test id  automatic-email-template-1-remove
  Wait until  Element should be visible by test id  automatic-email-template-0-remove
  Test id text is  automatic-email-template-0-operations  Rasitetoimitus
  Test id text is  automatic-email-template-0-states  KeskeytynytLuonnos
  Test id text is  automatic-email-template-0-parties  Erityissuunnittelijat
  Textfield value should be  xpath=//input[@data-test-id="automatic-email-template-0-title"]  ${EMPTY}

