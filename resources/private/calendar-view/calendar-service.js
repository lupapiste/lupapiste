LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this;

  self.calendar = ko.observable();
  self.calendarWeekdays = ko.observableArray();

  function Weekday(calendarId, startOfDay, slots) {
    var self = this;
    self.calendarId = calendarId;
    self.startOfDay = startOfDay;
    self.str = startOfDay.format("DD.MM."); // TODO -> ko.bindingHandlers.calendarViewDateHeader?
    self.slots = ko.observableArray(slots);
  }

  function ReservationSlot(startTime, endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
    this.duration = moment.duration(endTime.diff(startTime));
    this.positionTop = ((startTime.hour() - 7) * 60 + startTime.minute()) + 'px';
    this.height = this.duration.asMinutes() + 'px';
    this.viewText = startTime.format("H:mm") + " - " + this.endTime.format("H:mm") + " Vapaa";
  }

  var doFetchCalendarSlots = function(event) {
    ajax.query("calendar-slots", {calendarId: event.id, week: event.week, year: event.year})
      .success(function(data) {
        var startOfWeek = moment().isoWeek(event.week).year(event.year).startOf('isoWeek');
        var weekdays = _.map([1, 2, 3, 4, 5], function(i) {
          var day = startOfWeek.clone().isoWeekday(i);
          var slotsForDay = _.filter(data.slots, function(s) { return day.isSame(s.startTime, 'day'); });
          return new Weekday(event.id, day,
            _.map(slotsForDay, function(s) { return new ReservationSlot(moment(s.time.start), moment(s.time.end)); }));
        });

        console.log(weekdays);
        self.calendarWeekdays(weekdays);
      })
      .call();
  };

  var _fetchCalendar = hub.subscribe("calendarService::fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.id})
      .success(function(data) {
        self.calendar(data.calendar);
        doFetchCalendarSlots({id: data.calendar.id, week: moment().isoWeek(), year: moment().year()});
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
        if (event.modalClose) {
          LUPAPISTE.ModalDialog.close();
        }
      })
      .call();
  });

  self.dispose = function() {
    hub.unsubscribe(_fetchCalendar);
    hub.unsubscribe(_createSlots);
  };
};