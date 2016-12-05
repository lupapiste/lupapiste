*** Settings ***

Documentation   Bulletin page
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./julkipano_common.robot

*** Test Cases ***

Init Bulletins
  Create bulletins  2
  Go to bulletins page

Bulletin page should have docgen data
  Open bulletin by index  1
  Open bulletin tab  info

  Element should be visible  bulletinDocgen
  ${sectionCount}=  Get Matching Xpath Count  //div[@id='bulletin-component']//div[@id='bulletinDocgen']/section
  Should Be True  ${sectionCount} > 0

State is visible
  Bulletin state is  proclaimed

Map is visible
  Element should be visible  //div[@id='bulletin-component']//div[@id='bulletin-map']/div

Action buttons are visible
  Element should be visible  //div[@id='bulletin-component']//div[@data-test-id='bulletin-actions']/button[@data-test-id='comment-bulletin']

Tabs are visible
  Element should be visible  bulletin-tabs
  Element should be visible  xpath=//ul[@id='bulletin-tabs']/li/a[@data-test-id='bulletin-open-info-tab']
  Element should be visible  xpath=//ul[@id='bulletin-tabs']/li/a[@data-test-id='bulletin-open-attachments-tab']

