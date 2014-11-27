import os.path

TXT_TESTFILE_NAME = 'robotframework-testfile-21_stamping.txt'
TXT_TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/21_application_editing/variables.py'
TXT_TESTFILE_DESCRIPTION = "attachment-foo"
TXT_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TXT_TESTFILE_NAME)

JPG_TESTFILE_NAME = 'robotframework-testfile-05_application_editing.jpg'
JPG_TESTFILE_CONTENT = '12321'
JPG_TESTFILE_DESCRIPTION = "attachment-jpg-foo"
JPG_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), JPG_TESTFILE_NAME)

PDF_TESTFILE_NAME1 = 'robotframework-testfile-21_stamping_01.pdf'
PDF_TESTFILE_CONTENT = '1234'
PDF_TESTFILE_DESCRIPTION = "attachment-pdf-foo"
PDF_TESTFILE_PATH1 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME1)

PDF_TESTFILE_NAME2 = 'robotframework-testfile-21_stamping_02.pdf'
PDF_TESTFILE_PATH2 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME2)
PDF_TESTFILE_NAME3 = 'robotframework-testfile-21_stamping_03.pdf'
PDF_TESTFILE_PATH3 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME3)
PDF_TESTFILE_NAME4 = 'robotframework-testfile-21_stamping_04.pdf'
PDF_TESTFILE_PATH4 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME4)

def createFile(path, content):
    if not os.path.isfile(path):
        fp = open(path, 'w')
        fp.write(content)
        fp.close()

createFile(TXT_TESTFILE_PATH, TXT_TESTFILE_CONTENT)
createFile(JPG_TESTFILE_PATH, JPG_TESTFILE_CONTENT)
createFile(PDF_TESTFILE_PATH1, PDF_TESTFILE_CONTENT)
createFile(PDF_TESTFILE_PATH2, PDF_TESTFILE_CONTENT)
createFile(PDF_TESTFILE_PATH3, PDF_TESTFILE_CONTENT)
createFile(PDF_TESTFILE_PATH4, PDF_TESTFILE_CONTENT)
