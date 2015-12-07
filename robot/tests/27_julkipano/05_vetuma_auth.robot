*** Settings ***

Documentation   User authenticates to Julkipano.fi via Vetuma
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot
Resource        ../common_keywords/vetuma_helpers.robot

*** Test Cases ***

User can authenticate via Vetuma
  Create bulletins  1
  Go to bulletins page
  Open bulletin by index  1

  Open bulletin tab  info
  Element should be visible by test id  vetuma-init
  Element should not be visible by test id  bulletin-comment-box-form

  Authenticate via Nordea via Vetuma

  Open bulletin tab  info
  Element should not be visible by test id  vetuma-init
  Element should be visible by test id  bulletin-comment-box-form

User can logout after authentication
  Element should be visible  //div[@data-test-id='user-nav-menu']
  Click by test id  vetuma-logout
  Wait Until  Element should not be visible  //div[@data-test-id='user-nav-menu']

User is shown error when authentication via Vetuma canceled
  Go to bulletins page
  Open bulletin by index  1

  Open bulletin tab  info
  Start Vetuma authentication but cancel via Nordea
  Wait Until  Element should be visible by test id  indicator-negative
  Page should contain  Vetuma-tunnistautuminen peruutettiin
