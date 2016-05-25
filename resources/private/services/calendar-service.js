LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this;

  self.calendar = ko.observable();
  self.calendarWeekdays = ko.observableArray();

  self.myCalendars = ko.observableArray();

  self.calendarQuery = {
    calendarId: ko.observable(),
    week: ko.observable(),
    year: ko.observable()
  };

  var doFetchCalendarSlots = function(event) {
    if (event && event.id) {
      self.calendarQuery.calendarId(event.id);
    }
    if (event && event.week && event.year) {
      self.calendarQuery.week(event.week);
      self.calendarQuery.year(event.year);
    } else if (event && event.increment) {
      var newStartOfWeek = moment().year(self.calendarQuery.year()).isoWeek(self.calendarQuery.week()).add(event.increment, "weeks");
      self.calendarQuery.year(newStartOfWeek.year());
      self.calendarQuery.week(newStartOfWeek.isoWeek());
    }

    var week = self.calendarQuery.week();
    var year = self.calendarQuery.year();
    ajax.query("calendar-slots", { calendarId: self.calendarQuery.calendarId(),
                                   week: week,
                                   year: year })
      .success(function(data) {
        var now = moment();
        var startOfWeek = moment().isoWeek(week).year(year).startOf("isoWeek").valueOf();
        var weekdays = _.map([1, 2, 3, 4, 5], function(i) {
          var day = moment(startOfWeek).isoWeekday(i);
          var slotsForDay = _.filter(data.slots, function(s) { return day.isSame(s.startTime, "day"); });
          return {
            calendarId: self.calendarQuery.calendarId(),
            startOfDay: day.valueOf(),
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
        doFetchCalendarSlots({week: moment().isoWeek(), year: moment().year()});
      })
      .call();
  });

  var _fetchMyCalendars = hub.subscribe("calendarService::fetchMyCalendars", function(event) {
    ajax.query("my-calendars")
      .success(function(data) {
        self.myCalendars(data.calendars);
        if (data.calendars && data.calendars.length > 0) {
          self.calendar(data.calendars[0]);
          self.calendarQuery.calendarId(data.calendars[0].id);
          doFetchCalendarSlots({week: moment().isoWeek(), year: moment().year()});
        };
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
        hub.send("calendarView::reservationSlotCreated", {modalClose: event.modalClose});
        doFetchCalendarSlots();
      })
      .call();
  });

  self.dispose = function() {
    hub.unsubscribe(_fetchCalendar);
    hub.unsubscribe(_fetchMyCalendars);
    hub.unsubscribe(_fetchSlots);
    hub.unsubscribe(_createSlots);
  };
};