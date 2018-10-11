*** Settings ***

Documentation   YA application gets tasks based on verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        task_resource.robot
Resource        keywords.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko prepares YA application and fills it to pass KRYSP validation later in test
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${secs}
  Set Suite Variable  ${propertyId}  753-416-18-1
  Set Suite Variable  ${appname-ya}  Taskitesti-YA-${secs}
  Create application the fast way  ${appname-ya}  ${propertyId}  ya-katulupa-vesi-ja-viemarityot
  Tab should be visible  info
  # Alkupvm + loppupvm
  Fill tyoaika fields
  # Osapuolet
  Invite erkki@example.com to application
  # LPK-2915, previous was Solita Oy, which didn't have parties invited to application
  Invite company to application  Esimerkki Oy
  Scroll and click input  section[data-doc-type=hakija-ya] input[value=yritys]
  Select from list by value  jquery=select[name=company-select]:first  esimerkki
  Wait until  Value should be  jquery=input[data-docgen-path='yritys.yritysnimi']:first  Esimerkki Oy
  Select from list by value  jquery=select[name=company-select]:last  esimerkki
  Wait until  Value should be  jquery=input[data-docgen-path='yritys.yritysnimi']:last  Esimerkki Oy
  Submit application
  [Teardown]  Logout

Katselmus task created in an YA application does not include any Rakennus information (LPK-719)
  Sonja logs in  False
  Open application  ${appname-ya}  ${propertyId}
  Open tab  verdict
  Fetch YA verdict
  Open tab  tasks
  Create katselmus task  task-katselmus-ya  uus muu ya-tarkastus
  Wait until  Element should not be visible  xpath=//div[@id='taskDocgen']//div[@data-repeating-id='rakennus']
  [Teardown]  Logout

Mikko sets started past date for YA application (LPK-1054)
  Mikko logs in
  Open application  ${appname-ya}  ${propertyId}
  Open tab  tasks
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  ${EMPTY}
  Set date and check  application-inform-construction-started-btn  construction-state-change-info-started  10.8.2012
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  Intonen Mikko
  [Teardown]  Logout

Sonja comes back and finalizes YA review
  Sonja logs in  False
  Open application  ${appname-ya}  ${propertyId}
  Open tab  tasks
  Open task  uus muu ya-tarkastus
  Edit YA katselmus  Aloituskatselmus  14.4.2016  Some Sonja  Description is mandatory for YA reviews.
  # TODO: Sending requires fully formed application
  #Finalize review

# TODO: Sonja sets ready past date for YA application (LPK-1054)
# This would require a well-formed application with all the required fields.

No errors so far
  There are no frontend errors
