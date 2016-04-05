*** Keywords ***

Approve application
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  xpath=//button[@data-test-id="approve-application-summaryTab"]
  Click enabled by test id  approve-application
  # Confirm warning about designers
  Wait Until  Page should contain  Suunnittelijoiden tietoja hyväksymättä
  Element text should be  jquery=#modal-dialog-content-component li:first  Pääsuunnittelija
  Element text should be  jquery=#modal-dialog-content-component li:last   Suunnittelija
  Confirm yes no dialog
  Wait until  Application state should be  sent
