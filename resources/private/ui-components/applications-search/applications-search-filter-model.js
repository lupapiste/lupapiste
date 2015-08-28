LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.showAdvancedFilters = ko.observable(false);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  self.saveAdvancedFilters = function() {
    var filter = {
      tags:          _.map(ko.unwrap(lupapisteApp.models.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.models.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.models.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.models.areaFilterService.selected), "id")
    };

    ajax
    .command("update-default-application-filter", {filter: filter})
    .error(_.noop)
    .success(function() {
      // TODO show indicator for success
    })
    .call();
  };
};
