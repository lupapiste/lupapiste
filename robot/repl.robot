#
# Usage:
#   pip install robotframework-debuglibrary
#   pybot repl.robot
#
# Add other besources below here as needed,
# they can't be loaded from the interactive shell!

*** Settings ***
Library  DebugLibrary
Documentation   Interactive Robot Frameworks shell with common-resource
Resource        common_resource.robot
Variables       tests/06_attachments/variables.py

*** Test Cases ***

REPL
    Open browser to login page
    # Setting variables interactivelty doesn't work, see
    # https://github.com/xyb/robotframework-debuglibrary/issues/17
    ${secs} =  Get Time  epoch
    debug
