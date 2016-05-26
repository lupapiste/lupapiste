;(function() {
  "use strict";

  var calendarViewModel;

  var components = [
    {name: "calendar-slot-bubble"}
  ];

  _.forEach(components, function(component) {
    ko.components.register(component.name, {
      viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
      template: { element: (component.template ? component.template : component.name + "-template")},
      synchronous: component.synchronous ? true : false
    });
  });

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
