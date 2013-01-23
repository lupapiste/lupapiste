*** Settings ***

Documentation   Application invites
Test setup      Wait Until  Ajax calls have finished
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Open application
  Element should not be visible  test-add-invite
  Logout

Mikko (the Applicant) is allowed to invite people
  Mikko logs in
  Open application
  Wait until  element should be visible  test-add-invite

Weird jump via tabs to get the app awake
  Click element  test-conversation-tab
  Click element  test-application-tab

#Mikko invites Teppo
#  [Tags]  fail
#  Invite count is  0
#  Click element  test-add-invite
#  Input Text  invite-email  teppo@example.com
#  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
#  Click element  test-ask-for-planner
#  Wait until  Element should be visible  xpath=//a[@class='remove-invite']
#  Invite count is  1

#Mikko can't reinvite Teppo
#  [Tags]  fail
#  Click element  test-add-invite
#  Input Text  invite-email  teppo@example.com
#  Input Text  invite-text  Tervetuloa muokkaamaan taas hakemusta
#  Click element  test-ask-for-planner
  # TODO: print some error?
#  Invite count is  1

#Mikko can't invite himself
#  [Tags]  fail
#  Click element  test-add-invite
#  Input Text  invite-email  mikko@example.com
#  Input Text  invite-text  Voinko kutsua itseni?
#  Click element  test-ask-for-planner
  # TODO: print some error?
#  Invite count is  0

#Mikko removes Teppo's invite
#  [Tags]  fail
#  Click element  xpath=//a[@class='remove-invite']
#  Wait until  Element should not be visible  xpath=//a[@class='remove-invite']
#  Wait until  Invite count is  0

*** Keywords ***

Invite count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //li[@class='user-invite']  ${amount}
