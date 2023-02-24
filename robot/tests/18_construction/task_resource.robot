*** Settings ***

Documentation   Task utils
Resource        ../../common_resource.robot

*** Keywords ***

Open task
  [Arguments]  ${name}
  Wait until  Element should be visible  xpath=//div[@id='application-tasks-tab']//table//td/a[text()='${name}']
  Scroll to  div#application-tasks-tab table.tasks
  Sleep  0.5s
  Wait until  Click Element  //div[@id='application-tasks-tab']//table//td/a[text()='${name}']
  Sleep  0.5s
  Wait Until  Element should be visible  xpath=//section[@id="task"]/h1/span[contains(., "${name}")]
  Wait Until  Element should be visible  taskAttachments

Edit review date
  [Arguments]  ${date}
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Wait for jQuery
  Input text with jQuery  input[data-test-id="katselmus.pitoPvm"]  ${date}

Edit katselmus
  [Arguments]  ${select}  ${item}  ${date}  ${name}  ${notes}
  Test id disabled  review-done
  Select From List by test id and value  ${select}  ${item}
  Edit review date  ${date}
  Wait for jQuery
  Input text with jQuery  input[data-test-id="katselmus.pitaja"]  ${name}  ${true}
  Sleep  0.5s
  Wait for jQuery
  Input text with jQuery  textarea[data-test-id="katselmus.huomautukset.kuvaus"]  ${notes}  ${true}
  Sleep  0.5s
  Wait for jQuery
  Wait until  Test id enabled  review-done

Edit R katselmus
  [Arguments]  ${state}  ${date}  ${name}  ${notes}
  Edit katselmus  katselmus.tila  ${state}  ${date}  ${name}  ${notes}

Edit YA katselmus
  [Arguments]  ${type}  ${date}  ${name}  ${notes}
  Edit katselmus  katselmuksenLaji  ${type}  ${date}  ${name}  ${notes}

Open review
  [Arguments]  ${index}
  Wait until  Element should be visible  jquery=div.review-tasks
  Scroll to  tr[data-test-index=${index}]
  Wait until  Click element  jquery=tr[data-test-index=${index}] td[data-test-column=requirement] a
  Sleep  0.5s
  Wait test id visible  back-to-application-from-task

Return from review
  Scroll to top
  Click by test id  back-to-application-from-task
  Tab should be visible  tasks
  Scroll to test id  reviews-table-end

Review row check
  [Arguments]  ${index}  ${requirement}  ${date}  ${name}  ${state}  ${condition}
  Wait Until  Element should contain  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td[data-test-column=requirement]  ${requirement}
  Wait Until  Element should contain  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td[data-test-column=date]  ${date}
  Wait Until  Element should contain  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td[data-test-column=name]  ${name}
  Wait Until  Element should contain  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td[data-test-column=state]  ${state}
  Wait Until  Element should contain  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td[data-test-column=condition]  ${condition}

Review row has attachments
  [Arguments]  ${index}
  Element should be visible  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] i.lupicon-paperclip

Review row is done
  [Arguments]  ${index}
  Wait until  Element should be visible  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] i.lupicon-circle-check

Review row is not done
  [Arguments]  ${index}
  Element should be visible  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] i.lupicon-circle-attention

Review row does not have attachments
  [Arguments]  ${index}
  Element should not be visible  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] i.lupicon-paperclip

Review row does not have icons
  [Arguments]  ${index}
  Element should not be visible  jquery=tr[data-test-type='task-katselmus'][data-test-index=${index}] td.reviews-table--icons i

Review row note
  [Arguments]  ${index}  ${note}
  Click by test id  show-review-note-${index}
  Wait until  Element should contain  jquery=[data-test-id=review-note-${index}]  ${note}

Test id readonly
  [Arguments]  ${id}
  Element should be visible  jquery=[data-test-id="${id}"][readonly=true]

Review checkboxes enabled
  jQuery should match X times  table.review-buildings-table tbody input:disabled  0
  jQuery should match X times  input[data-test-id='katselmus.tiedoksianto']:disabled  0

Review checkboxes disabled
  jQuery should match X times  table.review-buildings-table tbody input:enabled  0
  jQuery should match X times  input[data-test-id='katselmus.tiedoksianto']:enabled  0

Review frozen
  Wait until  Xpath should match X times  //table[contains(@class, 'review-buildings')]/tbody//tr  3
  Test id disabled  review-done
  Element should not be visible  jquery=table.review-buildings-table tbody select:enabled
  Review checkboxes disabled
  Element should not be visible  jquery=table.review-buildings-table tbody input:enabled
  Element should be disabled  jquery=[data-test-id="katselmus.tila"]
  Test id disabled  katselmus.pitoPvm
  Test id disabled  katselmus.pitaja
  Test id disabled  katselmus.lasnaolijat
  Test id disabled  katselmus.poikkeamat
  Test id disabled  katselmus.huomautukset.kuvaus
  Test id disabled  katselmus.huomautukset.maaraAika
  Test id disabled  katselmus.huomautukset.toteaja
  Test id disabled  katselmus.huomautukset.toteamisHetki
  No such test id  add-targetted-attachment

Review active
  Xpath should match X times  //table[contains(@class, 'review-buildings')]/tbody//tr  3
  Element should not be visible  jquery=table.review-buildings-table tbody select:disabled
  Test id enabled  katselmus.tila
  Test id enabled  katselmus.pitoPvm
  Test id enabled  katselmus.pitaja
  Test id enabled  katselmus.lasnaolijat
  Test id enabled  katselmus.poikkeamat
  Test id enabled  katselmus.huomautukset.kuvaus
  Test id enabled  katselmus.huomautukset.maaraAika
  Test id enabled  katselmus.huomautukset.toteaja
  Test id enabled  katselmus.huomautukset.toteamisHetki
  Wait test id visible  upload-button-label

Review disabled for applicant
  Wait until  Xpath should match X times  //table[contains(@class, 'review-buildings')]/tbody//tr  3
  Test id disabled  review-done
  Element should not be visible  jquery=table.review-buildings-table tbody select:enabled
  Review checkboxes disabled
  Element should not be visible  jquery=table.review-buildings-table tbody input:enabled
  Element should be disabled  jquery=[data-test-id="katselmus.tila"]
  Test id disabled  katselmus.pitoPvm
  Test id disabled  katselmus.pitaja
  Test id disabled  katselmus.lasnaolijat
  Test id disabled  katselmus.poikkeamat
  Test id disabled  katselmus.huomautukset.kuvaus
  Test id disabled  katselmus.huomautukset.maaraAika
  Test id disabled  katselmus.huomautukset.toteaja
  Test id disabled  katselmus.huomautukset.toteamisHetki
  Wait test id visible  upload-button-label
  No such test id  review-done
  No such test id  delete-task


Finalize review
  Click by test id  review-done
  Confirm yes no dialog
  Confirm  dynamic-ok-confirm-dialog
  Review frozen
  Return from review

Has review attachment
  [Arguments]  ${type}  ${regex}  ${index}=0
  Javascript?  $("tr[data-test-type='${type}'] td[data-test-id=file-info] a").slice( ${index}).text().match( ${regex})
