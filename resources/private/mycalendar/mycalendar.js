;(function() {
  "use strict";
  var calendarService = lupapisteApp.services.calendarService,
      currentUserModel = lupapisteApp.models.currentUser;

  function MyCalendarsModel() {
    var self = this;
    self.calendars = calendarService.myCalendars; // observableArray
    self.noCalendarsFound = ko.observable(false);
    self.selectedCalendar = ko.observable();

    hub.subscribe("calendarService::myCalendarsFetched", function() {
      if (calendarService.myCalendars().length > 0) {
        // conversion to string for compatibility with radio-field...
        self.selectedCalendar("" + calendarService.myCalendars()[0].id);
      }
    });

    self.selectedCalendar.subscribe(function(val) {
      if (typeof val !== "undefined") {
        hub.send("calendarService::fetchCalendar", {id: self.selectedCalendar(), user: currentUserModel.id()});
      }
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
