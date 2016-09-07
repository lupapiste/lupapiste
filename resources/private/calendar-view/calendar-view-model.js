LUPAPISTE.CalendarViewModel = function (params) {
  "use strict";
  var self = this;

  self.calendarWeekdays = ko.observableArray();
  self.startOfWeek = ko.observable(moment().startOf("isoWeek"));
  self.calendarId = ko.observable();
  self.userId = ko.observable();
  self.view = ko.observable();

  self.currentRole = params.currentRole;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  if (_.get(params, "searchConditions.calendarId")) {
    self.calendarId = params.searchConditions.calendarId; // observable from parent
  }
  if (_.get(params, "searchConditions.userId")) {
    self.userId = params.searchConditions.userId; // observable from parent
  }
  self.authority = params.searchConditions.authority; // observable from parent
  self.reservationTypes = params.reservationTypes; // observable from parent
  self.client = params.searchConditions.client; // observable from parent
  self.reservationType = params.searchConditions.reservationType; // observable from parent
  self.defaultLocation = params.defaultLocation;
  self.applicationModel = params.applicationModel;

  self.view = params.view;

  self.firstFullHour = LUPAPISTE.config.calendars.firstFullHour;
  self.lastFullHour = LUPAPISTE.config.calendars.lastFullHour;

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
                   dataTestId: "timeline-slot-" + weekdayStr + "-" + _.padStart(hour, 2, "0") + "00"});
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
    if (self.isDirty()) {
      return;
    }
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

  self.isDirty = ko.observable(false);
  self.addEventListener("calendarView", "updateOperationCalled", function() {
    self.isDirty(true);
  });
  self.addEventListener("calendarView", "updateOperationProcessed", function() {
    if (self.currentRole === "authority") {
      self.client(null);
    } else {
      self.authority(null);
    }
    self.reservationType(null);
    self.isDirty(false);
  });

  self.disposedComputed(function() {
    if (self.isDirty()) {
      return;
    }
    if (params.view === "applicationView") {
      var data = { clientId: ko.unwrap(_.get(self.client(), "id")),
                   authorityId: _.get(self.authority(), "id"),
                   reservationTypeId: _.get(self.reservationType(), "id"),
                   applicationId: _.get(self.applicationModel(), "id"),
                   week: self.startOfWeek().isoWeek(),
                   year: self.startOfWeek().year(),
                   weekObservable: self.calendarWeekdays };
      hub.send("calendarService::fetchApplicationCalendarSlots", data);
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