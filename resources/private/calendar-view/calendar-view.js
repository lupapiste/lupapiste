var calendarView = (function($) {
  "use strict";

  function ViewCalendarModel(component) {
    var self = this;

    self.component = component;

    self.name = ko.observable();
    self.organization = ko.observable();
    self.weekdays = ko.observableArray();
    self.timelineTimes = ko.observableArray();

    var timelineTimesBuilder = function() {
      var times = [];
      ko.utils.arrayForEach(ko.utils.range(7, 16), function(hour) {
        times.push(hour+":00");
        times.push(hour+":30");
      });
      return times;
    };

    self.init = function(params) {
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));
      self.weekdays([{text: "ma"}, {text: "ti"}, {text: "ke"}, {text: "to"}, {text: "pe"}]);
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
