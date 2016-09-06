;(function() {
  "use strict";

  function MyCalendarsModel() {
    var self = this;
    self.calendars = ko.observableArray([]);
    self.noCalendarsFound = ko.observable(false);
    self.selectedCalendar = ko.observable();
    self.selectedCalendarId = ko.observable();
    self.reservationTypes = ko.observableArray();

    self.viewMode = ko.observable("calendar");

    self.setViewMode = function(mode) {
      self.viewMode(mode);
    }

    hub.subscribe("calendarService::myCalendarsFetched", function(event) {
      self.calendars(event.calendars);
      if (event.calendars.length > 0) {
        self.selectedCalendar(event.calendars[0]);
        self.noCalendarsFound(false);
      } else {
        self.selectedCalendar(undefined);
        self.noCalendarsFound(true);
      }
    });

    self.selectedCalendar.subscribe(function(val) {
      self.reservationTypes(_.get(val, "reservationTypes", []));
      self.selectedCalendarId(_.get(val, "id", undefined));
    });
  }

  $(function() {
    $("#mycalendar").applyBindings({ mycalendars: new MyCalendarsModel() });
  });

  if (features.enabled("ajanvaraus")) {
    hub.onPageLoad("mycalendar", function() {
      hub.send("calendarService::fetchMyCalendars");
    });
  }

})();
