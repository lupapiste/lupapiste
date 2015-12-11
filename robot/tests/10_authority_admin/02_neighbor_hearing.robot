*** Settings ***

Documentation   Email is send to municipality about hearing neighbors
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo admin sets email address for neighbor hearing notification
  ${save_indicator} =  Set Variable  xpath=//section[@id="applications"]//span[contains(@class, "save-indicator")]
  Sipoo logs in
  Go to page  applications
  Element should not be visible  ${save_indicator}
  Input text by test id  neighborOrderEmails  kirjaamo@sipoo.example.com
  Wait Until  Element should be visible  ${save_indicator}
  Logout
