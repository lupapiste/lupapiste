;(function() {
  "use strict";

  var calendarViewModel;

  $(function() {
    $("#mycalendar").applyBindings({});
  });

  if (features.enabled("ajanvaraus")) {
    hub.onPageLoad("mycalendar", function() {
      if (!calendarViewModel) {
        var component = $("#mycalendar .calendar-table");
        calendarViewModel = calendarView.create(component);
        hub.send("calendarService::fetchMyCalendars");
      }
    });
  }

})();
