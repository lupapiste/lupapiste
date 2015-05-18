import os.path

TXT_TESTFILE_NAME = 'robotframework-testfile-05_application_editing.txt'
TXT_TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/05_application_editing/variables.py'
TXT_TESTFILE_DESCRIPTION = "attachment-foo"
TXT_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TXT_TESTFILE_NAME)

JPG_TESTFILE_NAME = 'robotframework-testfile-05_application_editing.jpg'
JPG_TESTFILE_CONTENT = ''
JPG_TESTFILE_DESCRIPTION = "attachment-jpg-foo"
JPG_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), JPG_TESTFILE_NAME)

def createFile(path, content):
    if not os.path.isfile(path):
        fp = open(path, 'w')
        fp.write(content)
        fp.close()

createFile(TXT_TESTFILE_PATH, TXT_TESTFILE_CONTENT)
createFile(JPG_TESTFILE_PATH, JPG_TESTFILE_CONTENT)

