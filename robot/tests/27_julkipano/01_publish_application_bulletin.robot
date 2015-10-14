*** Settings ***

Documentation   Admin edits authority admin users
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja publishes an application as a bulletin
  Sonja logs in
  Create application with state  create-app  753-416-25-22  kerrostalo-rivitalo  sent
  Click by test id  publish-bulletin
