LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.filterCount = ko.computed(function() {
    return _.size(ko.unwrap(lupapisteApp.services.tagFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.operationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.organizationFilterService.selected)) +
           _.size(ko.unwrap(lupapisteApp.services.areaFilterService.selected));
  });

  self.showAdvancedFilters = ko.observable(self.filterCount() > 0);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  self.saveAdvancedFilters = function() {
    var filter = {
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    ajax
    .command("update-default-application-filter", {filter: filter})
    .error(_.noop)
    .success(function() {
      // TODO show indicator for success
    })
    .call();
  };

  hub.onPageLoad("applications", function() {
    self.showAdvancedFilters(self.filterCount() > 0);
  });
};
