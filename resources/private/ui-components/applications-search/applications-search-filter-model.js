LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;
  self.externalApi = params.externalApi;
  self.gotResults = params.gotResults;

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedFilters;

  self.savedForemanFilters = lupapisteApp.services.applicationFiltersService.savedForemanFilters;

  self.searchFieldSelected = ko.observable(false);

  self.showAdvancedFilters = ko.observable(false);
};
