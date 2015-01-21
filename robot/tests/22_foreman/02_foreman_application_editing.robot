*** Settings ***

Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Keywords ***
Mikko creates and invites foreman to application
  Mikko logs in
  Mikko creates new application
  Mikko invites foreman to application
  [Teardown]  logout

*** Test Cases ***
Foreman sees his other foreman jobs
  # Application 1
  Mikko creates and invites foreman to application
  Foreman applies personal information to the application

  # Application 2
  Mikko creates and invites foreman to application
  Foreman applies personal information to the application

  Foreman can see the first construction info on the application
