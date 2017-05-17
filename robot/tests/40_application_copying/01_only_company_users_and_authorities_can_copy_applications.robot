*** Settings ***

Documentation   Mikko should not be able to copy applications but Sonja and Kaino should
Resource        ../../common_resource.robot
Resource        ../25_company/company_resource.robot
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Test Cases ***

Mikko creates and submits application
  Mikko logs in
  Create application the fast way  authority-can-copy  753-416-25-30  kerrostalo-rivitalo
  Submit application

Mikko does not see the 'Copy application' button
  Element should not be visible by test id  copy-application-button
  Logout

Sonja should not see applications at this stage
  Sonja logs in
  Open application  authority-can-copy  753-416-25-30
  Element should be visible by test id  copy-application-button
  Logout

Kaino creates and submits application
  Kaino logs in
  Create application the fast way  authority-can-copy  753-416-25-30  kerrostalo-rivitalo

Kaino can copy the application
  Element should be visible by test id  copy-application-button
  Logout

*** Keywords ***
