import os.path
import zlib
import struct
from binascii import unhexlify, hexlify

def createFile(path, content):
    if not os.path.isfile(path):
        fp = open(path, 'wb')
        fp.write(content)
        fp.close()

TXT_TESTFILE_NAME = 'robotframework-testfile-06_attachments.txt'
PDF_TESTFILE_NAME = 'robotframework-testfile-06_attachments.pdf'
TXT_TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/06_attachments/variables.py'
TXT_TESTFILE_DESCRIPTION = "attachment-foo"
TXT_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TXT_TESTFILE_NAME)

PNG_TESTFILE_NAME = 'robotframework-testfile-06_attachments.png'
PNG_TESTFILE_CONTENT = unhexlify('89504e470d0a1a0a0000000d494844520000000100000001010300000025db56ca00000003504c5445ffffffa7c41bc80000000a4944415408d76360000000020001e221bc330000000049454e44ae426082')
PNG_TESTFILE_DESCRIPTION = "attachment-png-foo"
PNG_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), PNG_TESTFILE_NAME)

createFile(TXT_TESTFILE_PATH, TXT_TESTFILE_CONTENT)
createFile(PNG_TESTFILE_PATH, PNG_TESTFILE_CONTENT)
