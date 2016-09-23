#
# Usage:
#   pip install robotframework-debuglibrary
#   pybot repl.robot
#

*** Settings ***
Library  DebugLibrary
Documentation   Interactive Robot Framework shell with common resource keywords
Resource        common_resource.robot
Variables       tests/06_attachments/variables.py

*** Test Cases ***

REPL
    # Setting variables interactivelty doesn't work in debuglibrary master, see
    # https://github.com/xyb/robotframework-debuglibrary/issues/17 and
    # https://github.com/xyb/robotframework-debuglibrary/pull/19.
    ${secs} =  Get Time  epoch

    # To laod libraries, resources and variables in REPL, use keywords
    # Import Library
    # Import Resource
    # Import Variables

    Open browser to login page
    debug
