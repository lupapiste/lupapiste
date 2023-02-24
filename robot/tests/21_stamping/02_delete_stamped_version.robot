*** Settings ***

Documentation   Authority can delete stamped version
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        stamping_resource.robot
Resource        ../39_pate/pate_resource.robot
Resource        ../06_attachments/attachment_resource.robot

*** Variables ***
${appname}     Stampede!
${propertyId}  753-416-25-30

*** Test Case ***

Mikko logs in, creates and submits an application
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  asuinrakennus
  Submit application
  [Teardown]  Logout

Sonja logs in and fetches verdict
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict

Sonja stamps verdict attachments
  Open tab  attachments
  Click by test id  stamp-attachments
  Click group selection link  HAKEMUKSEN LIITTEET  select
  Scroll and click test id  start-stamping
  Test id text is  stamp-status-text  Leimaus valmis
  Click by test id  cancel-stamping

Open the first verdict attachment
  Open attachment details  paatoksenteko.paatos
  Click button  show-attachment-versions

Sonja cannot delete the first version
  Cannot delete version  0.1

Sonja could delete the second version
  Can delete version  0.2
  [Teardown]  Logout

Mikko is back and opens the first attachment
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Open attachment details  paatoksenteko.paatos

Mikko cannot delete either version
  Click button  show-attachment-versions
  Cannot delete version  0.1
  Cannot delete version  0.2
  [Teardown]  Logout
