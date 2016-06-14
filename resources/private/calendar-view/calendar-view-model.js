LUPAPISTE.CalendarViewModel = function (params) {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService;

  self.calendarWeekdays = ko.observableArray();
  self.reservationTypes = ko.observableArray();
  self.startOfWeek = ko.observable(moment().startOf("isoWeek"));
  self.calendarId = ko.observable();
  self.userId = ko.observable();

  if (params.searchConditions && params.searchConditions.calendarId) {
    self.calendarId = params.searchConditions.calendarId; // observable from parent
    self.userId = params.searchConditions.userId; // observable from parent
  }
  self.reservationTypes = params.reservationTypes; // observable from parent

  self.firstFullHour = calendarService.params().firstFullHour;
  self.lastFullHour = calendarService.params().lastFullHour;

  var timelineTimesBuilder = function() {
    var times = [];
    ko.utils.arrayForEach(ko.utils.range(self.firstFullHour, self.lastFullHour), function(hour) {
      times.push({ viewText: _.padStart(hour + ":00", 5, "0") });
    });
    return times;
  };

  self.timelineTimes = ko.observableArray(timelineTimesBuilder());

  self.timelineSlots = function(weekday) {
    var times = [];
    var weekdayStr = moment(weekday.startOfDay).format("dddd");
    ko.utils.arrayForEach(ko.utils.range(self.firstFullHour, self.lastFullHour), function(hour) {
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
    return ((start.hour() - self.firstFullHour) * 60 + start.minute()) + "px";
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

  self.calendarId.subscribe(function(val) {
    if (typeof val !== "undefined") {
      hub.send("calendarService::fetchCalendar",
        {calendarId: self.calendarId(), user: self.userId(),
         reservationTypesObservable: self.reservationTypes});
    }
  });

  ko.computed(function() {
    hub.send("calendarService::fetchCalendarSlots",
      { calendarId: self.calendarId(),
        week: self.startOfWeek().isoWeek(),
        year: self.startOfWeek().year(),
        weekObservable: self.calendarWeekdays});
  });

  self.gotoToday = function() {
    self.startOfWeek(moment().startOf("isoWeek"));
  };

  self.gotoPreviousWeek = function() {
    self.startOfWeek(self.startOfWeek().add(-1, "weeks"));
  };

  self.gotoFollowingWeek = function() {
    self.startOfWeek(self.startOfWeek().add(1, "weeks"));
  };

};