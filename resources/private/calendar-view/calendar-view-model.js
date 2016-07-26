LUPAPISTE.CalendarViewModel = function (params) {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService;

  self.calendarWeekdays = ko.observableArray();
  self.reservationTypes = ko.observableArray();
  self.startOfWeek = ko.observable(moment().startOf("isoWeek"));
  self.calendarId = ko.observable();
  self.userId = ko.observable();
  self.clientId = ko.observable();
  self.reservationTypeId = ko.observable();
  self.view = ko.observable();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  if (_.get(params, "searchConditions.calendarId")) {
    self.calendarId = params.searchConditions.calendarId; // observable from parent
  }
  self.userId = params.searchConditions.userId; // observable from parent
  self.reservationTypes = params.reservationTypes; // observable from parent
  self.clientId = params.searchConditions.clientId; // observable from parent
  self.reservationTypeId = params.searchConditions.reservationTypeId; // observable from parent
  self.participant = params.participant; // observable from parent

  self.view = params.view;

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
      self.sendEvent("calendarView", "timelineSlotClicked",
        { calendarId: this.calendarId,
          weekday: this.weekday,
          hour: this.hour,
          minutes: this.minutes });
    } else if (clazz === "calendar-slot") {
      self.sendEvent("calendarView", "calendarSlotClicked",
        { calendarId: this.calendarWeekday.calendarId,
          slot: this.slot });
    } else if (clazz === "available-slot") {
      self.sendEvent("calendarView", "availableSlotClicked",
        { slot: this.slot,
          weekday: this.calendarWeekday });
    }
  };

  self.calendarId.subscribe(function(val) {
    if (!_.isUndefined(val)) {
      self.sendEvent("calendarService", "fetchCalendar",
        {calendarId: val, user: self.userId(), reservationTypesObservable: self.reservationTypes});
    }
  });

  self.disposedComputed(function() {
    if (params.view === "applicationView") {
      hub.send("calendarService::fetchApplicationCalendarSlots",
        { clientId: self.clientId(),
          userId: self.userId(),
          reservationTypeId: self.reservationTypeId(),
          week: self.startOfWeek().isoWeek(),
          year: self.startOfWeek().year(),
          weekObservable: self.calendarWeekdays });
    } else {
      hub.send("calendarService::fetchCalendarSlots",
        { calendarId: self.calendarId(),
          week: self.startOfWeek().isoWeek(),
          year: self.startOfWeek().year(),
          weekObservable: self.calendarWeekdays });
    }
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