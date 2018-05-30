*** Settings ***

Documentation   Admin adds REST API user
Suite Teardown  Logout
Resource        ../../common_resource.robot

# Run:
# local-chrome-standalone.bat tests\14_admin\05_add_rest_api_user.robot

*** Variables ***

${LANDING_PAGE_USERS} =              xpath=//section[@id='users']
${USER_NAME_INPUT_FIELD} =           xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
${LANDING_PAGE_ADD_REST_API_USER} =  //*[@id="add-rest-api-user-to-organization-dialog"]
${ADD_REST_API_USER_BUTTON} =        //*[@id="addAuthAdmin"]/button[2]
${ORGANIZATION_INPUT_FIELD} =        //*[@id="admin.rest-api.add.organizationCode"]
${ADD_ORGANIZATION_CODE} =           //*[@id="admin.rest-api.add.organizationCode"]
${FIRST_NAME_INPUT_FIELD} =          //*[@id="admin.rest-api.add.firstName"]
${ADD_FIRST_NAME} =                  //*[@id="admin.rest-api.add.firstName"]
${ADD_BUTTON} =                      //*[@id="add-rest-api-user-to-organization-dialog"]/div[2]/div[1]/form/button
${ORGANIZATION_CODE} =               837-R
${SHORT_TEXT} =                      short text t
${LONG_TEXT} =                       too long text
${SUCCESS} =                         Rajapintatunnus paperilupia varten luotu onnistuneesti

*** Keywords ***

Verify Users Page Loaded
  SolitaAdmin logs in
  Go to page     users
  Wait until     Element should be visible    ${LANDING_PAGE_USERS}

Verify New REST API User Can Be Added
  Wait until     Element should be visible    ${USER_NAME_INPUT_FIELD}
  Wait until     Click element                ${ADD_REST_API_USER_BUTTON}
  Wait until     Element should be visible    ${LANDING_PAGE_ADD_REST_API_USER}
  Wait until     Element should be visible    ${ORGANIZATION_INPUT_FIELD}
  Input text                                  ${ADD_ORGANIZATION_CODE}  ${ORGANIZATION_CODE}
  Wait until     Element should be visible    ${FIRST_NAME_INPUT_FIELD}

*** Test Cases ***

Solita admin logs in and goes to 'users' page
  Verify Users Page Loaded

Solita admin adds new REST API user
  Verify New REST API User Can Be Added

New REST API user name OK
  [Documentation]  User enters legal (< 21) REST API user name
  [Tags]  Test1
  Element should be disabled                  ${ADD_BUTTON}
  Input text                                  ${ADD_FIRST_NAME}  ${SHORT_TEXT}
  Element should be enabled                   ${ADD_BUTTON}

New REST API user name too long
  [Documentation]  User enters illegal REST API user name
  [Tags]  Test2
  Input text                                  ${ADD_FIRST_NAME}  ${LONG_TEXT}
  Element should be disabled                  ${ADD_BUTTON}

New REST API user name OK again
  [Documentation]  User corrects REST API user name to be correct length
  [Tags]  Test3
  Input text                                  ${ADD_FIRST_NAME}  ${SHORT_TEXT}
  Element should be enabled                   ${ADD_BUTTON}

New REST API user name stored to database
  [Documentation]  User presses ADD button and API user name is added to database
  [Tags]  Test4
  Click element                               ${ADD_BUTTON}
  Page should contain                         ${SUCCESS}