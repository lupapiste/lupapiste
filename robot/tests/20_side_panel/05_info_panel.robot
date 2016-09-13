*** Settings ***

Documentation   Info panel
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        info_resource.robot
Resource        ../10_authority_admin/authority_admin_resource.robot

*** Variables ***
${appname}     Linklater
${propertyid}  753-416-88-88
${rakval}      http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta
${sipoo}       http://sipoo.fi
${oopis}       http://oopis.if
${example}     http://www.example.org
${bar}         https://bar.baz
${twitter}     http://twitter.com

*** Test Cases ***

# ------------------------
# Pena
# ------------------------

Pena logs in and creates application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

Pena sees star and opens info panel
  Star is shown
  Open info panel

Pena sees two organization links no info links
  Check organization link  0  Sipoo  ${sipoo}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  No such test id  organization-link-2

Pena toggles side panel and stars are now gone
  Close info panel
  Star is not shown
  Open info panel
  Check organization link  0  Sipoo  ${sipoo}
  Check organization link  1  Rakennusvalvonta  ${rakval}

Pena cannot add links
  No such test id  add-info-link

Pena submits application
  Submit application
  [Teardown]  Logout  

# ------------------------
# Sonja
# ------------------------

Sonja logs in and also sees new organization links
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${sipoo}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  No such test id  organization-link-2

Sonja adds info link
  Add info link  0  Example  www.example.org
  Save link  0
  Check view link  0  Example  ${example}  false  true
  
Sonja start editing link but cancels
  Edit info link  0  Example  ${example}
  Fill link  0  Foo  bar.baz
  Cancel link  0
  Check view link  0  Example  ${example}  false  true

Sonja edits and saves
  Edit info link  0  Example  ${example}
  Fill link  0  Foo  ${bar}
  Save link  0
  Check view link  0  Foo  ${bar}  false  true

Sonja adds link but cancels
  Add info link  1  Hii  hii.hoo
  Cancel link  1
  No such test id  view-link-edit-1
  
Sonja deletes link
  Delete info link  0
  No such test id  view-link-edit-0
  [Teardown]  Logout


# ------------------------
# Sipoo
# ------------------------

Sipoo admin modifies organization links
  Sipoo logs in
  Go to page  backends
  Update link  Sipoo  ${oopis}
  Add link  Titityy  ${twitter}
  [Teardown]  Logout

# ------------------------
# Ronja
# ------------------------

Ronja logs in and sees many stars
  Ronja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  Check organization link  2  Titityy  ${twitter}  true
  No such test id  view-link-0

Ronja adds info link
  Add info link  0  Bar  ${bar}
  Save link  0
  [Teardown]  Logout

# ------------------------
# Pena
# ------------------------

Pena logs in and sees new organization and Ronja's links
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}  true
  Check just link  0  Bar  ${bar}  true

Pena visits conversation panel and comes back
  Click element  open-conversation-side-panel
  Wait until  Element should be visible  conversation-panel
  Click element  open-info-side-panel
  No info panel stars

Pena submits application
  Submit application
  [Teardown]  Logout

# ------------------------
# Sonja
# ------------------------

Sonja logs in and sees some new links
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  false
  Check organization link  2  Titityy  ${twitter}  true
  Check view link  0  Bar  ${bar}  true  true

Sonja edits Ronja's link
  Edit info link  0  Bar  ${bar}
  Fill link  0  Beijing  beijing.cn
  Save link  0
  Check view link  0  Beijing  http://beijing.cn  false  true
  [Teardown]  Logout

# ------------------------
# Ronja
# ------------------------

Ronja logs in and sees Sonja's edit
  Ronja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  # Organization links are now old
  Check organization link  0  Sipoo  ${oopis}
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}
  Check view link  0  Beijing  http://beijing.cn  true  true

Ronja adds link
  #[Tags]  fail  # New links are currently added on top
  Add info link  1  Shanghai  shanghai.cn
  Save link  1
  Check view link  1  Shanghai  http://shanghai.cn  false  true
  [Teardown]  Logout
  







