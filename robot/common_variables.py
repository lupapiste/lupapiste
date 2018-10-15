import datetime

CURRENT_DATETIME = datetime.datetime.now()
CURRENT_DATE = '{dt.day}.{dt.month}.{dt.year}'.format(dt = datetime.datetime.now())
TOMORROW = (CURRENT_DATETIME + datetime.timedelta( 1 )).strftime( "%d.%m.%Y")
