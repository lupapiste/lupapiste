// Used for filters that do not have multiple options, like free text, etc.
// NOTE: Unlike other filter services, this takes two parameters!
LUPAPISTE.SimpleFilterService = function(applicationFiltersService, fieldName) {
  "use strict";
  var self = this;

  self.selected = ko.observable();

  // Updates the current value from the managing service (triggered while loading saved filter templates)
  ko.computed(function() {
    var savedFilter = util.getIn(applicationFiltersService.selected(), ["filter", fieldName]);
    if (savedFilter) {
      self.selected(savedFilter);
    }
  });

};