;(function() {
  "use strict";

  function MyCalendarsModel() {
    var self = this;
    self.calendars = ko.observableArray([]);
    self.noCalendarsFound = ko.observable(false);
    self.selectedCalendar = ko.observable();
    self.selectedCalendarId = ko.observable();
    self.reservationTypes = ko.observableArray();

    self.calendarNotificationsByDay = ko.observableArray([]);
    self.allAppointmentsByDay = ko.observableArray([]);

    self.viewMode = ko.observable("calendar");

    self.setViewMode = function(mode) {
      if (mode === "list") {
        self.calendarNotificationsByDay([]);
        hub.send("calendarService::fetchCalendarActionsRequired");
        hub.send("calendarService::fetchAllAppointments");
      }
      self.viewMode(mode);
    };

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

    // Show notifications for calendar-related actions required by this user
    hub.subscribe("calendarService::calendarActionsRequiredFetched", function(event) {
      var actionsRequired = _.map(event.actionsRequired,
        function(n) {
          n.acknowledged = ko.observable("none");
          return n;
        });
      self.calendarNotificationsByDay(_.transform(
        _.groupBy(actionsRequired, function(n) { return moment(n.startTime).startOf("day").valueOf(); }),
        function (result, value, key) {
          return result.push({ day: _.parseInt(key), notifications: value });
        }, []));
    });

    hub.subscribe("calendarService::allAppointmentsFetched", function(event) {
      var appointments = _.map(event.appointments,
        function(n) {
          n.acknowledged = ko.observable("none");
          return n;
        });
      self.allAppointmentsByDay(_.transform(
        _.groupBy(appointments, function(n) { return moment(n.startTime).startOf("day").valueOf(); }),
        function (result, value, key) {
          return result.push({ day: _.parseInt(key),
                               notifications: _.transform(value, function(acc, n) {
                                                n.participantsText = _.map(n.participants, function (p) { return util.partyFullName(p); }).join(", ");
                                                acc.push(n);
                                              }, [])
                             });
        }, []));
    });

    self.selectedCalendar.subscribe(function(val) {
      self.reservationTypes(_.get(val, "reservationTypes", []));
      self.selectedCalendarId(_.get(val, "id", undefined));
    });

    self.appointmentParticipants = function(r) {
      return _.map(r.participants(), function (p) { return util.partyFullName(p); }).join(", ");
    };

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
