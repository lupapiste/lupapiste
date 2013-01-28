*** Settings ***

Documentation  Sonja can assign application to herself
Suite setup    Sonja logs in
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Application is not assigned
  Wait until  Click element  xpath=//section[@id='applications']//tr[contains(@class,'application')]//td[text()='Latokuja 1, Sipoo']
  Application is not assigned

Sonja assign application to herself
  Select From List  xpath=//select[@data-test-id='application-assigneed-authority']  Sonja Sibbo
  
Assignee has changed
  Wait Until  Application is assigned to  Sonja Sibbo
  
*** Keywords ***

Application is assigned to
  [Arguments]  ${to}
  Wait until  Element should be visible  xpath=//select[@data-test-id='application-assigneed-authority']
  ${assignee} =  Get selected list label  xpath=//select[@data-test-id='application-assigneed-authority']
  Should be equal  ${assignee}  ${to}

Application is not assigned
  Application is assigned to  Valitse..
  
