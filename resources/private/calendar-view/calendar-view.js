var calendarView = (function($) {
  "use strict";

  function ViewCalendarModel(component) {
    var self = this;

    self.component = component;

    self.name = ko.observable();
    self.organization = ko.observable();
    self.weekdays = ko.observableArray();
    self.timelineTimes = ko.observableArray();

    self.firstFullHour = ko.observable(7);
    self.lastFullHour = ko.observable(16);

    var timelineTimesBuilder = function() {
      var times = [];
      ko.utils.arrayForEach(ko.utils.range(self.firstFullHour(), self.lastFullHour()), function(hour) {
        times.push(hour+":00");
        times.push(hour+":30");
      });
      return times;
    };

    function Weekday(startOfDay, slots) {
      var self = this;
      self.startOfDay = startOfDay;
      self.str = startOfDay.format("DD.MM."); // TODO -> ko.bindingHandlers.calendarViewDateHeader?
      self.slots = ko.observableArray(slots);
    }

    function ReservedSlot(startTime, duration) {
      this.startTime = startTime;
      this.duration = duration;
      this.endTime = startTime.clone().add(duration);
      this.positionTop = ((startTime.hour() - self.firstFullHour()) * 60 + startTime.minute()) + 'px';
      this.height = duration.asMinutes() + 'px';
      this.viewText = startTime.format("H:mm") + " - " + this.endTime.format("H:mm") + " Varaus";
    }

    self.init = function(params) {
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));

      // DUMMY DATA
      var startOfWeek = moment().startOf('isoWeek');
      self.weekdays([new Weekday(startOfWeek.clone().isoWeekday(1), []),
                     new Weekday(startOfWeek.clone().isoWeekday(2),
                       [new ReservedSlot(startOfWeek.clone().isoWeekday(2).hour(9), moment.duration(1, 'hours'))]),
                     new Weekday(startOfWeek.clone().isoWeekday(3), []),
                     new Weekday(startOfWeek.clone().isoWeekday(4), []),
                     new Weekday(startOfWeek.clone().isoWeekday(5), [])]);

      self.timelineTimes(timelineTimesBuilder());
    };

    component.applyBindings(self);
  }

  return {
    create: function(targetOrId) {
      var component = $("#calendar-view-templates .weekview-table").clone();
      var viewCalendarModel = new ViewCalendarModel(component);
      (_.isString(targetOrId) ? $("#" + targetOrId) : targetOrId).append(component);
      return viewCalendarModel;
    }
  };

})(jQuery);
