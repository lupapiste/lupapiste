*** Settings ***

Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        keywords.robot
Suite Setup     Initialize

*** Keywords ***
Mikko invites foreman and goes back to application
  Mikko invites foreman to application
  Mikko goes back to project application

*** Test Cases ***
Foreman sees his foreman application history
  Mikko logs in
  Mikko creates new application

  Repeat Keyword  5  Mikko invites foreman and goes back to application

  Foreman logs in
  Foreman sets role and difficulty to foreman application  0  KVV-työnjohtaja  B
  Foreman sets role and difficulty to foreman application  1  KVV-työnjohtaja  B
  Foreman sets role and difficulty to foreman application  2  KVV-työnjohtaja  A
  Foreman sets role and difficulty to foreman application  3  IV-työnjohtaja   B

  Foreman opens application  4
  Foreman accepts invitation and fills info

  Wait until  Foreman history should have text X times  Sipoo  3
  Foreman history should have text X times  Tavanomainen  2
