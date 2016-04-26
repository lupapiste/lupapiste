var calendarView = (function($) {
  "use strict";

  function ViewCalendarModel(component, calendarService) {
    var self = this;

    self.component = component;

    self.calendar = calendarService.calendar;
    self.calendarWeekdays = calendarService.calendarWeekdays;

    self.calendarId = ko.observable();

    self.firstFullHour = ko.observable(7);
    self.lastFullHour = ko.observable(16);

    var timelineTimesBuilder = function() {
      var times = [];
      ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
        times.push({ hour: hour, minutes:  0, viewText: hour + ":00" });
        times.push({ hour: hour, minutes: 30, viewText: hour + ":30" });
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
          { calendarId: this.weekday.calendarId,
            weekday: this.weekday,
            hour: this.slot.hour,
            minutes: this.slot.minutes });
      } else if (clazz === 'reservation-slot') {
        hub.send("calendarView::reservationSlotClicked",
          { calendarId: this.weekday.calendarId,
            slot: this.slot });
      }
    }

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
