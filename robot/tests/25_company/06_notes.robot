*** Settings ***

Documentation   Company notes
Suite Setup     Apply company-application fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

*** Test Cases ***

Company admin logs in
  Kaino logs in

Open application
  Open application  Latokuja 3  753-416-55-7

Company application notes should contain one tag
  Open side panel  company-notes
  Test id should contain  autocomplete-application-tags-component  Projekti1

Remove tag
  Click element  jquery=[data-test-id=autocomplete-application-tags-component] .tag
  Wait until  Element should not be visible  jquery=[data-test-id=autocomplete-application-tags-component] .tag

Close and re-open notes
  Close side panel  company-notes
  Wait until  Open side panel  company-notes
  Wait until  Element should be visible  jquery=[data-test-id=autocomplete-application-tags-component]
  Element should not be visible  jquery=[data-test-id=autocomplete-application-tags-component] .tag

Reload page and re-open notes
  Reload page
  Open side panel  company-notes
  Wait until  Element should be visible  jquery=[data-test-id=autocomplete-application-tags-component]
  Element should not be visible  jquery=[data-test-id=autocomplete-application-tags-component] .tag

Add tag again
  Select from autocomplete by test id  autocomplete-application-tags-component  Projekti1
  Wait until  Test id should contain  autocomplete-application-tags-component  Projekti1
  # Trigger autosave
  Focus test id  application-company-note
  Positive indicator icon should be visible

Add text note
  Input text by test id  application-company-note  Huomio, Obs, Achtung!

Close and re-open notes again
  Close side panel  company-notes
  Wait until  Open side panel  company-notes
  Wait until  Element should be visible  jquery=[data-test-id=autocomplete-application-tags-component]
  Element should be visible  jquery=[data-test-id=autocomplete-application-tags-component] .tag
  Test id should contain  autocomplete-application-tags-component  Projekti1
  Textarea value should be  jquery=[data-test-id=application-company-note]  Huomio, Obs, Achtung!

Reload page and re-open notes again
  Reload page
  Open side panel  company-notes
  Wait until  Element should be visible  jquery=[data-test-id=autocomplete-application-tags-component]
  Test id should contain  autocomplete-application-tags-component  Projekti1
  Textarea value should be  jquery=[data-test-id=application-company-note]  Huomio, Obs, Achtung!

No frontend errors
  There are no frontend errors
