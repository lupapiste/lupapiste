*** Keywords ***

Create katselmus task
  [Arguments]  ${taskSchemaName}  ${taskName}  ${taskSubtype}=
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   ${taskSchemaName}
  Run Keyword If  $taskSubtype  Wait until  Element should be visible  choose-task-subtype
  Run Keyword If  $taskSubtype  Select From List By Value  choose-task-subtype   ${taskSubtype}
  Input text  create-task-name  ${taskName}
  Click enabled by test id  create-task-save
  Wait test id visible  review-done

Set date and check
  [Arguments]  ${button}  ${span}  ${date}
  Wait Until  Element should be visible  jquery=[data-test-id=${button}]
  Click by test id  ${button}
  Wait Until  Element should be visible  modal-datepicker-date
  Input text by test id  modal-datepicker-date  ${date}
  ## Datepickers stays open when using Selenium
  Execute JavaScript  $("#ui-datepicker-div").hide();
  Click enabled by test id  modal-datepicker-continue
  Wait Until  Element should not be visible  modal-datepicker-date
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element Text Should Be  jquery=[data-test-id=${span}]  ${date}
