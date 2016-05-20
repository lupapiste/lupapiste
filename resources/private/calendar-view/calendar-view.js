var calendarView = (function($) {
  "use strict";

  function ViewCalendarModel(component, calendarService) {
    var self = this;

    self.component = component;

    self.calendar = calendarService.calendar;
    self.calendarWeekdays = calendarService.calendarWeekdays;
    self.week = calendarService.calendarQuery.week;
    self.year = calendarService.calendarQuery.year;

    self.month = function() {
      return moment().set({'year': self.year(), 'isoWeek': self.week()}).valueOf();
    };

    self.calendarId = ko.observable();

    self.firstFullHour = ko.observable(7);
    self.lastFullHour = ko.observable(16);

    var timelineTimesBuilder = function() {
      var times = [];
      ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
        times.push({ hour: hour, minutes:  0, viewText: hour + ":00" });
      });
      return times;
    };

    self.timelineTimes = ko.observableArray(timelineTimesBuilder());

    self.slotPositionTop = function(slot) {
      var start = moment(slot.startTime);
      return ((start.hour() - self.firstFullHour()) * 60 + start.minute()) + 'px';
    };

    self.slotHeight = function(slot) {
      var duration = moment.duration(slot.duration);
      return duration.asMinutes() + 'px';
    };

    self.slotViewText = function(slot) {
      return moment(slot.startTime).format("H:mm") + " - " + moment(slot.endTime).format("H:mm") + " Vapaa";
    };

    self.clickHandler = function(clazz) {
      if (clazz === 'timeline-slot') {
        hub.send("calendarView::timelineSlotClicked",
          { calendarId: this.calendarWeekday.calendarId,
            weekday: this.calendarWeekday,
            hour: this.slot.hour,
            minutes: this.slot.minutes });
      } else if (clazz === 'reservation-slot') {
        hub.send("calendarView::reservationSlotClicked",
          { calendarId: this.calendarWeekday.calendarId,
            slot: this.slot });
      }
    };

    self.gotoToday = function() {
      hub.send("calendarService::fetchCalendarSlots", { week: moment().isoWeek(), year: moment().year() });
    };

    self.gotoPreviousWeek = function() {
      console.log("CLICK PREVIOUS");
      hub.send("calendarService::fetchCalendarSlots", { increment: -1 });
    };

    self.gotoFollowingWeek = function() {
      hub.send("calendarService::fetchCalendarSlots", { increment: 1 });
    };

    component.applyBindings(self);
  }

  return {
    create: function(targetComponent, calendarService) {
      var component = $("#calendar-view-templates .weekview-table").clone();
      var viewCalendarModel = new ViewCalendarModel(component, calendarService);
      targetComponent.append(component);
      return viewCalendarModel;
    }
  };

})(jQuery);
