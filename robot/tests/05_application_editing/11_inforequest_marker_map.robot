*** Settings ***

Documentation   Behaving of the inforequest marker map
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

Mikko as applicant does not see the inforequest marker map
  Mikko logs in
  #User role should be  applicant
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-first}  ir-first-${secs}
  Set Suite Variable  ${propertyId-first}  433-405-3-427
  #Set Suite Variable  ${address-first}  Holvitie 4
  Create inforequest the fast way  ${inforequest-first}  360383.382  6734086.21  ${propertyId-first}  kerrostalo-rivitalo  Jiihaa-first
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  Element should not be visible  //div[@id='inforequest-marker-map']
  Element should not be visible  //div[@id='inforequest-marker-map-contents']
  Logout

Arto (authority) sees Mikko's new inforequest as a marker on map
  Arto logs in
  Hide nav-bar
  Open inforequest  ${inforequest-first}  ${propertyId-first}
  Wait until  Element should be visible  //div[@id='inforequest-marker-map']
  Total marker count is  1
  Marker count by type is  1  0  0  0

Arto clicks on the marker and the marker contents window is opened
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should be visible  //div[@id='inforequest-marker-map-contents']
  Total Inforequest info card count on map is  1
  Total inforequest comment count on the info cards is  1

The opened marker contents window has correct info about the inforequest
  ${first-app-id} =  Get Text  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']
  Set Suite Variable  ${first-app-id}
  Verify info card by app id  ${first-app-id}  ${inforequest-first} - Mikko Intonen  Asuinkerrostalon tai rivitalon rakentaminen  Jiihaa-first

Arto clicks on the marker again and the marker contents window is closed
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should not be visible  //div[@id='inforequest-marker-map-contents']

Arto adds comment and it is visible in the marker contents window
  Wait until  Page should contain element  //section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Input comment and mark answered  Oletko miettinyt askeesia?
  Wait until   Element Text Should Be  test-inforequest-state  Vastattu

Arto's comment is visible in the marker contents window
  Wait until  Element should be visible  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should be visible  //div[@id='inforequest-marker-map-contents']
  Total Inforequest info card count on map is  1
  Total inforequest comment count on the info cards is  2
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should not be visible  //div[@id='inforequest-marker-map-contents']

Arto creates three new inforequests
  # inforequest with same location
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-same-loc}  ir-same-loc-${secs}
  Set Suite Variable  ${propertyId-same-loc}  433-405-3-427
  #Set Suite Variable  ${address-same-loc}  Holvitie 4
  Create inforequest the fast way  ${inforequest-same-loc}  360383.382  6734086.21  ${propertyId-same-loc}  kerrostalo-rivitalo  Jiihaa-loc

  # inforequest with same operation
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-same-op}  ir-same-op-${secs}
  Set Suite Variable  ${propertyId-same-op}  433-405-57-8
  #Set Suite Variable  ${address-same-op}  Kauppatie 6
  Create inforequest the fast way  ${inforequest-same-op}  360365.358  6734200.355  ${propertyId-same-op}  kerrostalo-rivitalo  Jiihaa-op

  # other inforequest
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-other}  ir-other-${secs}
  Set Suite Variable  ${propertyId-other}  433-405-78-0
  #Set Suite Variable  ${address-other}  Kauppatie 4
  Create inforequest the fast way  ${inforequest-other}  360414.396  6734197.77  ${propertyId-other}  vapaa-ajan-asuinrakennus  Jiihaa-other

There are correct amount of correct type of markers on the marker map
  Total marker count is  3
  Marker count by type is  1  0  1  1

Open the marker contents window and follow the link displayed in an info card
  Hide nav-bar
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/lp-static/img/map-marker-group.png']
  Wait until  Element should be visible  //div[@id='inforequest-marker-map-contents']
  Click element  xpath=//div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${first-app-id}']/a[@data-test-id='inforequest-link']
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']  ${first-app-id}
  Logout

#Setting maps disabled again after the tests
#  Set integration proxy off


*** Keywords ***

Total marker count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]  ${amount}

Marker count by type is
  [Arguments]  ${current-location-amount}  ${same-operation-amount}  ${others-amount}  ${cluster-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/lp-static/img/map-marker-big.png']  ${current-location-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/lp-static/img/map-marker-green.png']  ${same-operation-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/lp-static/img/map-marker-orange.png']  ${others-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/lp-static/img/map-marker-group.png']  ${cluster-amount}

Total Inforequest info card count on map is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map-contents']//div[contains(@class, 'inforequest-card')]  ${amount}

Verify info card by app id
  [Arguments]  ${app-id}  ${title}  ${operation}  ${comment}
  Element text should be  //div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${app-id}']/h2[@data-test-id='inforequest-title']  ${title}
  Element text should be  //div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${app-id}']/h3[@data-test-id='inforequest-operation']  ${operation}
  Element text should be  //div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${app-id}']/div[contains(@class, 'inforequest-comment')]/blockquote  ${comment}
  Element should not be visible  //div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${app-id}']/a[@data-test-id='inforequest-link']

Comment count on the info card of current inforequest is
  [Arguments]  ${amount}
  ${app-id} =  Get Text  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']
  Xpath Should Match X Times  //div[@id='inforequest-marker-map-contents']//div[@data-test-id='inforequest-card-${app-id}']//div[contains(@class, 'inforequest-comment')]  ${amount}

Total inforequest comment count on the info cards is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map-contents']//div[contains(@class, 'inforequest-card')]//div[contains(@class, 'inforequest-comment')]  ${amount}


