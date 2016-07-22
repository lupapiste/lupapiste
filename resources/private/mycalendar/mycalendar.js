;(function() {
  "use strict";

  function MyCalendarsModel() {
    var self = this;
    self.calendars = ko.observableArray([]);
    self.noCalendarsFound = ko.observable(false);
    self.selectedCalendarId = ko.observable();
    self.reservationTypes = ko.observableArray();

    hub.subscribe("calendarService::myCalendarsFetched", function(event) {
      self.calendars(event.calendars);
      if (event.calendars.length > 0) {
        // conversion to string for compatibility with radio-field...
        self.selectedCalendarId("" + event.calendars[0].id);
        self.noCalendarsFound(false);
      } else {
        self.selectedCalendarId(undefined);
        self.noCalendarsFound(true);
      }
      console.log(self.noCalendarsFound());
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
