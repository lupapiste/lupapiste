# -*- coding: utf-8 -*-
import os.path

TXT_TESTFILE_NAME = 'robotframework-testfile-21_stamping.txt'
TXT_TESTFILE_CONTENT = 'Created by Lupapiste robot/tests/21_application_editing/variables.py'
TXT_TESTFILE_DESCRIPTION = "attachment-foo"
TXT_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), TXT_TESTFILE_NAME)

JPG_TESTFILE_NAME = 'robotframework-testfile-05_application_editing.jpg'
JPG_TESTFILE_CONTENT = '12321'
JPG_TESTFILE_DESCRIPTION = "attachment-jpg-foo"
JPG_TESTFILE_PATH = os.path.join(os.path.expanduser('~'), JPG_TESTFILE_NAME)

PDF_TESTFILE_CONTENT = """\
%PDF-1.1
%¥±ë

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
"""
PDF_TESTFILE_DESCRIPTION = "attachment-pdf-foo"

PDF_TESTFILE_NAME1 = 'robotframework-testfile-21_stamping_01v3.pdf'
PDF_TESTFILE_PATH1 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME1)

PDF_TESTFILE_NAME2 = 'robotframework-testfile-21_stamping_02v3.pdf'
PDF_TESTFILE_PATH2 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME2)
PDF_TESTFILE_NAME3 = 'robotframework-testfile-21_stamping_03v3.pdf'
PDF_TESTFILE_PATH3 = os.path.join(os.path.expanduser('~'), PDF_TESTFILE_NAME3)
PDF_TESTFILE_NAME4 = 'robotframework-testfile-21_stamping_04v3.pdf'
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

PDF_TESTFILE_NAME1_PDFA = 'robotframework-testfile-21_stamping_01v2.pdf'
PDF_TESTFILE_NAME2_PDFA = 'robotframework-testfile-21_stamping_02v2.pdf'
PDF_TESTFILE_NAME3_PDFA = 'robotframework-testfile-21_stamping_03v2.pdf'
PDF_TESTFILE_NAME4_PDFA = 'robotframework-testfile-21_stamping_04v2.pdf'
PDF_TXT_TESTFILE_NAME = 'robotframework-testfile-21_stamping.pdf'
