*** Settings ***

Documentation  Sidebar navigation for authority admin and admin admin.
Resource       ../../common_resource.robot


*** Test cases ***

# ------------------------
# Authority admin
# ------------------------

Sipoo logs in
  Sipoo logs in
  ${passed}=  Run Keyword and Return Status  Wait test id visible  sidebar-menu
  Set suite variable  ${menu}  ${passed}

Navigate to every page (excluding feature flagged)
  Navigate to  applications
  Navigate to  operations
  Navigate to  attachments
  Navigate to  backends
  Navigate to  areas
  Navigate to  reports
  Navigate to  assignments
  Navigate to  stamp-editor
  Navigate to  users
  [Teardown]  Logout

# ------------------------
# Admin admin
# ------------------------

Admin admin logs in
  SolitaAdmin logs in

Navigate to every admin page
  Navigate to  users
  Navigate to  organizations
  Navigate to  companies
  Navigate to  features
  Navigate to  actions
  Navigate to  single-sign-on-keys
  Navigate to  screenmessages
  Navigate to  notifications
  Navigate to  reports
  Navigate to  campaigns
  Navigate to  logs
  Navigate to  admin

Open Sipoo organization
  Navigate to  organizations
  Fill test id  organization-search-term  753-R
  Click by test id  edit-organization-753-R

Toolbar is not visible
  No such test id  sidebar-menu

No frontend errors
  There are no frontend errors

*** Keywords ***

Navigate to
  [Arguments]  ${page}
  Run keyword if  ${menu}  Click visible test id  sidebar-menu
  Test id visible  sidebar-${page}
  Click visible test id  sidebar-${page}
  Wait Until  Javascript?  pageutil.getPage() === "${page}"
  Test id disabled  sidebar-${page}
