*** Settings ***

Documentation   Digitized applications tab visibility
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Rakennustarkastaja logs in and creates an application
  Jarvenpaa authority logs in
  Create application the fast way  Digital Drive  186-13-1352-5  pientalo
  Go to page  applications

Digitized applications tab is visible
  Wait test id visible  search-tab-archivingProjects
  [Teardown]  Logout

Admin disables digitizer tools in Järvenpää
  SolitaAdmin logs in
  Go to page  organizations
  Fill test id  organization-search-term  Järvenpää
  Click by test id  edit-organization-186-R
  Wait until element is visible  digitizerToolsEnabled
  # We scroll past digitizerToolsEnabled to make sure it is fully visible
  Scroll to  input#automaticEmailsEnabled
  Unselect checkbox  digitizerToolsEnabled
  Positive indicator should be visible
  [Teardown]  Logout

Rakennustarkastaja logs in but tab is no longer visible
  Jarvenpaa authority logs in
  Wait test id visible  search-tab-readyForArchival
  No such test id  search-tab-archivingProjects
  [Teardown]  Logout
