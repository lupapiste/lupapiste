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
TXT_TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/06_attachments/variables.py'
TXT_TESTFILE_DESCRIPTION = "attachment-foo"
TXT_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TXT_TESTFILE_NAME)

PNG_TESTFILE_NAME = 'robotframework-testfile-06_attachments.png'
PNG_TESTFILE_CONTENT = unhexlify('89504e470d0a1a0a0000000d494844520000000100000001010300000025db56ca00000003504c5445ffffffa7c41bc80000000a4944415408d76360000000020001e221bc330000000049454e44ae426082')
PNG_TESTFILE_DESCRIPTION = "attachment-png-foo"
PNG_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), PNG_TESTFILE_NAME)

PDF_TESTFILE_CONTENT = u"""\
%PDF-1.1
%\u00a5\u571f\u00cb

1 0 obj
  << /Type /Catalog
     /Pages 2 0 R
  >>
endobj

2 0 obj
  << /Type /Pages
     /Kids [3 0 R]
     /Count 1
     /MediaBox [0 0 300 144]
  >>
endobj

3 0 obj
  <<  /Type /Page
      /Parent 2 0 R
      /Resources
       << /Font
           << /F1
               << /Type /Font
                  /Subtype /Type1
                  /BaseFont /Times-Roman
               >>
           >>
       >>
      /Contents 4 0 R
  >>
endobj

4 0 obj
  << /Length 55 >>
stream
  BT
    /F1 18 Tf
    0 0 Td
    (Hello World) Tj
  ET
endstream
endobj

xref
0 5
0000000000 65535 f
0000000018 00000 n
0000000077 00000 n
0000000178 00000 n
0000000457 00000 n
trailer
  <<  /Root 1 0 R
      /Size 5
  >>
startxref
565
%%EOF
""".encode('utf-8')
PDF_TESTFILE_DESCRIPTION = "attachment-pdf-foo"

PDF_TESTFILE_NAME = 'robotframework-testfile-06_attachments.pdf'
PDF_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME)

createFile(TXT_TESTFILE_PATH, TXT_TESTFILE_CONTENT)
createFile(PNG_TESTFILE_PATH, PNG_TESTFILE_CONTENT)
createFile(PDF_TESTFILE_PATH, PDF_TESTFILE_CONTENT)
