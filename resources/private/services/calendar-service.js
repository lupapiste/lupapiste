LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this,
      params = LUPAPISTE.config.calendars;

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

  var _getStartOfWeekMoment = function(week, year, weekObservable) {
    if (week && year) {
      return moment().set({"isoWeek": week, "year": year}).startOf("isoWeek");
    } else if (weekObservable) {
      return moment(weekObservable()[0].startOfDay).startOf("isoWeek");
    }
  };

  var doFetchCalendarWeek = function(event) {
    var startOfWeekMoment = _getStartOfWeekMoment(event.week, event.year, event.weekObservable);

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

  var doFetchApplicationCalendarWeek = function(event) {
    var startOfWeekMoment = _getStartOfWeekMoment(event.week, event.year, event.weekObservable);
    var slots = [];
    var _week = startOfWeekMoment.isoWeek();
    var _year = startOfWeekMoment.year();

    ajax.query("my-reserved-slots", { week: _week, year: _year })
      .success(function(data) {
        slots = slots.concat(data.reservations);

        notifyView(event, _weekdays(event, slots, startOfWeekMoment));

        if (event.clientId && event.applicationId) {
          var queryParams = { clientId: event.clientId,
                              week: startOfWeekMoment.isoWeek(), year: startOfWeekMoment.year(),
                              id: event.applicationId };
          // Optional params added if available
          if (!_.isUndefined(event.authorityId)) {
            queryParams.authorityId = event.authorityId;
          }
          if (!_.isUndefined(event.reservationTypeId)) {
            queryParams.reservationTypeId = event.reservationTypeId;
          }

          ajax.query("available-calendar-slots", queryParams)
            .success(function(data) {
              slots = _.concat(slots, data.availableSlots);
              slots = _.concat(slots,
                _.map(
                   _.filter(data.readOnlySlots, function(r) { return _.isEmpty(slots) || !_.includes(_.map(slots, "id"), r.id); }),
                   function(r) { return _.set(r, "status", "read-only"); }));
              notifyView(event, _weekdays(event, slots, startOfWeekMoment));
            })
            .error(function(e) {
              hub.send("indicator", {style: "negative", message: e.text});
            })
            .call();
        }
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
      }).call();
  };

  var _fetchCalendar = hub.subscribe("calendarService::fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.calendarId, userId: event.user})
      .success(function(data) {
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

  var _fetchApplicationCalendarConfig = hub.subscribe("calendarService::fetchApplicationCalendarConfig", function(event) {
    ajax.query("application-calendar-config", {id: event.applicationId})
      .success(function(d) {
        hub.send("calendarService::applicationCalendarConfigFetched",
          { authorities: d.authorities || [],
            reservationTypes: d.reservationTypes,
            defaultLocation: d.defaultLocation });
      })
      .call();
  });

  var _fetchReservationTypes = hub.subscribe("calendarService::fetchOrganizationReservationTypes", function(event) {
    ajax.query("reservation-types-for-organization", {organizationId: event.organizationId})
      .success(function (data) {
        var obj = {};
        obj[data.organization] = data.reservationTypes;
        hub.send("calendarService::organizationReservationTypesFetched",
          { organizationId: data.organization, reservationTypes: data.reservationTypes });
      })
      .call();
  });

  var _fetchMyCalendars = hub.subscribe("calendarService::fetchMyCalendars", function() {
    ajax.query("my-calendars")
      .success(function(data) {
        hub.send("calendarService::myCalendarsFetched", {calendars: data.calendars});
      }).error(function() {
        hub.send("calendarService::serviceNotAvailable");
      })
      .call();
  });

  var _fetchSlots = hub.subscribe("calendarService::fetchCalendarSlots", function(event) {
    doFetchCalendarWeek(event);
  });

  var _fetchApplicationCalendarSlots = hub.subscribe("calendarService::fetchApplicationCalendarSlots", function(event) {
    doFetchApplicationCalendarWeek(event);
  });

  var _createSlots = hub.subscribe("calendarService::createCalendarSlots", function(event) {
    ajax
      .command("create-calendar-slots", {calendarId: event.calendarId, slots: event.slots})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        doFetchCalendarWeek({calendarId: event.calendarId, weekObservable: event.weekObservable});
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
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
        hub.send("indicator", {style: "negative", message: e.text});
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
        hub.send("indicator", {style: "negative", message: e.text});
        doFetchCalendarWeek(event);
      })
      .call();
  });

  var _reserveSlot = hub.subscribe("calendarService::reserveCalendarSlot", function(event) {
    ajax
      .command("reserve-calendar-slot", { clientId: event.clientId, slotId: event.slot().id, reservationTypeId: event.reservationTypeId,
                                          comment: event.comment(), location: event.location(), id: event.applicationId })
      .success(function() {
        hub.send("indicator", { style: "positive" });
        if (lupapisteApp.models.application.id() === event.applicationId) {
          repository.load(ko.unwrap(lupapisteApp.models.application.id));
        }
        hub.send("calendarView::updateOperationProcessed");
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
        hub.send("calendarView::updateOperationProcessed");
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
    hub.unsubscribe(_reserveSlot);
    hub.unsubscribe(_fetchApplicationCalendarSlots);
    hub.unsubscribe(_fetchApplicationCalendarConfig);
  };
};
