LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this,
      params = LUPAPISTE.config.calendars;

  self.reservationTypesByOrganization = ko.observable();
  self.params = ko.observable(params);

  var _weekdays = function(calendarId, slots, startOfWeekMoment) {
    var now = moment();
    return _.map([1, 2, 3, 4, 5], function(i) {
      var day = startOfWeekMoment.set({ "isoWeekday": i, "hour": params.firstFullHour, "minutes": 0, "seconds": 0 });
      var slotsForDay = _.filter(slots, function(s) { return day.isSame(s.startTime, "day"); });
      return {
        calendarId: calendarId,
        startOfDay: day.valueOf(),
        endOfDay: moment(day).hour(params.lastFullHour).add(params.timeSlotLengthMinutes, "minutes").valueOf(),
        today: day.isSame(now, "day"),
        slots: _.map(slotsForDay,
          function(s) { return _.extend(s, { duration: moment(s.endTime).diff(s.startTime) }); })};
    });
  };

  var notifyView = function(event, weekdays) {
    if (event.weekObservable) {
      event.weekObservable(weekdays);
    }
  };

  var doFetchCalendarWeek = function(event) {
    var startOfWeekMoment;
    if (event && event.week && event.year) {
      startOfWeekMoment = moment().set({"isoWeek": event.week, "year": event.year}).startOf("isoWeek");
    } else if (event && event.weekObservable) {
      startOfWeekMoment = moment(event.weekObservable()[0].startOfDay).startOf("isoWeek");
    }

    if (event.calendarId) {
      ajax.query("calendar-slots", { calendarId: event.calendarId,
                                     week: startOfWeekMoment.isoWeek(),
                                     year: startOfWeekMoment.year() })
        .success(function(data) {
          notifyView(event, _weekdays(event.calendarId, data.slots, startOfWeekMoment));
        })
        .call();
    } else {
      notifyView(event, _weekdays(null, [], startOfWeekMoment));
    }

  };

  var _fetchCalendar = hub.subscribe("calendarService::fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.calendarId, userId: event.user})
      .success(function(data) {
        if (event.reservationTypesObservable) {
          event.reservationTypesObservable(self.reservationTypesByOrganization()[data.calendar.organization]);
        }
        if (event.calendarObservable) {
          event.calendarObservable(data.calendar);
        }
      })
      .call();
  });

  var _fetchOrgCalendars = hub.subscribe("calendarService::fetchOrganizationCalendars", function() {
    ajax.query("calendars-for-authority-admin")
      .success(function(d) {
        hub.send("calendarService::organizationCalendarsFetched", { calendars: d.users || [] });
      })
      .call();
   });

  var _fetchReservationTypes = hub.subscribe("calendarService::fetchOrganizationReservationTypes", function() {
    ajax.query("reservation-types-for-organization")
      .success(function (data) {
        var obj = {};
        obj[data.organization] = data.reservationTypes;
        self.reservationTypesByOrganization(obj);
        hub.send("calendarService::organizationReservationTypesFetched", { reservationTypes: data.reservationTypes });
      })
      .call();
  });

  var _fetchMyCalendars = hub.subscribe("calendarService::fetchMyCalendars", function() {
    ajax.query("my-calendars")
      .success(function(data) {
        self.reservationTypesByOrganization(data.reservationTypes);
        hub.send("calendarService::myCalendarsFetched", {calendars: data.calendars});
      })
      .call();
  });

  var _fetchSlots = hub.subscribe("calendarService::fetchCalendarSlots", function(event) {
    doFetchCalendarWeek(event);
  });

  var _createSlots = hub.subscribe("calendarService::createCalendarSlots", function(event) {
    ajax
      .command("create-calendar-slots", {calendarId: event.calendarId, slots: event.slots})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        doFetchCalendarWeek({calendarId: event.calendarId, weekObservable: event.weekObservable});
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.code});
        doFetchCalendarWeek(event);
      })
      .call();
  });

  var _updateSlot = hub.subscribe("calendarService::updateCalendarSlot", function(event) {
    ajax
      .command("update-calendar-slot", {slotId: event.id, reservationTypeIds: event.reservationTypes})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        doFetchCalendarWeek({calendarId: event.calendarId, weekObservable: event.weekObservable});
      })
      .error(function (e) {
        hub.send("indicator", {style: "negative", message: e.code});
        doFetchCalendarWeek(event);
      })
      .call();
  });

  var _deleteSlot = hub.subscribe("calendarService::deleteCalendarSlot", function(event) {
    ajax
      .command("delete-calendar-slot", {slotId: event.id})
      .success(function() {
        hub.send("indicator", {style: "positive", message: "calendar.deleted"});
        doFetchCalendarWeek({calendarId: event.calendarId, weekObservable: event.weekObservable});
      })
      .error(function (e) {
        hub.send("indicator", {style: "negative", message: e.code});
        doFetchCalendarWeek(event);
      })
      .call();
  });

  self.dispose = function() {
    hub.unsubscribe(_fetchOrgCalendars);
    hub.unsubscribe(_fetchReservationTypes);
    hub.unsubscribe(_fetchCalendar);
    hub.unsubscribe(_fetchMyCalendars);
    hub.unsubscribe(_fetchSlots);
    hub.unsubscribe(_createSlots);
    hub.unsubscribe(_updateSlot);
    hub.unsubscribe(_deleteSlot);
  };
};
