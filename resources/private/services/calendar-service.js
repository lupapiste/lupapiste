LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this;

  self.calendar = ko.observable();
  self.calendarWeekdays = ko.observableArray();

  self.myCalendars = ko.observableArray([]);
  self.organizationCalendars = ko.observableArray();
  self.reservationTypesByOrganization = ko.observable();

  self.firstFullHour = ko.observable(8);
  self.lastFullHour = ko.observable(16);

  // Data related to the current calendar view
  self.calendarQuery = {
    calendarId: ko.observable(),
    reservationTypes: ko.observableArray(),
    startOfWeek: ko.observable()
  };

  var doFetchCalendarSlots = function(event) {
    if (event && event.id) {
      self.calendarQuery.calendarId(event.id);
    }
    if (event && event.week && event.year) {
      var newStartOfWeek = moment().set({'isoWeek': event.week, 'year': event.year}).startOf("isoWeek").valueOf();
      self.calendarQuery.startOfWeek(newStartOfWeek);
    } else if (event && event.increment) {
      var newStartOfWeek = moment(self.calendarQuery.startOfWeek()).add(event.increment, "weeks");
      self.calendarQuery.startOfWeek(newStartOfWeek.valueOf());
    }

    var startOfWeekMoment = moment(self.calendarQuery.startOfWeek());
    ajax.query("calendar-slots", { calendarId: self.calendarQuery.calendarId(),
                                   week: startOfWeekMoment.isoWeek(),
                                   year: startOfWeekMoment.year() })
      .success(function(data) {
        var now = moment();
        var weekdays = _.map([1, 2, 3, 4, 5], function(i) {
          var day = moment(startOfWeekMoment).isoWeekday(i).hour(self.firstFullHour()).minutes(0).seconds(0);
          var slotsForDay = _.filter(data.slots, function(s) { return day.isSame(s.startTime, "day"); });
          return {
            calendarId: self.calendarQuery.calendarId(),
            startOfDay: day.valueOf(),
            endOfDay: moment(day).hour(self.lastFullHour()).valueOf(),
            today: day.isSame(now, "day"),
            slots: _.map(slotsForDay,
              function(s) {
                return _.extend(s, { duration: moment(s.endTime).diff(s.startTime) });
              })};
        });
        self.calendarWeekdays(weekdays);
      })
      .call();
  };

  var _fetchCalendar = hub.subscribe("calendarService::fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.id, userId: event.user})
      .success(function(data) {
        self.calendar(data.calendar);
        self.calendarQuery.calendarId(data.calendar.id);
        self.calendarQuery.reservationTypes(self.reservationTypesByOrganization()[data.calendar.organization]);
        doFetchCalendarSlots({week: moment().isoWeek(), year: moment().year()});
      })
      .call();
  });

  var _fetchOrgCalendars = hub.subscribe("calendarService::fetchOrganizationCalendars", function() {
    ajax.query("calendars-for-authority-admin")
      .success(function(d) {
        self.organizationCalendars(d.users || []);
        hub.send("calendarService::organizationCalendarsFetched");
      })
      .call();
   });

  var _fetchReservationTypes = hub.subscribe("calendarService::fetchOrganizationReservationTypes", function() {
    ajax.query("reservation-types-for-organization")
      .success(function (data) {
        var obj = {};
        obj[data.organization] = data.reservationTypes;
        self.calendarQuery.reservationTypes(data.reservationTypes);
        self.reservationTypesByOrganization(obj);
        hub.send("calendarService::organizationReservationTypesFetched");
      })
      .call();
  });

  var _fetchMyCalendars = hub.subscribe("calendarService::fetchMyCalendars", function() {
    ajax.query("my-calendars")
      .success(function(data) {
        self.myCalendars(data.calendars);
        self.reservationTypesByOrganization(data.reservationTypes);
        hub.send("calendarService::myCalendarsFetched");
      })
      .call();
  });

  var _fetchSlots = hub.subscribe("calendarService::fetchCalendarSlots", function(event) {
    doFetchCalendarSlots(event);
  });

  var _createSlots = hub.subscribe("calendarService::createCalendarSlots", function(event) {
    ajax
      .command("create-calendar-slots", {calendarId: event.calendarId, slots: event.slots})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        doFetchCalendarSlots();
      })
      .call();
  });

  var _updateSlot = hub.subscribe("calendarService::updateCalendarSlot", function(event) {
    ajax
      .command("update-calendar-slot", {slotId: event.id, reservationTypeIds: event.reservationTypes})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        doFetchCalendarSlots();
      })
      .error(function (e) {
        hub.send("indicator", {style: "negative", message: e.text});
      })
      .call();
  });

  var _deleteSlot = hub.subscribe("calendarService::deleteCalendarSlot", function(event) {
    ajax
      .command("delete-calendar-slot", {slotId: event.id})
      .success(function() {
        hub.send("indicator", {style: "positive", message: "calendar.deleted"});
        doFetchCalendarSlots();
      })
      .error(function (e) {
        hub.send("indicator", {style: "negative", message: e.text});
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