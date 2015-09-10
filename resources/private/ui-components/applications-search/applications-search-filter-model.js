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
      handler:       util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    ajax
    .command("update-default-application-filter", {filter: filter, sort: ko.toJS(self.dataProvider.sort)})
    .error(function() {
      hub.send("indicator", {style: "negative"});
    })
    .success(function() {
      hub.send("indicator", {style: "positive"});
    })
    .call();
  };

  hub.onPageLoad("applications", function() {
    self.showAdvancedFilters(self.filterCount() > 0);
  });
};
