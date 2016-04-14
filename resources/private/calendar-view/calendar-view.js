var calendarView = (function($) {
  "use strict";

  function ViewCalendarModel(component) {
    var self = this;

    self.component = component;

    self.name = ko.observable();
    self.organization = ko.observable();
    self.weekdays = ko.observableArray();
    self.timelineTimes = ko.observableArray();
    self.timelineClickHandler = null;

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

    function ReservationSlot(startTime, duration) {
      this.startTime = startTime;
      this.duration = duration;
      this.endTime = startTime.clone().add(duration);
      this.positionTop = ((startTime.hour() - self.firstFullHour()) * 60 + startTime.minute()) + 'px';
      this.height = duration.asMinutes() + 'px';
      this.viewText = startTime.format("H:mm") + " - " + this.endTime.format("H:mm") + " Varaus";
    }

    self.clickHandler = function(clazz, data) {
      if (clazz === 'timeline-slot' && self.timelineClickHandler) {
        self.timelineClickHandler(this, data);
      }

      if (clazz === 'reservation-slot') {
        console.log('reservation-slot', data);
      }
    }

    self.init = function(params) {
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));

      console.log(params.source.slots);
      var startOfWeek = moment().startOf('isoWeek');
      self.weekdays(_.map([1, 2, 3, 4, 5], function(i) {
        var day = startOfWeek.clone().isoWeekday(i);
        var slotsForDay = _.filter(params.source.slots, function(s) { return day.isSame(s.startTime, 'day'); });
        return new Weekday(day, _.map(slotsForDay, function(s) { return new ReservationSlot(moment(s.startTime), moment.duration(s.duration)); }));
      }));

      self.timelineTimes(timelineTimesBuilder());
      self.timelineClickHandler = util.getIn(params, ["opts", "clickTimeline"], null);
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
