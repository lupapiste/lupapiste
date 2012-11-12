import os.path

TESTFILE_NAME = 'robotframework-testfile-05_application_editing.txt'
TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/05_application_editing/variables.py'
TESTFILE_DESCRIPTION = "attachment-foo"
TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TESTFILE_NAME)

if not os.path.isfile(TESTFILE_PATH):
    fp = open(TESTFILE_PATH, 'w')
    fp.write(TESTFILE_CONTENT)
    fp.close()
