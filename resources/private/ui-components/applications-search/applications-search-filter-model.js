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
      tags:          _.map(ko.unwrap(self.dataProvider.tags), "id"),
      operations:    _.map(ko.unwrap(self.dataProvider.operations), "id"),
      organizations: _.map(ko.unwrap(self.dataProvider.organizations), "id"),
      areas:         _.map(ko.unwrap(self.dataProvider.areas), "id")
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
