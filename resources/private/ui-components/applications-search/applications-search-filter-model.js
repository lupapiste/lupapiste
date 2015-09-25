LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedFilters;

  self.query = ko.observable();

  self.searchFieldSelected = ko.observable(false);

  self.filterCount = ko.computed(function() {
    return _.size(ko.unwrap(lupapisteApp.services.handlerFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.tagFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.operationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.organizationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.areaFilterService.selected));
  });

  self.showAdvancedFilters = ko.observable(false);
};
