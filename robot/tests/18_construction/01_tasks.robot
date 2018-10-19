*** Settings ***

Documentation   Application gets tasks based on verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        task_resource.robot
Resource        keywords.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko prepares the application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${secs}
  Set Suite Variable  ${appname}  Taskitesti-${secs}
  Set Suite Variable  ${propertyId}  753-416-18-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Submit application
  [Teardown]  Logout

Sonja fetches verdict
  Sonja logs in  False
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict

Rakentaminen tab opens
  Open tab  tasks

Rakentaminen tab contains 3 foreman tasks
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  3

Rakentaminen tab contains 3 review tasks (katselmus)
  Wait until  Xpath Should Match X Times  //div[@id='application-tasks-tab']//div[contains(@class, 'review-tasks')]//tbody/tr  3

Rakentaminen tab contains 3 requirements (lupamaarays)
  Wait until  Xpath Should Match X Times  //div[@id='application-tasks-tab']//table[@data-test-id='lupamaaraykset']//tbody/tr  3

Katselmukset
  Wait Until  Page should contain  Kokoukset, katselmukset ja tarkastukset
  Task count is  task-katselmus  3

Tyonjohtajat
  Wait until  Page should contain  Vaaditut työnjohtajat
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  3

Muut lupamaaraykset
  Wait until  Page should contain  Muut lupamääräykset
  Task count is  task-lupamaarays  3
  Task state count is  task-lupamaarays  requires_user_action  3
  [Teardown]  Logout

Luukas on Rakentaminen tab
  Luukas logs in
  Open application  ${appname}  ${propertyId}
  Open tab  tasks

Luukas cannot add attachment to Aloituskokous
  Open review  0
  No such test id  upload-button-label
  Return from review

Luukas cannot add attachment to Valaistussuunnitelma
  Open task  Valaistussuunnitelma
  No such test id  upload-button-label
  Return from review
  [Teardown]  Logout

Sonja logs in again
  Sonja logs in  False
  Open application  ${appname}  ${propertyId}
  Open tab  tasks

Valaistussuunnitelma requires action
  Open task  Valaistussuunnitelma
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_user_action"]  1

Reject Valaistussuunnitelma
  Click enabled by test id  reject-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_user_action"]  1

Approve Valaistussuunnitelma
  Sleep  0.5s
  Click enabled by test id  approve-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="ok"]  1
  Return from review

Lupaamaarays states updated
  Task state count is  task-lupamaarays  requires_user_action  2
  Task state count is  task-lupamaarays  ok  1

Add attachment to Aloituskokous
  Open task  Aloituskokous
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Test id disabled  review-done
  Review active
  Review checkboxes disabled
  Upload verdict or task attachment  ${PDF_TESTFILE_PATH}  Aloituskokouksen pöytäkirja  Pöytäkirja  Yleisesti hankkeeseen

Aloituskokous form is still editable (LPK-494)
  Page Should Contain Element  xpath=//section[@id="task"]//input
  Test id enabled  katselmus.pitoPvm
  Edit R katselmus  osittainen  1.5.2016  Sonja Sibbo  Hello world!

Return to listing
  Return from review
  Review row check  0  Aloituskokous  1.5.2016  Sonja Sibbo  Osittainen  Kyllä
  Review row has attachments  0
  Review row note  0  Hello world!

Invalid date prevents review done
  Open review  0
  Wait until  Test id enabled  review-done
  Edit review date  1.2.34
  Wait until  Test id disabled  review-done
  Edit review date  1.5.2016
  Wait for jQuery
  Wait until  Test id enabled  review-done

Partial review generates new review
  Finalize review
  Review row check  1  Aloituskokous  ${EMPTY}  ${EMPTY}  ${EMPTY}  ${EMPTY}

The review task cannot be deleted after the review
  Open review  0
  Wait until  List Selection Should Be  xpath=//select[@data-test-id="katselmus.tila"]  Osittainen
  No such test id  delete-task
  Return from review

The same thing happens if the new review is also partially reviewed
  Open review  1
  Edit R katselmus  osittainen  20.5.2016  Sonja Igen  ${EMPTY}
  Finalize review
  Review row check  1  Aloituskokous  20.5.2016  Sonja Igen  Osittainen  ${EMPTY}
  No such test id  show-review-note-1
  Review row check  2  Aloituskokous  ${EMPTY}  ${EMPTY}  ${EMPTY}  ${EMPTY}

