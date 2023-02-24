*** Settings ***

Documentation   Not all applications have tasks tab after verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Hello Mikko
  Mikko logs in
  ${time} =  Get Time  epoch
  Set Suite Variable  ${secs}  ${time}

KT application does not have tasks tab after verdict
  Create application with state  Taskstab1-${secs}  753-416-18-2  kiinteistonmuodostus  verdictGiven
  Tab should be visible  applicationSummary
  Element should not be visible by test id  application-open-tasks-tab

YM application does not have tasks tab after verdict
  Create application with state  Taskstab2-${secs}  753-416-18-2  lannan-varastointi  verdictGiven
  Tab should be visible  applicationSummary
  Element should not be visible by test id  application-open-tasks-tab
