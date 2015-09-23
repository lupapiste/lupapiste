LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  // TODO saved filters service
  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedFilters;

  self.query = ko.observable();

  self.filterCount = ko.computed(function() {
    return _.size(ko.unwrap(lupapisteApp.services.handlerFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.tagFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.operationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.organizationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.areaFilterService.selected));
  });

  self.showAdvancedFilters = ko.observable(self.filterCount() > 0);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  hub.onPageLoad("applications", function() {
    self.showAdvancedFilters(self.filterCount() > 0);
  });
};
