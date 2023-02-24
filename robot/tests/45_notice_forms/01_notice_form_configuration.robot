*** Settings ***

Documentation   Notice forms configuration for organization
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo logs in and opens Reviews view
  Sipoo logs in
  Go to page  reviews

Forms are not enabled
  Scroll to bottom
  Toggle not selected  notice-form-construction-toggle
  Toggle not selected  notice-form-terrain-toggle
  Toggle not selected  notice-form-location-toggle

Form configuration details are not visible
  No such test id  notice-form-construction-details
  No such test id  notice-form-terrain-details
  No such test id  notice-form-location-details

# -----------------------
# Construction
# -----------------------

Enable construction form
  Toggle toggle  notice-form-construction-toggle
  Wait test id visible  notice-form-construction-details

Fill help texts for construction
  Fill test id  notice-form-construction-fi  Morjens!
  Fill test id  notice-form-construction-sv  Hejdå!
  Fill test id  notice-form-construction-en  Howdy!

Enable construction integration
  Toggle toggle  notice-form-construction-integration

# -----------------------
# Terrain
# -----------------------

Enable terrain form
  Toggle toggle  notice-form-terrain-toggle
  Wait test id visible  notice-form-terrain-details

Fill help text for terrain
  Fill test id  notice-form-terrain-fi  Maasto

Terrain form does not support integration
  No such test id  notice-form-terrain-integration

Hide terrain form
  Toggle toggle  notice-form-terrain-toggle

Reload page check
  Reload page
  Toggle visible  notice-form-construction-toggle

Construction details are OK
  Toggle selected  notice-form-construction-toggle
  Test id input is  notice-form-construction-fi  Morjens!
  Test id input is  notice-form-construction-sv  Hejdå!
  Test id input is  notice-form-construction-en  Howdy!
  Toggle selected  notice-form-construction-integration

Terrain details are OK
  Toggle not selected  notice-form-terrain-toggle
  No such test id  notice-form-terrain-details
  Toggle toggle  notice-form-terrain-toggle
  Test id input is  notice-form-terrain-fi  Maasto
  Test id input is  notice-form-terrain-sv  ${EMPTY}
  Test id input is  notice-form-terrain-en  ${EMPTY}
