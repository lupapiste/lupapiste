LUPAPISTE.CalendarViewModel = function () {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService;

  self.calendar = calendarService.calendar;
  self.calendarWeekdays = calendarService.calendarWeekdays;
  self.startOfWeek = calendarService.calendarQuery.startOfWeek;

  self.calendarId = ko.observable();

  self.firstFullHour = calendarService.firstFullHour;
  self.lastFullHour = calendarService.lastFullHour;

  var timelineTimesBuilder = function() {
    var times = [];
    ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
      times.push({ viewText: _.padStart(hour + ":00", 5, "0") });
    });
    return times;
  };

  self.timelineTimes = ko.observableArray(timelineTimesBuilder());

  self.timelineSlots = function(weekday) {
    var times = [];
    var weekdayStr = moment(weekday.startOfDay).format("dddd");
    ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
      times.push({ weekday: weekday,
                   calendarId: weekday.calendarId,
                   hour: hour,
                   minutes:  0,
                   dataTestId: "timeline-slot-" + weekdayStr + "-" + _.padStart(hour, 2, "0") + "00" });
    });
    return times;
  };

  self.slotPositionTop = function(slot) {
    var start = moment(slot.startTime);
    return ((start.hour() - self.firstFullHour()) * 60 + start.minute()) + "px";
  };

  self.slotHeight = function(slot) {
    return moment.duration(slot.duration).asMinutes() + "px";
  };

  self.slotViewText = function(slot) {
    return _.map(slot.reservationTypes, function(d) { return d.name; }).join();
  };

  self.clickHandler = function(clazz) {
    if (clazz === "timeline-slot") {
      hub.send("calendarView::timelineSlotClicked",
        { calendarId: this.calendarId,
          weekday: this.weekday,
          hour: this.hour,
          minutes: this.minutes });
    } else if (clazz === "calendar-slot") {
      hub.send("calendarView::calendarSlotClicked",
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