Making the latest Aloituskokous final will also finalize the first but not the second
  Open review  2
  Edit R katselmus  lopullinen  22.5.2016  Ronja Rules  Done!
  Finalize review
  Review row check  0  Aloituskokous  1.5.2016  Sonja Sibbo  Lopullinen  Kyllä
  Review row check  1  Aloituskokous  20.5.2016  Sonja Igen  Osittainen  ${EMPTY}
  Review row check  2  Aloituskokous  22.5.2016  Ronja Rules  Lopullinen  ${EMPTY}

Add attachment to loppukatselmus
  Open task  loppukatselmus
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Test id disabled  review-done
  Review active
  Review checkboxes enabled
  Upload verdict or task attachment  ${PDF_TESTFILE_PATH}  Katselmuksen pöytäkirja  Pöytäkirja  Yleisesti hankkeeseen
  Return from review

Delete loppukatselmus
  Open tab  tasks
  Wait until  Element should be visible  xpath=//div[@id="application-tasks-tab"]//table[contains(@class, 'tasks')]//tbody/tr
  Open task  loppukatselmus
  Review checkboxes enabled
  Scroll and click test id  delete-task
  Confirm  dynamic-yes-no-confirm-dialog

The attachment is gone too
  Open tab  attachments
  Javascript?  $("tr#attachment-row-katselmuksen_poytakirja").length === 0

Listing contains one less task
  Open tab  tasks
  Tab should be visible  tasks
  Task count is  task-katselmus  4

Add katselmus
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-katselmus
  Wait until  Element should be visible  choose-task-subtype
  Select From List By Value  choose-task-subtype   muu tarkastus
  Input text  create-task-name  uus muu tarkastus
  Click enabled by test id  create-task-save
  Wait Until  Element should be visible  taskAttachments

Katselmuksenlaji is set and disabled
  Element should be disabled  xpath=//section[@id="task"]//select[@data-test-id='katselmuksenLaji']
  List Selection Should Be  xpath=//section[@id="task"]//select[@data-test-id='katselmuksenLaji']  muu tarkastus
  Click by test id  back-to-application-from-task

New katselmus is listed
  Tab should be visible  tasks
  Task count is  task-katselmus  5

Sonja adds an end review
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-katselmus
  Wait until  Element should be visible  choose-task-subtype
  Select From List By Value  choose-task-subtype   osittainen loppukatselmus
  Input text  create-task-name  End review
  Click enabled by test id  create-task-save
  Wait Until  Element should be visible  taskAttachments
  Review checkboxes enabled
  Return from review

Verify post-verdict attachments - Aloituskokous
  Wait until  Element should be visible  jquery=a[data-test-id=application-open-attachments-tab]
  Open tab  attachments
  Wait Until  Element should be visible  jquery=div#application-attachments-tab a:contains('${PDF_TESTFILE_NAME}')
  [Teardown]  Logout

Mikko is unable to edit Kayttoonottotarkastus (LPK-494)
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  tasks
  Open task  Käyttöönottotarkastus
  Review checkboxes disabled
  Page Should Contain Element  xpath=//section[@id="task"]//input

  # All but one input (add attachments) should be disabled
  ${inputCount} =  Get Matching Xpath Count  //section[@id="task"]//input
  ${inputCountInt} =  Convert to Integer  ${inputCount}
  Xpath Should Match X Times  //section[@id="task"]//input[@disabled]  ${inputCountInt-1}

  Page Should Contain Element  xpath=//section[@id="task"]//select
  ${selectCount} =  Get Matching Xpath Count  //section[@id="task"]//select
  Xpath Should Match X Times  //section[@id="task"]//select[@disabled]  ${selectCount}
  Review disabled for applicant

Mikko can add attachments though
  Scroll to top
  Wait test id visible  upload-button-label
  Return from review
  [Teardown]  Logout

Deleting R verdict does not delete its done reviews
  Sonja logs in  False
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Delete verdict  1
  Wait until  Open tab  tasks
  Javascript?  $("[data-test-type=task-katselmus]").length === 5
  Review row check  0  Aloituskokous  1.5.2016  Sonja Sibbo  Lopullinen  Kyllä
  Review row check  1  Aloituskokous  20.5.2016  Sonja Igen  Osittainen  ${EMPTY}
  Review row check  2  Aloituskokous  22.5.2016  Ronja Rules  Lopullinen  ${EMPTY}

Attachments have been updated
  Open tab  attachments
  Javascript?  $("tr[data-test-type='katselmukset_ja_tarkastukset.aloituskokouksen_poytakirja']").length === 4
  [Teardown]  Logout


# TODO: Sonja sets ready past date for YA application (LPK-1054)
# This would require a well-formed application with all the required fields.

No errors so far
  There are no frontend errors
