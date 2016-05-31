;(function() {
  "use strict";

  function MyCalendarsModel() {
    var self = this;
    self.calendars = lupapisteApp.services.calendarService.myCalendars; // observableArray
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
