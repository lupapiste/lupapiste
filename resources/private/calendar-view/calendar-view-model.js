LUPAPISTE.CalendarViewModel = function (params) {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService;

  self.calendar = calendarService.calendar;
  self.calendarWeekdays = calendarService.calendarWeekdays;
  self.week = calendarService.calendarQuery.week;
  self.year = calendarService.calendarQuery.year;

  // helper function for the view
  self.month = function() {
    return moment().set({'year': self.year(), 'isoWeek': self.week(), 'isoWeekday': 1 /* Monday */}).valueOf();
  };

  self.calendarId = ko.observable();

  self.firstFullHour = ko.observable(8);
  self.lastFullHour = ko.observable(16);

  var timelineTimesBuilder = function() {
    var times = [];
    ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
      times.push({ hour: hour, minutes:  0, viewText: hour + ":00" });
    });
    return times;
  };

  self.timelineTimes = ko.observableArray(timelineTimesBuilder());

  self.slotPositionTop = function(slot) {
    var start = moment(slot.startTime);
    return ((start.hour() - self.firstFullHour()) * 60 + start.minute()) + 'px';
  };

  self.slotHeight = function(slot) {
    var duration = moment.duration(slot.duration);
    return duration.asMinutes() + 'px';
  };

  self.slotViewText = function(slot) {
    return _.map(slot.reservationTypes, function(d) { return d.name; }).join();
  };

  self.clickHandler = function(clazz) {
    if (clazz === 'timeline-slot') {
      hub.send("calendarView::timelineSlotClicked",
        { calendarId: this.calendarWeekday.calendarId,
          weekday: this.calendarWeekday,
          hour: this.slot.hour,
          minutes: this.slot.minutes });
    } else if (clazz === 'reservation-slot') {
      hub.send("calendarView::reservationSlotClicked",
        { calendarId: this.calendarWeekday.calendarId,
          slot: this.slot });
    }
  };

  self.gotoToday = function() {
    hub.send("calendarService::fetchCalendarSlots", { week: moment().isoWeek(), year: moment().year() });
  };

  self.gotoPreviousWeek = function() {
    hub.send("calendarService::fetchCalendarSlots", { increment: -1 });
  };

  self.gotoFollowingWeek = function() {
    hub.send("calendarService::fetchCalendarSlots", { increment: 1 });
  };

};