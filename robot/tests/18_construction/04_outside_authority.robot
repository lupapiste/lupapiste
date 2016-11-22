*** Settings ***
Documentation  Outside authority should not be able to edit reviews.
Suite setup    Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        task_resource.robot

*** Variables ***

${appname}     Outsider
${propertyId}  753-416-88-88

*** Test Cases ***

Olli creates application in Sipoo
  Olli logs in
  Create application with state  ${appname}  ${propertyId}  pientalo  submitted
  [Teardown]  Logout

Sonja logs in and fetches verdict
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict
  [Teardown]  Logout

Olli logs in and cannot edit reviews
  Olli logs in
  Open application  ${appname}  ${propertyId}
  Open tab  tasks
  Open task  Aloituskokous
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Review disabled for applicant
  [Teardown]  Logout
