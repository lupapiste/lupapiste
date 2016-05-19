*** Keywords ***

Approve application
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  xpath=//button[@data-test-id="approve-application-summaryTab"]
  Click enabled by test id  approve-application
  # Confirm warning about designers
  Wait until  Page should contain  Suunnittelijoiden tietoja hyväksymättä
  Wait until  Element should contain  jquery=#modal-dialog-content-component li:first  Pääsuunnittelija
  Element Should Contain  jquery=#modal-dialog-content-component li:last   Suunnittelija
  Confirm yes no dialog
  Wait until  Application state should be  sent
