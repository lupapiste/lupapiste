;(function() {
  "use strict";

  var calendarViewModel;

  lupapisteApp.services.calendarService = new LUPAPISTE.CalendarService();

  $(function() {
    $("#mycalendar").applyBindings({});
  });

  if (features.enabled("ajanvaraus")) {
    hub.onPageLoad("mycalendar", function() {
      if (!calendarViewModel) {
        var component = $("#mycalendar .calendar-table");
        calendarViewModel = calendarView.create(component, new LUPAPISTE.CalendarService());
        hub.send("calendarService::fetchMyCalendars");
      }
    });
  }

})();
