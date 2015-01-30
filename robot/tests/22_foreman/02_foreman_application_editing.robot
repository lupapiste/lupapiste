*** Settings ***

Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Keywords ***
Mikko creates an application and invites foreman
  Mikko creates new application
  Mikko invites foreman to application

*** Test Cases ***
Foreman sees his other foreman jobs
  Mikko logs in
  Mikko creates an application and invites foreman
  Mikko creates an application and invites foreman

  Foreman logs in
  Foreman applies personal information to the foreman application  0
  Foreman applies personal information to the foreman application  1
  Foreman can see the first related construction info on the second foreman application
