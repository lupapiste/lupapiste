LUPAPISTE.EventFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  var _data = ko.observable();

  var events = ["warranty-period-end",
    "license-period-start",
    "license-period-end",
    "license-started-not-ready",
    "license-ended-not-ready",
    "announced-to-ready-state-not-ready"];

  self.selected = ko.observableArray([]);

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "event"]);
  });

  function wrapInObject(events) {
    return _.map(events, function(event) {
      return {
        label: loc("applications.event." + event),
        id: event,
        behaviour: "singleSelection"};
    });
  }

  ko.computed(function() {
    self.selected([]);
    if (savedFilter()) {
      self.selected(wrapInObject(savedFilter()));
    }
  });

  self.data = ko.pureComputed(function() {
    return _data();
  });

  function load(){
    _data(events);
    return true;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }
};
