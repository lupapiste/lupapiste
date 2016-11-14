LUPAPISTE.CalendarService = function() {
  "use strict";
  var params = LUPAPISTE.config.calendars;

  function hubscribe( eventName, fun ) {
    hub.subscribe( "calendarService::" + eventName, fun );
  }

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

        notifyView(event, _weekdays(null, slots, startOfWeekMoment));

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
              notifyView(event, _weekdays(null, slots, startOfWeekMoment));
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

  hubscribe("fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.calendarId, userId: event.user})
      .success(function(data) {
        if (event.calendarObservable) {
          event.calendarObservable(data.calendar);
        }
      })
      .call();
  });

  hubscribe("fetchOrganizationCalendars", function() {
    ajax.query("calendars-for-authority-admin")
      .success(function(d) {
        hub.send("calendarService::organizationCalendarsFetched", { calendars: d.users || [] });
      })
      .call();
   });

  hubscribe("fetchApplicationCalendarConfig", function(event) {
    ajax.query("application-calendar-config", {id: event.applicationId})
      .success(function(d) {
        hub.send("calendarService::applicationCalendarConfigFetched",
          { authorities: d.authorities || [],
            reservationTypes: d.reservationTypes,
            defaultLocation: d.defaultLocation });
      })
      .call();
  });

  hubscribe("fetchOrganizationReservationTypes", function(event) {
    ajax.query("reservation-types-for-organization", {organizationId: event.organizationId})
      .success(function (data) {
        var obj = {};
        obj[data.organization] = data.reservationTypes;
        hub.send("calendarService::organizationReservationTypesFetched",
          { organizationId: data.organization, reservationTypes: data.reservationTypes });
      })
      .call();
  });

  hubscribe("fetchMyCalendars", function() {
    ajax.query("my-calendars")
      .success(function(data) {
        hub.send("calendarService::myCalendarsFetched", {calendars: data.calendars});
      }).error(function() {
        hub.send("calendarService::serviceNotAvailable");
      })
      .call();
  });

  hubscribe("fetchCalendarActionsRequired", function() {
    ajax.query("calendar-actions-required")
      .success(function(data) {
        hub.send("calendarService::calendarActionsRequiredFetched", {actionsRequired: data.actionsRequired});
      }).error(function() {
        hub.send("calendarService::serviceNotAvailable");
      })
      .call();
  });

  hubscribe("fetchAllAppointments", function() {
    ajax.query("applications-with-appointments")
      .success(function(data) {
        hub.send("calendarService::allAppointmentsFetched", {appointments: data.appointments});
      }).error(function() {
        hub.send("calendarService::serviceNotAvailable");
      })
      .call();
  });

  hubscribe("fetchCalendarSlots", function(event) {
    doFetchCalendarWeek(event);
  });

  hubscribe("fetchApplicationCalendarSlots", function(event) {
    doFetchApplicationCalendarWeek(event);
  });

  hubscribe("createCalendarSlots", function(event) {
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

  hubscribe("updateCalendarSlot", function(event) {
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

  hubscribe("deleteCalendarSlot", function(event) {
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

  hubscribe("reserveCalendarSlot", function(event) {
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
  
  hubscribe("cancelReservation", function(event) {
    ajax
      .command("cancel-reservation", { id: event.applicationId, reservationId: event.reservationId })
      .success(function() {
        hub.send("indicator", { style: "positive" });
        doFetchApplicationCalendarWeek({ clientId: event.clientId, authorityId: event.authorityId,
                                         reservationTypeId: event.reservationTypeId, weekObservable: event.weekObservable });
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
      })
      .call();
  });
};
